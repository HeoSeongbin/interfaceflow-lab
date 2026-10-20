package lab.interfaceflow.integration;

import lab.interfaceflow.common.Contracts.*;
import lab.interfaceflow.common.Simulations.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.slf4j.MDC;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Service
public class ReliableDelivery {
 private final DeliveryStore store;private final PayloadEngine engine;private final ObjectMapper json;
 private final String target,failure;private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private final boolean scheduling;
 private final RetryBatch batch;
 public ReliableDelivery(DeliveryStore store,PayloadEngine engine,ObjectMapper json,@Value("${lab.target-url}") String target,
  @Value("${lab.connection-failure-url:http://127.0.0.1:1}") String failure,@Value("${lab.retry-worker:true}") boolean scheduling,
  @Value("${lab.retry-concurrency:2}") int concurrency){this.store=store;this.engine=engine;this.json=json;this.target=target;this.failure=failure;this.scheduling=scheduling;batch=new RetryBatch(concurrency);}
 public SendResult send(SendRequest request,String trace,Scenario scenario){var registration=store.register(request,trace,scenario);if(registration.claim()!=null)dispatch(registration.claim());return store.result(registration.id());}
 @Scheduled(fixedDelayString="${lab.retry-poll-ms:1000}")
 public void poll(){
  if(!scheduling)return;
  var jobs=store.due().stream().<Runnable>map(id->()->{try{run(id);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Delivery worker deferred an unfinished request");}}).toList();
  try{batch.execute(jobs);}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
 @jakarta.annotation.PreDestroy public void stop(){batch.close();}
 public void run(String id){
  var claim=store.claim(id);if(claim!=null)dispatch(claim);
 }
 private void dispatch(DeliveryStore.Claim claim){
  String id=claim.id();var previous=MDC.getCopyOfContextMap();
  long start=System.nanoTime();String status="UNKNOWN",error=null;Integer http=null;JsonNode response=null;
  try{
   MDC.put("traceId",claim.traceId());
   JsonNode context;
   try{context=store.context(claim);}catch(Exception e){store.complete(claim,"UNKNOWN",null,"REPLAY_UNAVAILABLE",null,0);return;}
   var request=json.convertValue(context.path("request"),SendRequest.class);
   var definition=json.convertValue(context.path("definition"),InterfaceModels.Definition.class);
   var scenario=context.path("scenario").isNull()?null:json.convertValue(context.path("scenario"),Scenario.class);
   var prepared=engine.prepare(request.payload(),definition);
   if(!prepared.valid()){store.complete(claim,"UNKNOWN",null,"REPLAY_UNAVAILABLE",null,0);return;}
   MDC.put("requestId",request.requestId());MDC.put("attemptId",claim.attemptId());
   var factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(Duration.ofMillis(definition.timeoutMs()));
   String url=scenario!=null&&scenario.type()==Type.CONNECTION_FAILURE?failure:target;
   var transport=RestClient.builder().baseUrl(url).requestFactory(factory).build();
   try{
    var capability=RestClient.builder().baseUrl(target).requestFactory(factory).build().get().uri("/api/capabilities").header("X-Trace-Id",claim.traceId()).retrieve().body(JsonNode.class);
    if(capability==null||!"interfaceflow-v1".equals(capability.path("idempotencyProtocol").asText())){
     store.complete(claim,"UNKNOWN",null,"TARGET_IDEMPOTENCY_UNCONFIRMED",null,0);return;
    }
   }catch(RestClientResponseException e){store.complete(claim,"UNKNOWN",null,"TARGET_IDEMPOTENCY_UNCONFIRMED",null,0);return;}
   catch(RestClientException e){store.complete(claim,"UNKNOWN",null,"TARGET_CAPABILITY_UNAVAILABLE",null,0);return;}
   try{
    var entity=transport.post().uri(definition.path()).header("X-Trace-Id",claim.traceId()).header("X-Request-Id",request.requestId())
     .header("X-Attempt-Id",claim.attemptId()).header("X-Source-System",definition.sourceSystem()).header("X-Idempotency-Scope",claim.scope())
     .headers(h->{if(scenario!=null)h.set("X-Simulation-Id",scenario.id());})
     .body(new SendRequest(request.interfaceId(),request.requestId(),prepared.mapped())).retrieve().toEntity(String.class);
    http=entity.getStatusCode().value();response=parse(entity.getBody());
    if(http>=200&&http<300&&response!=null&&"OK".equals(response.path("code").asText())&&claim.traceId().equals(response.path("traceId").asText())&&request.requestId().equals(response.path("requestId").asText()))status="SUCCESS";
    else error="INVALID_RESPONSE";
   }catch(RestClientResponseException e){http=e.getStatusCode().value();status=http>=500?"UNKNOWN":"FAILED";error="TARGET_HTTP_ERROR";response=parse(e.getResponseBodyAsString());}
   catch(ResourceAccessException e){error="TARGET_IO_ERROR";}
   catch(RestClientException e){error="INVALID_RESPONSE";}
   boolean committed=store.complete(claim,status,http,error,response,(System.nanoTime()-start)/1_000_000);
   org.slf4j.LoggerFactory.getLogger(getClass()).info("Delivery attempt completed status={} httpStatus={} committed={}",status,http,committed);
  }finally{MDC.clear();if(previous!=null)MDC.setContextMap(previous);}
 }
 private JsonNode parse(String data){if(data==null||data.isBlank())return null;try{return json.readTree(data);}catch(Exception e){return json.createObjectNode().put("invalidJson",true);}}
}
