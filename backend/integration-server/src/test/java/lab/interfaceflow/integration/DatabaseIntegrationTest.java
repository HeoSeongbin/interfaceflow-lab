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
  target.createContext("/api/receive",e->{String trace=e.getRequestHeaders().getFirst("X-Trace-Id");String request=e.getRequestHeaders().getFirst("X-Request-Id");byte[] body=("{\"code\":\"OK\",\"requestId\":\""+request+"\",\"traceId\":\""+trace+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);e.getRequestBody().readAllBytes();e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});target.start();
  r.add("lab.target-url",()->"http://127.0.0.1:"+target.getAddress().getPort());
 }
 @AfterAll static void close(){if(target!=null)target.stop(0); if(db!=null)db.stop();}
 static com.sun.net.httpserver.HttpServer target;
 @Test void persistsActualHttpResultAndRejectsUnknownInterface(){
  String id="REQ-"+UUID.randomUUID();
  var response=http.postForEntity("/api/requests",new lab.interfaceflow.common.Contracts.SendRequest("IF-USER-001",id,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()),lab.interfaceflow.common.Contracts.SendResult.class);
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
}
