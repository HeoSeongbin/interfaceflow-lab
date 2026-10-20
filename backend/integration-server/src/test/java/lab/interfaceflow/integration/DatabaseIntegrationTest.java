package lab.interfaceflow.integration;
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
  r.add("lab.retry-worker",()->"false");
  String url=System.getenv("TEST_DB_URL");
  if(url==null){db=new PostgreSQLContainer<>("postgres:17-alpine");db.start();r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
  else {r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"interfaceflow");r.add("spring.datasource.password",()->"local-test");}
  target=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  target.createContext("/api/capabilities",e->{byte[] body=(protection?"{\"idempotencyProtocol\":\"interfaceflow-v1\"}":"{}").getBytes(java.nio.charset.StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});
  target.createContext("/api/receive",e->{String trace=e.getRequestHeaders().getFirst("X-Trace-Id");String request=e.getRequestHeaders().getFirst("X-Request-Id");byte[] body=("{\"code\":\"OK\",\"requestId\":\""+request+"\",\"traceId\":\""+trace+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);lastPayload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getRequestBody()).path("payload");received.incrementAndGet();
   var waiting=block;
   if(waiting!=null){arrived.countDown();try{waiting.await(15,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
   e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(targetStatus.get(),body.length);e.getResponseBody().write(body);e.close();});target.start();
  r.add("lab.target-url",()->"http://127.0.0.1:"+target.getAddress().getPort());
 }
 @AfterAll static void close(){if(target!=null)target.stop(0); if(db!=null)db.stop();}
 static com.sun.net.httpserver.HttpServer target;
 static final java.util.concurrent.atomic.AtomicInteger received=new java.util.concurrent.atomic.AtomicInteger();
 static volatile com.fasterxml.jackson.databind.JsonNode lastPayload;
 static volatile boolean protection=true;
 static final java.util.concurrent.atomic.AtomicInteger targetStatus=new java.util.concurrent.atomic.AtomicInteger(200);
 static volatile java.util.concurrent.CountDownLatch block,arrived;
 private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
 private com.fasterxml.jackson.databind.node.ObjectNode configuration(){
  var draft=(com.fasterxml.jackson.databind.node.ObjectNode)http.getForObject("/api/interfaces/IF-USER-001",com.fasterxml.jackson.databind.JsonNode.class).deepCopy();
  return draft.put("interfaceId","IF-TEST-"+UUID.randomUUID()).put("interfaceName","Test interface").put("version",0);
 }
 private com.fasterxml.jackson.databind.node.ObjectNode create(com.fasterxml.jackson.databind.node.ObjectNode draft){
  var result=http.postForEntity("/api/interfaces",draft,com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(201,result.getStatusCode().value(),String.valueOf(result.getBody()));return (com.fasterxml.jackson.databind.node.ObjectNode)result.getBody();
 }
 private com.fasterxml.jackson.databind.node.ObjectNode payload(){return json.createObjectNode().put("userId","U001").put("userName","Demo");}
 private ResponseEntity<com.fasterxml.jackson.databind.JsonNode> send(String interfaceId,com.fasterxml.jackson.databind.JsonNode payload,String requestId){
  return http.postForEntity("/api/requests",new lab.interfaceflow.common.Contracts.SendRequest(interfaceId,requestId,payload),com.fasterxml.jackson.databind.JsonNode.class);
 }
 private com.fasterxml.jackson.databind.JsonNode send(String interfaceId,com.fasterxml.jackson.databind.JsonNode payload){
  var result=send(interfaceId,payload,"REQ-"+UUID.randomUUID());assertEquals(200,result.getStatusCode().value());return result.getBody();
 }
 private ResponseEntity<com.fasterxml.jackson.databind.JsonNode> put(String path,com.fasterxml.jackson.databind.JsonNode body){return http.exchange(path,HttpMethod.PUT,new HttpEntity<>(body),com.fasterxml.jackson.databind.JsonNode.class);}

 @Test void persistsActualHttpResultAndRejectsUnknownInterface(){
  String id="REQ-"+UUID.randomUUID();
  var response=http.postForEntity("/api/requests",new lab.interfaceflow.common.Contracts.SendRequest("IF-USER-001",id,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("userId","U001").put("userName","Demo")),lab.interfaceflow.common.Contracts.SendResult.class);
  assertEquals(200,response.getStatusCode().value());assertNotNull(response.getBody());assertEquals("SUCCESS",response.getBody().status());
  assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM integration.if_request WHERE id=CAST(? AS uuid)",String.class,response.getBody().id()));
  assertEquals(response.getBody().traceId(),response.getHeaders().getFirst("X-Trace-Id"));
  var bad=http.postForEntity("/api/requests",new lab.interfaceflow.common.Contracts.SendRequest("MISSING",id,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()),String.class);
  assertEquals(400,bad.getStatusCode().value());
 }
 @Test void oversizedBodyIsRejected(){
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
  assertEquals(413,http.postForEntity("/api/requests",new HttpEntity<>(" ".repeat(65537),headers),String.class).getStatusCode().value());
 }

 @Test void previewAndActualMappingUseSameVersionWithoutPreviewSideEffects(){
  var definition=create(configuration());String id=definition.path("interfaceId").asText();int before=received.get();
  var preview=http.postForObject("/api/interfaces/"+id+"/preview",json.createObjectNode().set("payload",payload()),com.fasterxml.jackson.databind.JsonNode.class);
  assertTrue(preview.path("valid").asBoolean());assertEquals("U001",preview.path("mappedPayload").path("employee_id").asText());
  assertEquals(before,received.get());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM integration.if_request WHERE interface_id=?",Integer.class,id));
  var result=send(id,payload());assertEquals("SUCCESS",result.path("status").asText());assertEquals(preview.path("mappedPayload"),lastPayload);
  assertEquals(1,result.path("interfaceVersion").asInt());
 }
 @Test void sourceValidationFailureIsStoredWithoutAttemptOrHttp(){
  var definition=create(configuration());int before=received.get();
  var result=send(definition.path("interfaceId").asText(),json.createObjectNode().put("userId","U1"));
  assertEquals("VALIDATION_FAILED",result.path("status").asText());assertEquals("SOURCE_VALIDATION",result.path("processingStage").asText());
  assertTrue(result.path("attemptId").isNull());assertEquals("userName",result.path("errors").get(0).path("field").asText());
  assertEquals(before,received.get());assertEquals("VALIDATION_FAILED",jdbc.queryForObject("SELECT status FROM integration.if_request WHERE id=CAST(? AS uuid)",String.class,result.path("id").asText()));
 }
 @Test void mappingAndTargetValidationFailuresAreDistinct(){
  var mappingDraft=configuration();((com.fasterxml.jackson.databind.node.ObjectNode)mappingDraft.path("mappings").get(0)).put("source","missing");
  var mapping=create(mappingDraft);int before=received.get();
  var failed=send(mapping.path("interfaceId").asText(),payload());assertEquals("MAPPING_FAILED",failed.path("status").asText());assertEquals("FIELD_MAPPING",failed.path("processingStage").asText());
  var targetDraft=configuration();for(var rule:targetDraft.path("validations"))if(rule.path("stage").asText().equals("TARGET")&&rule.path("field").asText().equals("employee_id")){((com.fasterxml.jackson.databind.node.ObjectNode)rule).put("type","NUMBER").remove(java.util.List.of("minLength","maxLength"));}
  var targetDefinition=create(targetDraft);var invalid=send(targetDefinition.path("interfaceId").asText(),payload());
  assertEquals("TARGET_VALIDATION",invalid.path("processingStage").asText());assertTrue(invalid.path("attemptId").isNull());assertEquals(before,received.get());
 }
 @Test void versionsProtectOldHistoryAndRejectStaleUpdates(){
  var original=create(configuration());String id=original.path("interfaceId").asText();var sent=send(id,payload());
  var update=original.deepCopy().put("interfaceName","New name");var result=put("/api/interfaces/"+id,update);
  assertEquals(200,result.getStatusCode().value());assertEquals(2,result.getBody().path("version").asInt());
  assertEquals(409,put("/api/interfaces/"+id,update).getStatusCode().value());
  var history=http.getForObject("/api/requests/"+sent.path("id").asText(),com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals("Test interface",history.path("interfaceSnapshot").path("interfaceName").asText());assertEquals(1,history.path("interfaceVersion").asInt());
  assertEquals(2,http.getForObject("/api/interfaces/"+id+"/versions",com.fasterxml.jackson.databind.JsonNode.class).size());
 }
 @Test void deletionPreservesReferencedHistoryAndDisablesDispatch(){
  var definition=create(configuration());String id=definition.path("interfaceId").asText();var sent=send(id,payload());
  var deleted=http.exchange("/api/interfaces/"+id,HttpMethod.DELETE,HttpEntity.EMPTY,com.fasterxml.jackson.databind.JsonNode.class);
  assertFalse(deleted.getBody().path("deleted").asBoolean());assertTrue(deleted.getBody().path("disabled").asBoolean());
  assertEquals(400,send(id,payload(),"DISABLED-"+UUID.randomUUID()).getStatusCode().value());
  assertEquals(200,http.getForEntity("/api/requests/"+sent.path("id").asText(),String.class).getStatusCode().value());
  var unused=create(configuration());String unusedId=unused.path("interfaceId").asText();http.delete("/api/interfaces/"+unusedId);
  assertEquals(404,http.getForEntity("/api/interfaces/"+unusedId,String.class).getStatusCode().value());
 }
 @Test void maskedHistoryDoesNotAlterRealOutboundPayload(){
  var draft=configuration();((com.fasterxml.jackson.databind.node.ArrayNode)draft.path("mappings")).add(json.createObjectNode().put("source","password").put("target","credentialAlias").put("transform","COPY").put("required",true).put("nullPolicy","ERROR"));
  var definition=create(draft);var result=send(definition.path("interfaceId").asText(),payload().put("password","private-test-value").put("email","demo@example.com"));
  assertEquals("SUCCESS",result.path("status").asText());assertEquals("private-test-value",lastPayload.path("credentialAlias").asText());
  assertFalse(result.toString().contains("private-test-value"));assertEquals("***",result.path("mappedPayload").path("credentialAlias").asText());
  var history=http.getForObject("/api/requests/"+result.path("id").asText(),com.fasterxml.jackson.databind.JsonNode.class);
  assertFalse(history.toString().contains("private-test-value"));assertEquals("***",history.path("originalPayload").path("email").asText());
 }
 @Test void conflictingConcurrentUpdatesHaveOneWinner() throws Exception {
  var definition=create(configuration());String path="/api/interfaces/"+definition.path("interfaceId").asText();
  try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var first=workers.submit(()->put(path,definition.deepCopy().put("interfaceName","First")).getStatusCode().value());
   var second=workers.submit(()->put(path,definition.deepCopy().put("interfaceName","Second")).getStatusCode().value());
   var statuses=new java.util.HashSet<>(java.util.List.of(first.get(),second.get()));assertEquals(java.util.Set.of(200,409),statuses);
  }
 }
 @Test void httpCallDoesNotHoldConfigurationTransaction() throws Exception {
  var definition=create(configuration().put("timeoutMs",10000));String id=definition.path("interfaceId").asText();
  block=new java.util.concurrent.CountDownLatch(1);arrived=new java.util.concurrent.CountDownLatch(1);
  var request=java.util.concurrent.CompletableFuture.supplyAsync(()->send(id,payload()));
  try {
   assertTrue(arrived.await(5,java.util.concurrent.TimeUnit.SECONDS));
   var update=java.util.concurrent.CompletableFuture.supplyAsync(()->put("/api/interfaces/"+id,definition.deepCopy().put("interfaceName","While HTTP pending")));
   assertEquals(200,update.get(5,java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value());
  } finally {block.countDown();block=null;}
  var result=request.get(5,java.util.concurrent.TimeUnit.SECONDS);assertEquals("SUCCESS",result.path("status").asText());assertEquals(1,result.path("interfaceVersion").asInt());
 }
 @Test void mappingAndValidationEndpointsVersionAtomically(){
  var definition=create(configuration());String id=definition.path("interfaceId").asText();
  var mapping=json.createObjectNode().put("version",1);mapping.set("rules",definition.path("mappings"));
  var changed=put("/api/interfaces/"+id+"/mappings",mapping);assertEquals(200,changed.getStatusCode().value());assertEquals(2,changed.getBody().path("version").asInt());
  var rules=json.createObjectNode().put("version",2);rules.set("rules",definition.path("validations"));
  assertEquals(3,put("/api/interfaces/"+id+"/validations",rules).getBody().path("version").asInt());
  assertEquals(3,http.getForObject("/api/interfaces/"+id+"/mappings",com.fasterxml.jackson.databind.JsonNode.class).path("version").asInt());
 }
 @Test void allowedSystemsAndConfigurationBoundsAreEnforced(){
  assertEquals(400,http.postForEntity("/api/interfaces",configuration().put("path","http://127.0.0.1/private"),String.class).getStatusCode().value());
  assertEquals(400,http.postForEntity("/api/interfaces",configuration().put("targetSystem","UNREGISTERED"),String.class).getStatusCode().value());
  var source=json.createObjectNode().put("systemId","SRC-"+UUID.randomUUID()).put("systemName","Demo source").put("role","SOURCE");
  assertEquals(201,http.postForEntity("/api/systems",source,String.class).getStatusCode().value());
  assertEquals(409,http.postForEntity("/api/systems",source,String.class).getStatusCode().value());
  assertEquals(400,http.postForEntity("/api/systems",source.put("role","TARGET"),String.class).getStatusCode().value());
  var definition=create(configuration());assertEquals(409,http.postForEntity("/api/interfaces",definition.deepCopy().put("version",0),String.class).getStatusCode().value());
 }
 @Test void repeatedLogicalIdsReturnOriginalResult(){
  var definition=create(configuration());String id=definition.path("interfaceId").asText(),requestId="REPEAT-"+UUID.randomUUID();
  var first=send(id,payload(),requestId).getBody();var second=send(id,payload(),requestId).getBody();
  assertEquals(requestId,first.path("requestId").asText());assertEquals(requestId,second.path("requestId").asText());
  assertEquals(first.path("attemptId"),second.path("attemptId"));assertEquals(first.path("id"),second.path("id"));
  var history=http.getForObject("/api/requests?query="+requestId+"&size=1",com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(1,history.path("total").asLong());assertEquals(1,history.path("items").size());
 }
 @Test void numericBoundsKeepJsonDecimalPrecision(){
  var draft=configuration();draft.set("mappings",json.createArrayNode());
  draft.set("validations",json.createArrayNode().add(json.createObjectNode().put("field","amount").put("stage","SOURCE").put("type","NUMBER").put("required",true).put("nullable",false).put("maxValue",new java.math.BigDecimal("0.1"))));
  var definition=create(draft);int before=received.get();
  var result=send(definition.path("interfaceId").asText(),json.createObjectNode().put("amount",new java.math.BigDecimal("0.10000000000000001")));
  assertEquals("VALIDATION_FAILED",result.path("status").asText());assertEquals("maxValue",result.path("errors").get(0).path("rule").asText());assertEquals(before,received.get());
 }

 @Test void traceAndDashboardReflectActualLifecycle(){
  var definition=create(configuration());
  var success=send(definition.path("interfaceId").asText(),payload());
  String trace=success.path("traceId").asText();
  var rows=http.getForObject("/api/traces/"+trace,com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(1,rows.size());
  assertEquals(8,rows.get(0).path("events").size());
  assertEquals("REQUEST_RECEIVED",rows.get(0).path("events").get(0).path("stage").asText());
  var attempt=rows.get(0).path("attempts").get(0);
  assertEquals(success.path("attemptId").asText(),attempt.path("id").asText());
  assertEquals("SUCCESS",attempt.path("status").asText());assertTrue(attempt.path("duration_ms").asLong()>=0);
  var failed=send(definition.path("interfaceId").asText(),json.createObjectNode());
  var failureTrace=http.getForObject("/api/traces/"+failed.path("traceId").asText(),com.fasterxml.jackson.databind.JsonNode.class).get(0);
  assertEquals(0,failureTrace.path("attempts").size());assertEquals(3,failureTrace.path("events").size());
  assertEquals("VALIDATION_FAILED",failureTrace.path("events").get(2).path("outcome").asText());
  var summary=http.getForObject("/api/dashboard/summary",com.fasterxml.jackson.databind.JsonNode.class);
  assertTrue(summary.path("recentErrors").isArray());assertTrue(summary.path("interfaces").isArray());assertTrue(summary.path("success").asLong()>0);assertTrue(summary.path("failed").asLong()>0);
  assertEquals(summary.path("total").asLong(),summary.path("success").asLong()+summary.path("failed").asLong()+summary.path("unknown").asLong()+summary.path("inProgress").asLong());
  assertTrue(http.getForObject("/api/dashboard/statistics",com.fasterxml.jackson.databind.JsonNode.class).isArray());
  assertEquals(400,http.getForEntity("/api/traces/bad",String.class).getStatusCode().value());
  assertEquals(404,http.getForEntity("/api/traces/"+UUID.randomUUID().toString().replace("-",""),String.class).getStatusCode().value());
  assertEquals(400,http.getForEntity("/api/dashboard/summary?from=2026-10-19T00:00:00Z&to=2026-10-18T00:00:00Z",String.class).getStatusCode().value());
 }


 @Autowired RequestLifecycle lifecycle;
 @Test void rejectedTransitionCannotCreateAnotherAttempt(){
  var result=send(create(configuration()).path("interfaceId").asText(),payload());
  String id=result.path("id").asText();
  assertThrows(IllegalStateException.class,()->lifecycle.sending(id,UUID.randomUUID().toString()));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM integration.if_attempt WHERE request_key=CAST(? AS uuid)",Integer.class,id));
  assertEquals(8,jdbc.queryForObject("SELECT count(*) FROM integration.if_trace_event WHERE request_key=CAST(? AS uuid)",Integer.class,id));
 }
 @Test void sharedTraceKeepsIndependentRequestsAndPeriodIsHalfOpen(){
  var definition=create(configuration());String trace=UUID.randomUUID().toString().replace("-","");
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Trace-Id",trace);
  for(int i=0;i<2;i++){
   var body=new lab.interfaceflow.common.Contracts.SendRequest(definition.path("interfaceId").asText(),"REPEATED-"+i,payload());
   assertEquals(200,http.postForEntity("/api/requests",new HttpEntity<>(body,headers),String.class).getStatusCode().value());
  }
  var rows=http.getForObject("/api/traces/"+trace,com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(2,rows.size());assertNotEquals(rows.get(0).path("request").path("id"),rows.get(1).path("request").path("id"));
  long baseline=jdbc.queryForObject("SELECT count(*) FROM integration.if_request WHERE created_at >= TIMESTAMPTZ '2001-01-01T00:00:00Z' AND created_at < TIMESTAMPTZ '2001-01-01T01:00:00Z'",Long.class);
  jdbc.update("UPDATE integration.if_request SET created_at='2001-01-01T00:00:00Z' WHERE trace_id=?",trace);
  var period="?from=2001-01-01T00:00:00Z&to=2001-01-01T01:00:00Z";
  assertEquals(baseline+2,http.getForObject("/api/dashboard/summary"+period,com.fasterxml.jackson.databind.JsonNode.class).path("total").asLong());
  assertEquals(0,http.getForObject("/api/dashboard/summary?from=2000-12-31T23:00:00Z&to=2001-01-01T00:00:00Z",com.fasterxml.jackson.databind.JsonNode.class).path("total").asLong());
  assertEquals(2,http.getForObject("/api/requests"+period+"&query="+trace,com.fasterxml.jackson.databind.JsonNode.class).path("total").asLong());
 }

 @Test void uncertainHttpResultRemainsUnknownInAttemptAndEvents(){
  var definition=create(configuration());
  try {
   for(int code:new int[]{500,400}) {
    targetStatus.set(code);
    var result=send(definition.path("interfaceId").asText(),payload());
    String expected=code==500?"UNKNOWN":"FAILED";
    assertEquals(expected,result.path("status").asText());
    var row=http.getForObject("/api/traces/"+result.path("traceId").asText(),com.fasterxml.jackson.databind.JsonNode.class).get(0);
    assertEquals(expected,row.path("attempts").get(0).path("status").asText());
    assertEquals(code,row.path("attempts").get(0).path("http_status").asInt());
    assertEquals(expected,row.path("events").get(7).path("outcome").asText());
    assertEquals(result.path("attemptId").asText(),row.path("events").get(7).path("attempt_id").asText());
   }
  } finally {targetStatus.set(200);}
 }
 @Autowired TransmissionService transmission;
 @Test void simulationScopeAndTimeoutValidationRollBackAcceptance(){
  var d=create(configuration());String interfaceId=d.path("interfaceId").asText();String requestId="SIM-"+UUID.randomUUID();
  var s=new lab.interfaceflow.common.Simulations.Scenario(UUID.randomUUID().toString(),lab.interfaceflow.common.Simulations.Type.TIMEOUT,interfaceId,requestId,1,0,java.time.Instant.now().plusSeconds(60),0);
  var request=new lab.interfaceflow.common.Contracts.SendRequest(interfaceId,requestId,payload());
  String trace=UUID.randomUUID().toString().replace("-","");
  assertThrows(ApiProblem.class,()->lifecycle.acceptSimulation(request,trace,s));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM integration.if_request WHERE request_id=?",Integer.class,requestId));
  var wrong=new lab.interfaceflow.common.Contracts.SendRequest(interfaceId,"OTHER",payload());
  assertThrows(ApiProblem.class,()->lifecycle.acceptSimulation(wrong,trace,s));
 }
 @Test void connectionSimulationMakesRealIoFailureAndRecordsUnknown(){
  var d=create(configuration());String interfaceId=d.path("interfaceId").asText();String requestId="SIM-"+UUID.randomUUID();
  var s=new lab.interfaceflow.common.Simulations.Scenario(UUID.randomUUID().toString(),lab.interfaceflow.common.Simulations.Type.CONNECTION_FAILURE,interfaceId,requestId,0,0,java.time.Instant.now().plusSeconds(60),0);
  int before=received.get();
  var result=transmission.send(new lab.interfaceflow.common.Contracts.SendRequest(interfaceId,requestId,payload()),UUID.randomUUID().toString().replace("-",""),s);
  assertEquals("UNKNOWN",result.status());assertEquals("TARGET_IO_ERROR",result.errorCode());assertNull(result.httpStatus());assertNotNull(result.attemptId());assertEquals(before,received.get());
  assertEquals(s.id(),lifecycle.detail(result.id()).path("simulationId").asText());
  org.slf4j.MDC.clear();
 }

 @Autowired DeliveryStore durable;
 @Autowired ReliableDelivery reliable;
 @Test void concurrentDuplicateIsBlockedAndChangedPayloadConflicts() throws Exception {
  var d=create(configuration());String interfaceId=d.path("interfaceId").asText(),requestId="KEY-"+UUID.randomUUID();int before=received.get();
  block=new java.util.concurrent.CountDownLatch(1);arrived=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var first=pool.submit(()->send(interfaceId,payload(),requestId));assertTrue(arrived.await(5,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals(409,send(interfaceId,payload(),requestId).getStatusCode().value());
   assertEquals(409,send(interfaceId,payload().put("userId","CHANGED"),requestId).getStatusCode().value());
   block.countDown();var result=first.get(10,java.util.concurrent.TimeUnit.SECONDS).getBody();
   var cached=send(interfaceId,payload(),requestId);assertEquals(200,cached.getStatusCode().value());assertEquals(result.path("id"),cached.getBody().path("id"));assertEquals(result.path("traceId"),cached.getBody().path("traceId"));
   assertEquals(result.path("traceId").asText(),cached.getHeaders().getFirst("X-Trace-Id"));assertEquals(before+1,received.get());
  }finally{block.countDown();block=null;arrived=null;}
 }
 @Test void expiredLeaseIsRecoveredAndOldCompletionIsFenced(){
  var draft=configuration().put("retryMaxAttempts",3);var d=create(draft);String id=d.path("interfaceId").asText(),trace=UUID.randomUUID().toString().replace("-","");
  var payload=payload().put("email","protected@example.com");
  var registration=durable.register(new lab.interfaceflow.common.Contracts.SendRequest(id,"LEASE-"+UUID.randomUUID(),payload),trace,null);
  var first=registration.claim();assertNotNull(first);assertNull(durable.claim(registration.id()));
  String encrypted=jdbc.queryForObject("SELECT replay_cipher FROM integration.if_delivery WHERE request_key=CAST(? AS uuid)",String.class,registration.id());assertFalse(encrypted.contains("protected@example.com"));
  jdbc.update("UPDATE integration.if_delivery SET lease_until=now()-interval '1 second' WHERE request_key=CAST(? AS uuid)",registration.id());
  var recovered=durable.claim(registration.id());assertNotNull(recovered);assertNotEquals(first.attemptId(),recovered.attemptId());assertEquals(first.traceId(),recovered.traceId());
  assertFalse(durable.complete(first,"SUCCESS",200,null,null,0));
  assertTrue(durable.complete(recovered,"SUCCESS",200,null,null,0));
  assertEquals("UNKNOWN",jdbc.queryForObject("SELECT status FROM integration.if_attempt WHERE id=CAST(? AS uuid)",String.class,first.attemptId()));
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM integration.if_attempt WHERE request_key=CAST(? AS uuid)",Integer.class,registration.id()));
 }
 @Test void automaticBudgetAndConcurrentManualRetryAreBounded() throws Exception {
  var d=create(configuration());String id=d.path("interfaceId").asText();
  var r=durable.register(new lab.interfaceflow.common.Contracts.SendRequest(id,"MANUAL-"+UUID.randomUUID(),payload()),UUID.randomUUID().toString().replace("-",""),null);
  var claim=r.claim();assertNotNull(claim);assertTrue(durable.complete(claim,"UNKNOWN",503,"TARGET_HTTP_ERROR",null,1));
  assertEquals("DEAD_LETTER",jdbc.queryForObject("SELECT state FROM integration.if_delivery WHERE request_key=CAST(? AS uuid)",String.class,r.id()));
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();for(int i=0;i<4;i++)tasks.add(pool.submit(()->{try{durable.retry(r.id());return true;}catch(ApiProblem e){return false;}}));
   int accepted=0;for(var task:tasks)if(task.get())accepted++;assertEquals(1,accepted);
  }
  reliable.run(r.id());assertEquals("SUCCESS",durable.result(r.id()).status());assertEquals(2,jdbc.queryForObject("SELECT attempts FROM integration.if_delivery WHERE request_key=CAST(? AS uuid)",Integer.class,r.id()));
  assertThrows(ApiProblem.class,()->durable.retry(r.id()));
  assertFalse(http.getForObject("/api/retries",String.class).contains("replay_cipher"));assertFalse(http.getForObject("/api/requests/"+r.id(),String.class).contains("replay_cipher"));
 }

 @Test void unconfirmedTargetIdempotencyNeverDispatchesBusinessRequest(){
  var d=create(configuration());int before=received.get();
  try{
   protection=false;
   var result=send(d.path("interfaceId").asText(),payload());
   assertEquals("UNKNOWN",result.path("status").asText());assertEquals("TARGET_IDEMPOTENCY_UNCONFIRMED",result.path("errorCode").asText());assertEquals(before,received.get());
   assertEquals("DEAD_LETTER",jdbc.queryForObject("SELECT state FROM integration.if_delivery WHERE request_key=CAST(? AS uuid)",String.class,result.path("id").asText()));
   protection=true;durable.retry(result.path("id").asText());reliable.run(result.path("id").asText());assertEquals("SUCCESS",durable.result(result.path("id").asText()).status());
  }finally{protection=true;}
 }

 @Test void cursorHistoryHandlesTiesAndDoesNotShiftWhenNewRowsArrive(){
  var d=create(configuration());String interfaceId=d.path("interfaceId").asText();
  for(int i=0;i<12;i++)send(interfaceId,payload());
  jdbc.update("UPDATE integration.if_request SET created_at=TIMESTAMPTZ '2020-01-01T00:00:00Z' WHERE interface_id=?",interfaceId);
  var seen=new java.util.HashSet<String>();String cursor="";int pages=0;
  do{
   var response=http.getForEntity("/api/requests/scroll?query="+interfaceId+"&size=5&cursor="+cursor,com.fasterxml.jackson.databind.JsonNode.class);
   assertEquals(200,response.getStatusCode().value());var page=response.getBody();
   assertFalse(page.has("total"));for(var row:page.path("items"))assertTrue(seen.add(row.path("id").asText()));
   cursor=page.path("nextCursor").isNull()?"":page.path("nextCursor").asText();
   if(pages++==0){
    send(interfaceId,payload());
    assertEquals(400,http.getForEntity("/api/requests/scroll?query=OTHER&cursor="+cursor,String.class).getStatusCode().value());
   }
  }while(!cursor.isEmpty()&&pages<5);
  assertEquals(12,seen.size());assertEquals(3,pages);
  assertEquals(400,http.getForEntity("/api/requests/scroll?cursor=bad!",String.class).getStatusCode().value());
  assertEquals(400,http.getForEntity("/api/requests/scroll?size=101",String.class).getStatusCode().value());
 }
 @Test void dueSelectionIncludesExpiredLeasesAndExcludesTerminalAndFutureWork(){
  var d=create(configuration().put("retryMaxAttempts",3));String interfaceId=d.path("interfaceId").asText();
  var first=durable.register(new lab.interfaceflow.common.Contracts.SendRequest(interfaceId,"DUE-A-"+UUID.randomUUID(),payload()),UUID.randomUUID().toString().replace("-",""),null);
  var second=durable.register(new lab.interfaceflow.common.Contracts.SendRequest(interfaceId,"DUE-B-"+UUID.randomUUID(),payload()),UUID.randomUUID().toString().replace("-",""),null);
  jdbc.update("UPDATE integration.if_delivery SET lease_until=now()-interval '1 second' WHERE request_key=CAST(? AS uuid)",first.id());
  assertTrue(durable.due().contains(first.id()));assertFalse(durable.due().contains(second.id()));
  var recovered=durable.claim(first.id());assertNotNull(recovered);durable.complete(recovered,"SUCCESS",200,null,null,0);
  durable.complete(second.claim(),"SUCCESS",200,null,null,0);assertFalse(durable.due().contains(first.id()));
 }

}
