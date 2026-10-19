package lab.interfaceflow.target;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lab.interfaceflow.common.Contracts.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
public class ReceiveController {
 private final ReceiptMapper mapper;
 private final SimulationService simulations;
 private final BusinessService business;
 public ReceiveController(ReceiptMapper mapper,SimulationService simulations,BusinessService business){this.mapper=mapper;this.simulations=simulations;this.business=business;}
 @PostMapping("/api/receive")
 public org.springframework.http.ResponseEntity<?> receive(@Valid @RequestBody SendRequest request,
   @RequestHeader("X-Trace-Id") @Pattern(regexp="[a-f0-9]{32}") String traceId,
   @RequestHeader("X-Request-Id") String requestId,
   @RequestHeader("X-Attempt-Id") @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String attemptId,
   @RequestHeader(value="X-Simulation-Id",required=false) String simulationId,
   @RequestHeader(value="X-Idempotency-Scope",required=false) String scope,
   @RequestHeader(value="X-Source-System",required=false) String source){
   if(!request.requestId().equals(requestId)) throw new IllegalArgumentException("Request ID mismatch");
   org.slf4j.LoggerFactory.getLogger(ReceiveController.class).info("TARGET_RECEIVED");
   if(simulationId!=null || scope!=null){
    var plan=scope==null?simulations.plan(simulationId,request.interfaceId(),requestId,traceId,attemptId):business.plan(request,source,scope,traceId,attemptId,simulationId);
    // plan() commits receipt/counter before delay. Never sleep with DB locks held.
    if(plan.delayMs()>0)try{Thread.sleep(plan.delayMs());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SimulationService.Problem(503,"SIMULATION_INTERRUPTED");}
    org.slf4j.LoggerFactory.getLogger(ReceiveController.class).info("TARGET_RESPONDED simulationType={} httpStatus={}",plan.type(),plan.status());
    Object body=plan.type()==lab.interfaceflow.common.Simulations.Type.INVALID_RESPONSE?java.util.Map.of("unexpected",true):new TargetResponse(plan.code(),requestId,traceId);
    return org.springframework.http.ResponseEntity.status(plan.status()).body(body);
   }
   mapper.insert(requestId,traceId,attemptId);
   org.slf4j.LoggerFactory.getLogger(ReceiveController.class).info("TARGET_RESPONDED");
   return org.springframework.http.ResponseEntity.ok(new TargetResponse("OK",requestId,traceId));
 }
}
