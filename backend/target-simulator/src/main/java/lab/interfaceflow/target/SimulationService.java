package lab.interfaceflow.target;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.interfaceflow.common.Simulations.*;
import lab.interfaceflow.common.Simulations;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class SimulationService {
 public record Plan(Type type,int delayMs,int status,String code) {}
 public static class Problem extends RuntimeException {
  public final int status;public final String code;
  public Problem(int status,String code){this.status=status;this.code=code;}
 }
 private final SimulationMapper mapper;private final ObjectMapper json;private final boolean enabled;
 public SimulationService(SimulationMapper mapper,ObjectMapper json,@Value("${lab.simulations-enabled:true}") boolean enabled){this.mapper=mapper;this.json=json;this.enabled=enabled;}
 private void enabled(){if(!enabled)throw new Problem(403,"SIMULATIONS_DISABLED");}
 public Scenario create(Input input){
  enabled();validate(input,Instant.now());String id=UUID.randomUUID().toString();mapper.insert(id,input);return get(id);
 }
 static void validate(Input input,Instant now){
  if(input.expiresAt()==null || !input.expiresAt().isAfter(now) || input.expiresAt().isAfter(now.plusSeconds(3600)))throw new Problem(400,"INVALID_EXPIRATION");
  boolean delayed=input.type()==Type.DELAY||input.type()==Type.TIMEOUT;
  if((delayed && input.delayMs()<1)||(!delayed && input.delayMs()!=0)
      ||(input.type()==Type.FAIL_THEN_SUCCESS?input.failureCount()<1:input.failureCount()!=0))throw new Problem(400,"INVALID_SCENARIO_OPTIONS");
 }
 public Scenario get(String id){
  enabled();if(!Simulations.validId(id))throw new Problem(400,"INVALID_SIMULATION_ID");
  String value=mapper.find(id);if(value==null)throw new Problem(404,"SIMULATION_NOT_FOUND");return decode(value);
 }
 public List<Scenario> list(){enabled();return mapper.list().stream().map(this::decode).toList();}
 private Scenario decode(String value){try{return json.readValue(value,Scenario.class);}catch(Exception e){throw new IllegalStateException("Invalid stored simulation",e);}}
 @Transactional
 public Plan plan(String id,String interfaceId,String requestId,String traceId,String attemptId){
  var scenario=get(id);
  if(scenario.type()==Type.CONNECTION_FAILURE)throw new Problem(400,"CONNECTION_SCENARIO_REQUIRES_CLOSED_ENDPOINT");
  Integer sequence=mapper.consume(id,interfaceId,requestId);
  if(sequence==null)throw new Problem(409,"SIMULATION_INACTIVE_OR_OUT_OF_SCOPE");
  var plan=outcome(scenario,sequence);
  mapper.receipt(id,requestId,traceId,attemptId,sequence,plan.status());
  return plan;
 }
 static Plan outcome(Scenario s,int sequence){
  int status=switch(s.type()) {
   case HTTP_400 -> 400;case HTTP_500 -> 500;
   case FAIL_THEN_SUCCESS -> sequence<=s.failureCount()?500:200;
   case DUPLICATE -> sequence>1?409:200;
   default -> 200;
  };
  String code=status==409?"SIMULATED_DUPLICATE":status==200?"OK":"SIMULATED_HTTP_ERROR";
  return new Plan(s.type(),s.delayMs(),status,code);
 }
}
