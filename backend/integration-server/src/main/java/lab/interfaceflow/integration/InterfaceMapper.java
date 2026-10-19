package lab.interfaceflow.integration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import lab.interfaceflow.integration.InterfaceModels.*;

@Mapper
public interface InterfaceMapper {
    List<SystemInfo> systems();
    void insertSystem(SystemInfo system);
    String role(String id);
    List<String> snapshots(@Param("query") String query);
    String snapshot(String id);
    String lock(String id);
    String shareLock(String id);
    void insert(Definition definition);
    int update(@Param("definition") Definition definition, @Param("expected") int expected);
    void insertVersion(@Param("id") String id, @Param("version") int version, @Param("snapshot") String snapshot);
    void insertMapping(@Param("id") String id,@Param("version") int version,@Param("position") int position,@Param("rule") String rule);
    void insertValidation(@Param("id") String id,@Param("version") int version,@Param("position") int position,@Param("rule") String rule);
    boolean hasRequests(String id);
    int delete(String id);
    List<String> versions(String id);
}
