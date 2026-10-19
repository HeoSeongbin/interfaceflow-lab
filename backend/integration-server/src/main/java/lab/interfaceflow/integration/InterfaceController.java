package lab.interfaceflow.integration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import lab.interfaceflow.integration.InterfaceModels.*;
import java.util.*;

@RestController
public class InterfaceController {
    private final InterfaceService service;
    public InterfaceController(InterfaceService service){this.service=service;}
    @GetMapping("/api/systems") public List<SystemInfo> systems(){return service.systems();}
    @PostMapping("/api/systems") @ResponseStatus(HttpStatus.CREATED)
    public SystemInfo system(@Valid @RequestBody SystemInfo system){return service.addSystem(system);}
    @GetMapping("/api/interfaces") public List<Definition> list(@RequestParam(defaultValue="") String query){return service.list(query);}
    @GetMapping("/api/interfaces/{id}") public Definition get(@PathVariable String id){return service.get(id);}
    @PostMapping("/api/interfaces") @ResponseStatus(HttpStatus.CREATED)
    public Definition create(@Valid @RequestBody Definition definition){return service.create(definition);}
    @PutMapping("/api/interfaces/{id}") public Definition update(@PathVariable String id,@Valid @RequestBody Definition definition){return service.update(id,definition);}
    @PatchMapping("/api/interfaces/{id}/status") public Definition status(@PathVariable String id,@Valid @RequestBody StatusChange change){return service.status(id,change);}
    @DeleteMapping("/api/interfaces/{id}") public Map<String,Object> delete(@PathVariable String id){return service.delete(id);}
    @GetMapping("/api/interfaces/{id}/versions") public List<Definition> versions(@PathVariable String id){return service.versions(id);}
    @GetMapping("/api/interfaces/{id}/mappings") public Map<String,Object> mappings(@PathVariable String id){var d=service.get(id);return Map.of("version",d.version(),"rules",d.mappings());}
    @PutMapping("/api/interfaces/{id}/mappings") public Definition mappings(@PathVariable String id,@Valid @RequestBody MappingChange change){return service.mappings(id,change);}
    @GetMapping("/api/interfaces/{id}/validations") public Map<String,Object> validations(@PathVariable String id){var d=service.get(id);return Map.of("version",d.version(),"rules",d.validations());}
    @PutMapping("/api/interfaces/{id}/validations") public Definition validations(@PathVariable String id,@Valid @RequestBody ValidationChange change){return service.validations(id,change);}
}
