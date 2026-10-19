package lab.interfaceflow.target;

import lab.interfaceflow.common.Simulations.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {
 private final Instant now=Instant.parse("2026-10-19T00:00:00Z");
 private Scenario scenario(Type type,int failures){return new Scenario("test",type,"IF","REQ",100,failures,now.plusSeconds(10),0);}
 @Test void failureBudgetAndDuplicateAreScopedSequenceRules(){
  assertEquals(500,SimulationService.outcome(scenario(Type.FAIL_THEN_SUCCESS,2),1).status());
  assertEquals(500,SimulationService.outcome(scenario(Type.FAIL_THEN_SUCCESS,2),2).status());
  assertEquals(200,SimulationService.outcome(scenario(Type.FAIL_THEN_SUCCESS,2),3).status());
  assertEquals(200,SimulationService.outcome(scenario(Type.DUPLICATE,0),1).status());
  assertEquals(409,SimulationService.outcome(scenario(Type.DUPLICATE,0),2).status());
 }
 @Test void expirationIsBoundedAndOptionsAreRelevant(){
  assertThrows(SimulationService.Problem.class,()->SimulationService.validate(new Input(Type.SUCCESS,"IF","REQ",0,0,now),now));
  assertThrows(SimulationService.Problem.class,()->SimulationService.validate(new Input(Type.SUCCESS,"IF","REQ",0,0,now.plusSeconds(3601)),now));
  assertThrows(SimulationService.Problem.class,()->SimulationService.validate(new Input(Type.SUCCESS,"IF","REQ",100,0,now.plusSeconds(60)),now));
  assertThrows(SimulationService.Problem.class,()->SimulationService.validate(new Input(Type.TIMEOUT,"IF","REQ",0,0,now.plusSeconds(60)),now));
  assertThrows(SimulationService.Problem.class,()->SimulationService.validate(new Input(Type.FAIL_THEN_SUCCESS,"IF","REQ",0,0,now.plusSeconds(60)),now));
  assertDoesNotThrow(()->SimulationService.validate(new Input(Type.DELAY,"IF","REQ",100,0,now.plusSeconds(60)),now));
 }
 @Test void disabledServiceDoesNotQueryDatabase(){
  var mapper=org.mockito.Mockito.mock(SimulationMapper.class);
  var service=new SimulationService(mapper,new com.fasterxml.jackson.databind.ObjectMapper(),false);
  assertThrows(SimulationService.Problem.class,service::list);
  org.mockito.Mockito.verifyNoInteractions(mapper);
 }
}
