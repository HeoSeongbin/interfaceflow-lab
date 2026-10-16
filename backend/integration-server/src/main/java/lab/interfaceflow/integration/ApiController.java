package lab.interfaceflow.integration;
import jakarta.validation.Valid;
import lab.interfaceflow.common.Contracts.*;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;
@RestController
public class ApiController {
    private final TransmissionService service;
    public ApiController(TransmissionService service){this.service=service;}
    @PostMapping("/api/requests")
    public SendResult send(@Valid @RequestBody SendRequest request){return service.send(request,MDC.get("traceId"));}
    @GetMapping("/api/interfaces")
    public Object interfaces(){return java.util.List.of(java.util.Map.of("interfaceId","IF-USER-001","interfaceName","사용자 정보 연동 · Phase 1", "enabled",true));}
}
