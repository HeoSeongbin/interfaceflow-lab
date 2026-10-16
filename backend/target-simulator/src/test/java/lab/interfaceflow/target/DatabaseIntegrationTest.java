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
}
