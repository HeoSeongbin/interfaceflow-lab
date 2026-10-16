package lab.interfaceflow.target;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ReceiptMapper {
 void insert(@Param("requestId") String requestId,@Param("traceId") String traceId,@Param("attemptId") String attemptId);
}
