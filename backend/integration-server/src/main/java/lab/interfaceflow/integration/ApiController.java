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
    private final ReliableDelivery reliable;
    public ApiController(TransmissionService service,RequestLifecycle lifecycle,ReliableDelivery reliable){this.service=service;this.lifecycle=lifecycle;this.reliable=reliable;}
    @PostMapping("/api/requests")
    public SendResult send(@Valid @RequestBody SendRequest request,jakarta.servlet.http.HttpServletResponse response){
        var result=reliable.send(request,MDC.get("traceId"),null);MDC.put("traceId",result.traceId());MDC.put("requestId",result.requestId());response.setHeader("X-Trace-Id",result.traceId());return result;
    }
    public record PreviewInput(JsonNode payload) {}
    @PostMapping("/api/interfaces/{id}/preview")
    public TransmissionService.Preview preview(@PathVariable String id,@RequestBody PreviewInput request){return service.preview(id,request.payload());}
    @GetMapping("/api/requests")
    public RequestLifecycle.HistoryPage history(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) java.time.Instant from,@RequestParam(required=false) java.time.Instant to){return lifecycle.history(query,status,page,size,from,to);}
    @GetMapping("/api/requests/{id}") public JsonNode detail(@PathVariable String id){return lifecycle.detail(id);}
}
