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
 public ReceiveController(ReceiptMapper mapper){this.mapper=mapper;}
 @PostMapping("/api/receive")
 public TargetResponse receive(@Valid @RequestBody SendRequest request,
   @RequestHeader("X-Trace-Id") @Pattern(regexp="[a-f0-9]{32}") String traceId,
   @RequestHeader("X-Request-Id") String requestId,
   @RequestHeader("X-Attempt-Id") @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String attemptId){
   if(!request.requestId().equals(requestId)) throw new IllegalArgumentException("Request ID mismatch");
   mapper.insert(requestId,traceId,attemptId);
   return new TargetResponse("OK",requestId,traceId);
 }
}
