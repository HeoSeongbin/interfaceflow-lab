package lab.interfaceflow.target;

import jakarta.validation.Valid;
import lab.interfaceflow.common.Simulations.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class SimulationController {
 private final SimulationService service;
 public SimulationController(SimulationService service){this.service=service;}
 @GetMapping("/api/capabilities") public java.util.Map<String,String> capabilities(){return java.util.Map.of("idempotencyProtocol","interfaceflow-v1");}
 @PostMapping("/api/simulations") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public Scenario create(@Valid @RequestBody Input input){return service.create(input);}
 @GetMapping("/api/simulations") public List<Scenario> list(){return service.list();}
 @GetMapping("/api/simulations/{id}") public Scenario get(@PathVariable String id){return service.get(id);}
}
