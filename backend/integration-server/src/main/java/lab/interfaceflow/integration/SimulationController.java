package lab.interfaceflow.integration;

import lab.interfaceflow.common.Contracts.*;
import lab.interfaceflow.common.Simulations;
import lab.interfaceflow.common.Simulations.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.slf4j.MDC;
import java.time.Duration;
import java.net.http.HttpClient;

@RestController
public class SimulationController {
 private final RestClient target;private final TransmissionService transmission;private final InterfaceService interfaces;
 private final ReliableDelivery reliable;
 public SimulationController(TransmissionService transmission,InterfaceService interfaces,ReliableDelivery reliable,@Value("${lab.target-url}") String targetUrl){
  this.transmission=transmission;this.interfaces=interfaces;this.reliable=reliable;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());factory.setReadTimeout(Duration.ofSeconds(3));
  target=RestClient.builder().baseUrl(targetUrl).requestFactory(factory).build();
 }
 private <T> T call(java.util.function.Supplier<T> action){
  try{return action.get();}
  catch(RestClientResponseException e){int status=e.getStatusCode().value();throw new ApiProblem(status>=500?502:status,"SIMULATION_CONTROL_ERROR","시나리오 설정/범위/활성 상태를 확인하세요.");}
  catch(RestClientException e){throw new ApiProblem(502,"SIMULATOR_UNAVAILABLE","Target 시뮬레이터 연결을 확인하세요.");}
 }
 @GetMapping("/api/simulations") public Scenario[] list(){return call(()->target.get().uri("/api/simulations").header("X-Trace-Id",MDC.get("traceId")).retrieve().body(Scenario[].class));}
 @PostMapping("/api/simulations") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public Scenario create(@Valid @RequestBody Input input){
  interfaces.get(input.interfaceId());
  return call(()->target.post().uri("/api/simulations").header("X-Trace-Id",MDC.get("traceId")).body(input).retrieve().body(Scenario.class));
 }
 @PostMapping("/api/simulations/{id}/execute")
 public SendResult execute(@PathVariable String id,@Valid @RequestBody SendRequest request){
  if(!Simulations.validId(id))throw new ApiProblem(400,"INVALID_SIMULATION_ID","시나리오 UUID를 확인하세요.");
  var scenario=call(()->target.get().uri("/api/simulations/{id}",id).header("X-Trace-Id",MDC.get("traceId")).retrieve().body(Scenario.class));
  if(scenario==null)throw new ApiProblem(502,"INVALID_SIMULATION_RESPONSE","시나리오 조회 응답이 없습니다.");
  return transmission.send(request,MDC.get("traceId"),scenario);
 }
 @PostMapping("/api/simulations/{id}/reliable-execute")
 public SendResult reliable(@PathVariable String id,@Valid @RequestBody SendRequest request,jakarta.servlet.http.HttpServletResponse response){
  if(!Simulations.validId(id))throw new ApiProblem(400,"INVALID_SIMULATION_ID","시나리오 UUID를 확인하세요.");
  var scenario=call(()->target.get().uri("/api/simulations/{id}",id).header("X-Trace-Id",MDC.get("traceId")).retrieve().body(Scenario.class));
  if(scenario==null)throw new ApiProblem(502,"INVALID_SIMULATION_RESPONSE","시나리오 응답이 없습니다.");
  var result=reliable.send(request,MDC.get("traceId"),scenario);MDC.put("traceId",result.traceId());MDC.put("requestId",result.requestId());response.setHeader("X-Trace-Id",result.traceId());return result;
 }
}
