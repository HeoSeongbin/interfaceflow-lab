package lab.interfaceflow.integration;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface DeliveryMapper {
 String advisory(String key);
 String existing(@Param("interfaceId") String interfaceId,@Param("source") String source,@Param("requestId") String requestId,@Param("scope") String scope);
 void insert(@Param("id") String id,@Param("interfaceId") String interfaceId,@Param("source") String source,@Param("requestId") String requestId,@Param("scope") String scope,@Param("hash") String hash,@Param("cipher") String cipher,@Param("max") int max,@Param("state") String state);
 String row(String id);
 String lock(String id);
 List<String> due();
 List<String> list();
 void claim(@Param("id") String id,@Param("token") String token);
 int sending(@Param("id") String id,@Param("attempt") String attempt);
 void expired(String id);
 void closeAttempt(String id);
 void finish(@Param("id") String id,@Param("state") String state,@Param("retryable") boolean retryable,@Param("error") String error,@Param("delay") long delay);
 void manual(String id);
}
