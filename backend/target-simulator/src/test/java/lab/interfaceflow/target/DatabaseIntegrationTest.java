package lab.interfaceflow.target;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class DatabaseIntegrationTest {
 static PostgreSQLContainer<?> db;
 @Autowired TestRestTemplate http;
 @Autowired JdbcTemplate jdbc;
 @DynamicPropertySource static void config(DynamicPropertyRegistry r) throws Exception {
  String url=System.getenv("TEST_DB_URL");
  if(url==null){db=new PostgreSQLContainer<>("postgres:17-alpine");db.start();r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
  else {r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"interfaceflow");r.add("spring.datasource.password",()->"local-test");}

 }
 @AfterAll static void close(){ if(db!=null)db.stop();}
 @Test void storesReceiptAndReturnsTrace(){
  String request="REQ-"+UUID.randomUUID(),trace="0123456789abcdef0123456789abcdef";
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Trace-Id",trace);headers.set("X-Request-Id",request);headers.set("X-Attempt-Id",UUID.randomUUID().toString());
  var body=new lab.interfaceflow.common.Contracts.SendRequest("IF-USER-001",request,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
  var response=http.postForEntity("/api/receive",new HttpEntity<>(body,headers),lab.interfaceflow.common.Contracts.TargetResponse.class);
  assertEquals(200,response.getStatusCode().value());assertNotNull(response.getBody());assertEquals(trace,response.getBody().traceId());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM target.if_target_receipt WHERE request_id=?",Integer.class,request));
 }
 @Test void invalidAttemptIsRejectedBeforeWritingReceipt(){
  String request="INVALID-"+UUID.randomUUID();
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
  headers.set("X-Trace-Id","0123456789abcdef0123456789abcdef");
  headers.set("X-Request-Id",request);headers.set("X-Attempt-Id","------------------------------------");
  var body=new lab.interfaceflow.common.Contracts.SendRequest("IF-USER-001",request,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
  var response=http.postForEntity("/api/receive",new HttpEntity<>(body,headers),com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(400,response.getStatusCode().value());assertNotNull(response.getBody());
  assertEquals("VALIDATION_ERROR",response.getBody().path("code").asText());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM target.if_target_receipt WHERE request_id=?",Integer.class,request));
 }
 @Autowired SimulationService simulations;
 private lab.interfaceflow.common.Simulations.Scenario scenario(lab.interfaceflow.common.Simulations.Type type){
  return simulations.create(new lab.interfaceflow.common.Simulations.Input(type,"IF-SIM","REQ-"+UUID.randomUUID(),0,type==lab.interfaceflow.common.Simulations.Type.FAIL_THEN_SUCCESS?2:0,java.time.Instant.now().plusSeconds(600)));
 }
 private ResponseEntity<com.fasterxml.jackson.databind.JsonNode> executeScenario(lab.interfaceflow.common.Simulations.Scenario s,String requestId){
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Trace-Id","0123456789abcdef0123456789abcdef");headers.set("X-Request-Id",requestId);headers.set("X-Attempt-Id",UUID.randomUUID().toString());headers.set("X-Simulation-Id",s.id());
  var body=new lab.interfaceflow.common.Contracts.SendRequest(s.interfaceId(),requestId,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
  return http.postForEntity("/api/receive",new HttpEntity<>(body,headers),com.fasterxml.jackson.databind.JsonNode.class);
 }
 @Test void failureBudgetAndDuplicatePersistIndependently(){
  var fail=scenario(lab.interfaceflow.common.Simulations.Type.FAIL_THEN_SUCCESS);
  for(int code:new int[]{500,500,200})assertEquals(code,executeScenario(fail,fail.requestId()).getStatusCode().value());
  assertEquals(3,simulations.get(fail.id()).uses());
  var duplicate=scenario(lab.interfaceflow.common.Simulations.Type.DUPLICATE);
  assertEquals(200,executeScenario(duplicate,duplicate.requestId()).getStatusCode().value());
  var response=executeScenario(duplicate,duplicate.requestId());assertEquals(409,response.getStatusCode().value());assertEquals("SIMULATED_DUPLICATE",response.getBody().path("code").asText());
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM target.if_target_receipt WHERE simulation_id=CAST(? AS uuid)",Integer.class,duplicate.id()));
  assertEquals(3,simulations.get(fail.id()).uses());
 }
 @Test void scopeExpiryAndBudgetRejectWithoutReceipt(){
  var s=scenario(lab.interfaceflow.common.Simulations.Type.SUCCESS);
  assertEquals(409,executeScenario(s,"WRONG-REQUEST").getStatusCode().value());assertEquals(0,simulations.get(s.id()).uses());
  jdbc.update("UPDATE target.if_simulation SET expires_at=now()-interval '1 second' WHERE id=CAST(? AS uuid)",s.id());
  assertEquals(409,executeScenario(s,s.requestId()).getStatusCode().value());
  jdbc.update("UPDATE target.if_simulation SET expires_at=now()+interval '1 hour',uses=20 WHERE id=CAST(? AS uuid)",s.id());
  assertEquals(409,executeScenario(s,s.requestId()).getStatusCode().value());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM target.if_target_receipt WHERE simulation_id=CAST(? AS uuid)",Integer.class,s.id()));
 }
 @Test void concurrentConsumersHaveUniqueSequencesAndFixedFailureBudget() throws Exception {
  var s=scenario(lab.interfaceflow.common.Simulations.Type.FAIL_THEN_SUCCESS);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
   for(int i=0;i<6;i++)tasks.add(pool.submit(()->executeScenario(s,s.requestId()).getStatusCode().value()));
   int failures=0;for(var task:tasks)if(task.get(10,java.util.concurrent.TimeUnit.SECONDS)==500)failures++;
   assertEquals(2,failures);
  }
  assertEquals(6,simulations.get(s.id()).uses());
  assertEquals(6,jdbc.queryForObject("SELECT count(DISTINCT simulation_sequence) FROM target.if_target_receipt WHERE simulation_id=CAST(? AS uuid)",Integer.class,s.id()));
 }
 @Test void invalidScenarioApiIsRejected(){
  var connection=scenario(lab.interfaceflow.common.Simulations.Type.CONNECTION_FAILURE);
  assertEquals(400,executeScenario(connection,connection.requestId()).getStatusCode().value());
  assertEquals(0,simulations.get(connection.id()).uses());
  String body="{\"type\":\"HTTP_500\",\"interfaceId\":\"IF-SIM\",\"requestId\":\"REQ-SIM\",\"delayMs\":16000,\"failureCount\":0,\"expiresAt\":\"2099-01-01T00:00:00Z\"}";
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
  assertEquals(400,http.postForEntity("/api/simulations",new HttpEntity<>(body,headers),String.class).getStatusCode().value());
 }

}
