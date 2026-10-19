package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import lab.interfaceflow.common.Contracts.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransmissionServiceTest {
 private final ObjectMapper json=new ObjectMapper();
 private final RequestLifecycle lifecycle=mock(RequestLifecycle.class);
 private final InterfaceService interfaces=mock(InterfaceService.class);
 @BeforeEach void setup(){when(lifecycle.accept(any(),anyString())).thenReturn(new RequestLifecycle.Accepted("11111111-1111-1111-1111-111111111111",TestDefinitions.user()));}
 private TransmissionService service(String url,int timeout){return new TransmissionService(lifecycle,interfaces,new PayloadEngine(),new PayloadRedactor(),json,url,timeout);}
 private final String trace="0123456789abcdef0123456789abcdef";
 private HttpServer server;
 private JsonNode sentPayload;
 @AfterEach void cleanup(){if(server!=null)server.stop(0);MDC.clear();}
 private SendResult execute(int code,String response,AtomicReference<String> headers) throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/receive",exchange->{
   headers.set(exchange.getRequestHeaders().getFirst("X-Trace-Id")+"/"+exchange.getRequestHeaders().getFirst("X-Request-Id")+"/"+exchange.getRequestHeaders().getFirst("X-Attempt-Id"));
   sentPayload=json.readTree(exchange.getRequestBody()).path("payload");
   byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  var service=service("http://127.0.0.1:"+server.getAddress().getPort(),1000);
  return service.send(new SendRequest("IF-USER-001","REQ-1",json.readTree("{\"userId\":\"U001\"}")),trace);
 }
 @Test void realHttpRoundTripPropagatesIdentifiersAndPersistsResult() throws Exception {
  var headers=new AtomicReference<String>();
  var result=execute(200,"{\"code\":\"OK\",\"requestId\":\"REQ-1\",\"traceId\":\""+trace+"\"}",headers);
  assertEquals("SUCCESS",result.status());assertEquals("U001",sentPayload.path("employee_id").asText());assertFalse(sentPayload.has("userId"));assertEquals(trace+"/REQ-1/"+result.attemptId(),headers.get());
  var order=inOrder(lifecycle);
  order.verify(lifecycle).accept(any(),eq(trace));
  order.verify(lifecycle).prepared(eq(result.id()),any(),any(),anyLong());
  order.verify(lifecycle).sending(result.id(),result.attemptId());
  order.verify(lifecycle).complete(eq(result.id()),eq("SUCCESS"),eq(200),isNull(),any(),anyLong());
 }
 @Test void badRequestIsDefiniteFailure() throws Exception {assertEquals("FAILED",execute(400,"{}",new AtomicReference<>()).status());}
 @Test void serverErrorIsUncertain() throws Exception {assertEquals("UNKNOWN",execute(500,"{}",new AtomicReference<>()).status());}
 @Test void mismatchedResponseIsUncertain() throws Exception {assertEquals("INVALID_RESPONSE",execute(200,"{\"code\":\"OK\"}",new AtomicReference<>()).errorCode());}
 @Test void failedRegistrationNeverDispatches() throws Exception {
  doThrow(new IllegalStateException("DB down")).when(lifecycle).accept(any(),anyString());
  var service=service("http://127.0.0.1:1",100);
  assertThrows(IllegalStateException.class,()->service.send(new SendRequest("IF-USER-001","REQ-1",json.createObjectNode()),trace));
  verify(lifecycle,never()).sending(anyString(),anyString());
 }
 @Test void responseTimeoutRemainsUnknown() throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/receive",exchange->{
   try {Thread.sleep(400);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   exchange.close();
  });server.start();
  when(lifecycle.accept(any(),anyString())).thenReturn(new RequestLifecycle.Accepted("11111111-1111-1111-1111-111111111111",new InterfaceModels.Definition("IF-USER-001","테스트","","SYSTEM_A","SYSTEM_B","POST","/api/receive",true,100,1,1,TestDefinitions.user().mappings(),TestDefinitions.user().validations())));
  var service=service("http://127.0.0.1:"+server.getAddress().getPort(),100);
  var result=service.send(new SendRequest("IF-USER-001","REQ-1",json.createObjectNode().put("userId","U001")),trace);
  assertEquals("UNKNOWN",result.status());assertEquals("TARGET_IO_ERROR",result.errorCode());
 }
 @Test void validationRejectsMalformedInterfaceIdAndNonObjectPayload() {
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   assertFalse(factory.getValidator().validate(new SendRequest("invalid id","REQ-1",json.createObjectNode())).isEmpty());
   assertFalse(factory.getValidator().validate(new SendRequest("IF-USER-001","REQ-1",json.createArrayNode())).isEmpty());
  }
 }
}
