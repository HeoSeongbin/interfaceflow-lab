package lab.interfaceflow.integration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import lab.interfaceflow.common.Contracts.SendRequest;
import lab.interfaceflow.integration.InterfaceModels.Definition;
import java.util.List;

@Mapper
public interface RequestMapper {
    void insert(@Param("id") String id,@Param("request") SendRequest request,@Param("traceId") String traceId,
        @Param("definition") Definition definition,@Param("original") String original);
    int prepared(@Param("id") String id,@Param("mapped") String mapped,@Param("status") String status,
        @Param("stage") String stage,@Param("errors") String errors,@Param("duration") long duration);
    int sending(@Param("id") String id,@Param("attemptId") String attemptId);
    int complete(@Param("id") String id,@Param("status") String status,@Param("httpStatus") Integer httpStatus,
        @Param("errorCode") String errorCode,@Param("response") String response,@Param("duration") long duration);
    List<String> history(@Param("query") String query,@Param("status") String status,@Param("size") int size,@Param("offset") int offset,@Param("from") java.time.Instant from,@Param("to") java.time.Instant to);
    long count(@Param("query") String query,@Param("status") String status,@Param("from") java.time.Instant from,@Param("to") java.time.Instant to);
    String detail(String id);
}
