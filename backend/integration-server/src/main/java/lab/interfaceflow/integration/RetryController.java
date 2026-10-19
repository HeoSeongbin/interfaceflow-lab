package lab.interfaceflow.integration;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class RetryController {
 private final DeliveryStore store;
 public RetryController(DeliveryStore store){this.store=store;}
 @GetMapping("/api/retries") public List<JsonNode> list(){return store.list();}
 @PostMapping("/api/requests/{id}/retry") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public Map<String,String> retry(@PathVariable String id){
  if(!lab.interfaceflow.common.Simulations.validId(id))throw new ApiProblem(400,"INVALID_REQUEST_KEY","내부 UUID를 확인하세요.");
  store.retry(id);return Map.of("id",id,"state","WAIT");
 }
}
