package lab.interfaceflow.target;

import org.apache.ibatis.annotations.*;
import lab.interfaceflow.common.Simulations.Input;
import java.util.List;

@Mapper
public interface SimulationMapper {
 void insert(@Param("id") String id,@Param("input") Input input);
 String find(String id);
 List<String> list();
 Integer consume(@Param("id") String id,@Param("interfaceId") String interfaceId,@Param("requestId") String requestId);
 void receipt(@Param("id") String id,@Param("requestId") String requestId,@Param("traceId") String traceId,
     @Param("attemptId") String attemptId,@Param("sequence") int sequence,@Param("httpStatus") int httpStatus);
}
