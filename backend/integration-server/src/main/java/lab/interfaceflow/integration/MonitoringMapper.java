package lab.interfaceflow.integration;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MonitoringMapper {
    void event(@Param("id") String id,@Param("attempt") String attempt,@Param("stage") String stage,@Param("outcome") String outcome);
    void startAttempt(@Param("id") String id,@Param("attempt") String attempt);
    int finishAttempt(@Param("id") String id);
    List<String> trace(String traceId);
    String summary(@Param("from") java.time.Instant from,@Param("to") java.time.Instant to);
    List<String> statistics(@Param("from") java.time.Instant from,@Param("to") java.time.Instant to);
}
