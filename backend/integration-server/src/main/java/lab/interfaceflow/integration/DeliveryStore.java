package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lab.interfaceflow.common.*;
import lab.interfaceflow.common.Contracts.*;
import lab.interfaceflow.common.Simulations.Scenario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DeliveryStore {
 public record Registration(String id,boolean created,Claim claim){}
 public record Claim(String id,String token,String attemptId,String traceId,String scope,String cipher){}
 private final DeliveryMapper mapper;private final InterfaceMapper definitions;private final InterfaceService interfaces;
 private final RequestLifecycle lifecycle;private final PayloadEngine engine;private final ReplayCipher cipher;private final ObjectMapper json;private final MonitoringMapper events;
 public DeliveryStore(DeliveryMapper mapper,InterfaceMapper definitions,InterfaceService interfaces,RequestLifecycle lifecycle,PayloadEngine engine,ReplayCipher cipher,ObjectMapper json,MonitoringMapper events){
  this.mapper=mapper;this.definitions=definitions;this.interfaces=interfaces;this.lifecycle=lifecycle;this.engine=engine;this.cipher=cipher;this.json=json;this.events=events;
 }
 @Transactional
 public Registration register(SendRequest r,String trace,Scenario scenario){
  if(definitions.shareLock(r.interfaceId())==null)throw new ApiProblem(400,"INTERFACE_NOT_FOUND","인터페이스를 찾을 수 없습니다.");
  var current=interfaces.get(r.interfaceId());String scope=scenario==null?"normal":scenario.id(),hash=CanonicalJson.hash(r.payload());
  mapper.advisory(List.of(r.interfaceId(),current.sourceSystem(),r.requestId(),scope).toString());
  String existing=mapper.existing(r.interfaceId(),current.sourceSystem(),r.requestId(),scope);
  if(existing!=null){var old=decode(existing);if(!hash.equals(old.path("payload_hash").asText()))throw new ApiProblem(409,"PAYLOAD_CONFLICT","같은 요청 키의 전문이 다릅니다.");
   if(Set.of("LEASED","WAIT").contains(old.path("state").asText()))throw new ApiProblem(409,"REQUEST_IN_PROGRESS","이미 처리 또는 재시도 대기 중입니다.");
   return new Registration(old.path("request_key").asText(),false,null);
  }
  var accepted=scenario==null?lifecycle.accept(r,trace):lifecycle.acceptSimulation(r,trace,scenario);
  var prepared=engine.prepare(r.payload(),accepted.definition());lifecycle.prepared(accepted.id(),prepared,accepted.definition(),0);
  ObjectNode context=json.createObjectNode();context.set("request",json.valueToTree(r));context.set("definition",json.valueToTree(accepted.definition()));context.set("scenario",json.valueToTree(scenario));
  String protectedData=cipher.encrypt(context.toString(),accepted.id());
  mapper.insert(accepted.id(),r.interfaceId(),current.sourceSystem(),r.requestId(),scope,hash,protectedData,accepted.definition().retryMaxAttempts(),prepared.valid()?"WAIT":"DONE");
  return new Registration(accepted.id(),true,prepared.valid()?claim(accepted.id()):null);
 }
 @Transactional
 public Claim claim(String id){
  var row=required(mapper.lock(id));String state=row.path("state").asText();
  if(state.equals("LEASED")&&!row.path("live").asBoolean()){
   mapper.closeAttempt(id);mapper.expired(id);events.event(id,null,"LEASE_EXPIRED","UNKNOWN");
   boolean exhausted=row.path("attempts").asInt()>=row.path("max_attempts").asInt();
   mapper.finish(id,exhausted?"DEAD_LETTER":"WAIT",true,"LEASE_EXPIRED",0);
   if(exhausted)return null;state="WAIT";
  }else if(!state.equals("WAIT")||!row.path("due").asBoolean())return null;
  if(!state.equals("WAIT"))return null;
  String token=UUID.randomUUID().toString(),attempt=UUID.randomUUID().toString();
  mapper.claim(id,token);if(mapper.sending(id,attempt)!=1)throw new IllegalStateException("Delivery state transition rejected");
  events.startAttempt(id,attempt);events.event(id,attempt,"REQUEST_DISPATCHED","SENDING");
  return new Claim(id,token,attempt,row.path("traceId").asText(),row.path("scope").asText(),row.path("replay_cipher").asText());
 }
 @Transactional
 public boolean complete(Claim claim,String status,Integer http,String error,JsonNode response,long duration){
  var row=required(mapper.lock(claim.id()));
  if(!row.path("state").asText().equals("LEASED")||!row.path("lease_token").asText().equals(claim.token())||!row.path("live").asBoolean())return false;
  lifecycle.complete(claim.id(),status,http,error,response,duration);
  boolean unavailable=Set.of("REPLAY_UNAVAILABLE","TARGET_IDEMPOTENCY_UNCONFIRMED").contains(error==null?"":error);
  boolean retryable=unavailable||retryable(status,http,error),available=row.path("attempts").asInt()<row.path("max_attempts").asInt();
  String next=unavailable?"DEAD_LETTER":retryable?(available?"WAIT":"DEAD_LETTER"):"DONE";
  mapper.finish(claim.id(),next,retryable,error,backoff(row.path("attempts").asInt()));
  if(retryable)events.event(claim.id(),null,next.equals("WAIT")?"RETRY_SCHEDULED":"DEAD_LETTER",status);
  return true;
 }
 static boolean retryable(String status,Integer http,String error){return !"SUCCESS".equals(status)&&!"REPLAY_UNAVAILABLE".equals(error)&&
   ("TARGET_IO_ERROR".equals(error)||"TARGET_CAPABILITY_UNAVAILABLE".equals(error)||"INVALID_RESPONSE".equals(error)||"LEASE_EXPIRED".equals(error)||(http!=null&&(http>=500||http==408||http==429)));}
 static long backoff(int attempt){long ceiling=Math.min(30000L,500L<<Math.min(6,Math.max(0,attempt-1)));return java.util.concurrent.ThreadLocalRandom.current().nextLong(ceiling/2,ceiling+1);}
 @Transactional
 public void retry(String id){
  var row=required(mapper.lock(id));
  if(!Set.of("DONE","DEAD_LETTER").contains(row.path("state").asText())||!row.path("retryable").asBoolean()||row.path("attempts").asInt()>=10)
   throw new ApiProblem(409,"RETRY_NOT_ALLOWED","현재 상태·오류·최대 10회 한도를 확인하세요.");
  try{cipher.decrypt(row.path("replay_cipher").asText(),id);}catch(Exception e){throw new ApiProblem(503,"REPLAY_UNAVAILABLE","기존 암호화 키를 복구한 뒤 재시도하세요.");}
  mapper.manual(id);events.event(id,null,"MANUAL_RETRY_QUEUED","UNKNOWN");
 }
 public List<JsonNode> list(){return mapper.list().stream().map(this::decode).toList();}
 public List<String> due(){return mapper.due();}
 public JsonNode context(Claim claim){return decode(cipher.decrypt(claim.cipher(),claim.id()));}
 public SendResult result(String id){
  var r=lifecycle.detail(id);
  return new SendResult(id,r.path("requestId").asText(),r.path("traceId").asText(),r.path("attemptId").isNull()?null:r.path("attemptId").asText(),r.path("status").asText(),r.path("httpStatus").isNull()?null:r.path("httpStatus").asInt(),r.get("response"),r.path("errorCode").isNull()?null:r.path("errorCode").asText(),r.path("processingStage").asText(),r.path("interfaceVersion").asInt(),r.get("originalPayload"),r.get("mappedPayload"),json.convertValue(r.path("errors"),new com.fasterxml.jackson.core.type.TypeReference<List<FieldError>>(){}));
 }
 private JsonNode required(String data){if(data==null)throw new ApiProblem(404,"DELIVERY_NOT_FOUND","보호된 재처리 기록이 없습니다.");return decode(data);}
 private JsonNode decode(String data){try{return json.readTree(data);}catch(Exception e){throw new IllegalStateException("Invalid delivery data");}}
}
