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
  String url=System.getenv("TEST_DB_URL");
  if(url==null){db=new PostgreSQLContainer<>("postgres:17-alpine");db.start();r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
  else {r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"interfaceflow");r.add("spring.datasource.password",()->"local-test");}
  target=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  target.createContext("/api/receive",e->{String trace=e.getRequestHeaders().getFirst("X-Trace-Id");String request=e.getRequestHeaders().getFirst("X-Request-Id");byte[] body=("{\"code\":\"OK\",\"requestId\":\""+request+"\",\"traceId\":\""+trace+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);lastPayload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getRequestBody()).path("payload");received.incrementAndGet();
   var waiting=block;
   if(waiting!=null){arrived.countDown();try{waiting.await(15,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
   e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});target.start();
  r.add("lab.target-url",()->"http://127.0.0.1:"+target.getAddress().getPort());
 }
 @AfterAll static void close(){if(target!=null)target.stop(0); if(db!=null)db.stop();}
 static com.sun.net.httpserver.HttpServer target;
 static final java.util.concurrent.atomic.AtomicInteger received=new java.util.concurrent.atomic.AtomicInteger();
 static volatile com.fasterxml.jackson.databind.JsonNode lastPayload;
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
 @Test void repeatedLogicalIdsRemainDistinctUntilIdempotencyPhase(){
  var definition=create(configuration());String id=definition.path("interfaceId").asText(),requestId="REPEAT-"+UUID.randomUUID();
  var first=send(id,payload(),requestId).getBody();var second=send(id,payload(),requestId).getBody();
  assertEquals(requestId,first.path("requestId").asText());assertEquals(requestId,second.path("requestId").asText());
  assertNotEquals(first.path("attemptId"),second.path("attemptId"));assertNotEquals(first.path("id"),second.path("id"));
  var history=http.getForObject("/api/requests?query="+requestId+"&size=1",com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(2,history.path("total").asInt());assertEquals(1,history.path("items").size());
 }
 @Test void numericBoundsKeepJsonDecimalPrecision(){
  var draft=configuration();draft.set("mappings",json.createArrayNode());
  draft.set("validations",json.createArrayNode().add(json.createObjectNode().put("field","amount").put("stage","SOURCE").put("type","NUMBER").put("required",true).put("nullable",false).put("maxValue",new java.math.BigDecimal("0.1"))));
  var definition=create(draft);int before=received.get();
  var result=send(definition.path("interfaceId").asText(),json.createObjectNode().put("amount",new java.math.BigDecimal("0.10000000000000001")));
  assertEquals("VALIDATION_FAILED",result.path("status").asText());assertEquals("maxValue",result.path("errors").get(0).path("rule").asText());assertEquals(before,received.get());
 }
}
