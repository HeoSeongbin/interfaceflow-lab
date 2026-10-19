package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lab.interfaceflow.integration.InterfaceModels.*;
import java.util.*;

@Service
public class InterfaceService {
    private final InterfaceMapper mapper;
    private final ObjectMapper json;
    private final PayloadEngine engine;
    private final Validator validator;
    public InterfaceService(InterfaceMapper mapper,ObjectMapper json,PayloadEngine engine,Validator validator) {
        this.mapper=mapper;this.json=json;this.engine=engine;this.validator=validator;
    }
    public List<SystemInfo> systems(){return mapper.systems();}
    public SystemInfo addSystem(SystemInfo system) {
        if(!"SOURCE".equals(system.role())) throw new ApiProblem(400,"TARGET_NOT_ALLOWED","대상 시스템은 서버 허용 목록으로 관리합니다.");
        try{mapper.insertSystem(system);}catch(DuplicateKeyException e){throw new ApiProblem(409,"SYSTEM_EXISTS","이미 등록된 시스템입니다.");}
        return system;
    }
    public List<Definition> list(String query){return mapper.snapshots(query).stream().map(this::decode).toList();}
    public Definition get(String id) {
        String snapshot=mapper.snapshot(id);
        if(snapshot==null) throw new ApiProblem(404,"INTERFACE_NOT_FOUND","인터페이스를 찾을 수 없습니다.");
        return decode(snapshot);
    }
    // Must be invoked inside the short request-registration transaction.
    public Definition forDispatch(String id) {
        if(mapper.shareLock(id)==null) throw new ApiProblem(400,"INTERFACE_NOT_FOUND","인터페이스를 찾을 수 없습니다.");
        var definition=get(id);
        if(!definition.enabled()) throw new ApiProblem(400,"INTERFACE_DISABLED","비활성화된 인터페이스입니다.");
        return definition;
    }
    @Transactional
    public Definition create(Definition input) {
        check(input);
        if(input.version()!=0) throw new ApiProblem(400,"INVALID_VERSION","신규 인터페이스 version은 0입니다.");
        var saved=input.next(input.enabled(),input.mappings(),input.validations());
        try{mapper.insert(saved);}catch(DuplicateKeyException e){throw new ApiProblem(409,"INTERFACE_EXISTS","이미 등록된 Interface ID입니다.");}
        version(saved);return saved;
    }
    @Transactional
    public Definition update(String id,Definition input) {
        if(!id.equals(input.interfaceId())) throw new ApiProblem(400,"INTERFACE_ID_MISMATCH","경로와 본문의 Interface ID가 다릅니다.");
        check(input);
        return replace(input);
    }
    @Transactional
    public Definition status(String id,StatusChange input) {
        var current=locked(id,input.version());
        return replace(new Definition(current.interfaceId(),current.interfaceName(),current.description(),current.sourceSystem(),current.targetSystem(),current.method(),current.path(),input.enabled(),current.timeoutMs(),current.retryMaxAttempts(),current.version(),current.mappings(),current.validations()));
    }
    @Transactional
    public Definition mappings(String id,MappingChange input) {
        var current=locked(id,input.version());
        var changed=new Definition(current.interfaceId(),current.interfaceName(),current.description(),current.sourceSystem(),current.targetSystem(),current.method(),current.path(),current.enabled(),current.timeoutMs(),current.retryMaxAttempts(),current.version(),input.rules(),current.validations());
        check(changed);return replace(changed);
    }
    @Transactional
    public Definition validations(String id,ValidationChange input) {
        var current=locked(id,input.version());
        var changed=new Definition(current.interfaceId(),current.interfaceName(),current.description(),current.sourceSystem(),current.targetSystem(),current.method(),current.path(),current.enabled(),current.timeoutMs(),current.retryMaxAttempts(),current.version(),current.mappings(),input.rules());
        check(changed);return replace(changed);
    }
    @Transactional
    public Map<String,Object> delete(String id) {
        if(mapper.lock(id)==null) throw new ApiProblem(404,"INTERFACE_NOT_FOUND","인터페이스를 찾을 수 없습니다.");
        var current=get(id);
        if(mapper.hasRequests(id)) {
            if(current.enabled()) status(id,new StatusChange(false,current.version()));
            return Map.of("deleted",false,"disabled",true);
        }
        mapper.delete(id);return Map.of("deleted",true,"disabled",false);
    }
    public List<Definition> versions(String id){get(id);return mapper.versions(id).stream().map(this::decode).toList();}
    private Definition locked(String id,int version) {
        if(mapper.lock(id)==null) throw new ApiProblem(404,"INTERFACE_NOT_FOUND","인터페이스를 찾을 수 없습니다.");
        var current=get(id);
        if(current.version()!=version) throw new ApiProblem(409,"VERSION_CONFLICT","다른 작업에서 설정을 변경했습니다. 다시 조회하세요.");
        return current;
    }
    private Definition replace(Definition input) {
        locked(input.interfaceId(),input.version());
        var saved=input.next(input.enabled(),input.mappings(),input.validations());
        if(mapper.update(saved,input.version())!=1) throw new ApiProblem(409,"VERSION_CONFLICT","설정 버전 충돌입니다.");
        version(saved);return saved;
    }
    private void check(Definition definition) {
        if(!validator.validate(definition).isEmpty()) throw new ApiProblem(400,"INVALID_CONFIGURATION","필수 설정과 범위를 확인하세요.");
        if(!"SOURCE".equals(mapper.role(definition.sourceSystem())) || !"TARGET".equals(mapper.role(definition.targetSystem())))
            throw new ApiProblem(400,"SYSTEM_NOT_ALLOWED","등록된 출발·대상 시스템을 선택하세요.");
        engine.validateDefinition(definition);
    }
    private void version(Definition definition) {
        mapper.insertVersion(definition.interfaceId(),definition.version(),encode(definition));
        for(int i=0;i<definition.mappings().size();i++) mapper.insertMapping(definition.interfaceId(),definition.version(),i,encode(definition.mappings().get(i)));
        for(int i=0;i<definition.validations().size();i++) mapper.insertValidation(definition.interfaceId(),definition.version(),i,encode(definition.validations().get(i)));
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Configuration serialization failed",e);}}
    private Definition decode(String value){try{return json.readValue(value,Definition.class);}catch(Exception e){throw new IllegalStateException("Stored configuration is invalid",e);}}
}
