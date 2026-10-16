package lab.interfaceflow.integration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface RequestMapper {
    void insert(@Param("id") String id,@Param("requestId") String requestId,@Param("traceId") String traceId,@Param("attemptId") String attemptId);
    void complete(@Param("id") String id,@Param("status") String status,@Param("httpStatus") Integer httpStatus,@Param("errorCode") String errorCode);
}
