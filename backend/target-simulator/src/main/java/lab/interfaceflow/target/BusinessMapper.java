package lab.interfaceflow.target;
import org.apache.ibatis.annotations.*;
import lab.interfaceflow.common.Contracts.SendRequest;
@Mapper
public interface BusinessMapper {
 String lock(String key);
 String hash(@Param("r") SendRequest request,@Param("source") String source,@Param("scope") String scope);
 void insert(@Param("r") SendRequest request,@Param("source") String source,@Param("scope") String scope,@Param("hash") String hash);
 void replayReceipt(@Param("requestId") String requestId,@Param("trace") String trace,@Param("attempt") String attempt,@Param("scenario") String scenario);
}
