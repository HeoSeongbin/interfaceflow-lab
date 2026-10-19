package lab.interfaceflow.target;

import lab.interfaceflow.common.*;
import lab.interfaceflow.common.Contracts.SendRequest;
import lab.interfaceflow.common.Simulations.Type;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class BusinessService {
 private final BusinessMapper mapper;private final ReceiptMapper receipts;private final SimulationService simulations;
 public BusinessService(BusinessMapper mapper,ReceiptMapper receipts,SimulationService simulations){this.mapper=mapper;this.receipts=receipts;this.simulations=simulations;}
 @Transactional
 public SimulationService.Plan plan(SendRequest r,String source,String scope,String trace,String attempt,String scenario){
  if(source==null || !source.matches("[A-Za-z0-9_-]{1,80}") || !("normal".equals(scope)||Simulations.validId(scope))
      || !(scenario==null?"normal".equals(scope):Objects.equals(scope,scenario)))throw new SimulationService.Problem(400,"INVALID_IDEMPOTENCY_SCOPE");
  String key=java.util.List.of(r.interfaceId(),source,r.requestId(),scope).toString();mapper.lock(key);
  String hash=CanonicalJson.hash(r.payload()),existing=mapper.hash(r,source,scope);
  if(existing!=null){
   if(!existing.equals(hash))throw new SimulationService.Problem(409,"PAYLOAD_CONFLICT");
   mapper.replayReceipt(r.requestId(),trace,attempt,scenario);
   return new SimulationService.Plan(Type.SUCCESS,0,200,"OK");
  }
  var plan=scenario==null?new SimulationService.Plan(Type.SUCCESS,0,200,"OK"):simulations.plan(scenario,r.interfaceId(),r.requestId(),trace,attempt);
  if(scenario==null)receipts.insert(r.requestId(),trace,attempt);
  // Commit the business result before delayed/lost/malformed HTTP responses.
  if(plan.status()==200)mapper.insert(r,source,scope,hash);
  return plan;
 }
}
