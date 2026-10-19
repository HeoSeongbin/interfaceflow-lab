package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lab.interfaceflow.common.Contracts.*;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;

@RestController
public class ApiController {
    private final TransmissionService service;
    private final RequestLifecycle lifecycle;
    public ApiController(TransmissionService service,RequestLifecycle lifecycle){this.service=service;this.lifecycle=lifecycle;}
    @PostMapping("/api/requests")
    public SendResult send(@Valid @RequestBody SendRequest request){return service.send(request,MDC.get("traceId"));}
    public record PreviewInput(JsonNode payload) {}
    @PostMapping("/api/interfaces/{id}/preview")
    public TransmissionService.Preview preview(@PathVariable String id,@RequestBody PreviewInput request){return service.preview(id,request.payload());}
    @GetMapping("/api/requests")
    public RequestLifecycle.HistoryPage history(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) java.time.Instant from,@RequestParam(required=false) java.time.Instant to){return lifecycle.history(query,status,page,size,from,to);}
    @GetMapping("/api/requests/{id}") public JsonNode detail(@PathVariable String id){return lifecycle.detail(id);}
}
