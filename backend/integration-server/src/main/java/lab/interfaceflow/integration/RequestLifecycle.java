package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.*;
import lab.interfaceflow.common.Contracts.SendRequest;
import lab.interfaceflow.integration.InterfaceModels.Definition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RequestLifecycle {
    public record Accepted(String id,Definition definition) {}
    public record HistoryPage(List<JsonNode> items,long total,int page,int size) {}
    private final InterfaceService interfaces;
    private final RequestMapper mapper;
    private final PayloadRedactor redactor;
    private final ObjectMapper json;
    public RequestLifecycle(InterfaceService interfaces,RequestMapper mapper,PayloadRedactor redactor,ObjectMapper json) {
        this.interfaces=interfaces;this.mapper=mapper;this.redactor=redactor;this.json=json;
    }
    @Transactional
    public Accepted accept(SendRequest request,String traceId) {
        var definition=interfaces.forDispatch(request.interfaceId());
        String id=UUID.randomUUID().toString();
        mapper.insert(id,request,traceId,definition,encode(redactor.redact(request.payload())));
        return new Accepted(id,definition);
    }
    public void prepared(String id,PayloadEngine.Prepared prepared,Definition definition,long duration) {
        changed(mapper.prepared(id,encode(redactor.mapped(prepared.mapped(),definition)),prepared.status(),prepared.stage(),encode(prepared.errors()),duration));
    }
    public void sending(String id,String attemptId){changed(mapper.sending(id,attemptId));}
    public void complete(String id,String status,Integer httpStatus,String error,JsonNode response,long duration) {
        changed(mapper.complete(id,status,httpStatus,error,encode(redactor.redact(response)),duration));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public HistoryPage history(String query,String status,int page,int size) {
        if(query.length()>160 || page<0 || page>1000000 || size<1 || size>100) throw new ApiProblem(400,"INVALID_FILTER","조회 범위를 확인하세요.");
        if(!status.isEmpty() && !Set.of("VALIDATING","VALIDATION_FAILED","MAPPING_FAILED","READY","SENDING","SUCCESS","FAILED","UNKNOWN").contains(status))
            throw new ApiProblem(400,"INVALID_FILTER","지원하지 않는 상태입니다.");
        return new HistoryPage(mapper.history(query,status,size,page*size).stream().map(this::decode).toList(),mapper.count(query,status),page,size);
    }
    public JsonNode detail(String id) {
        if(!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw new ApiProblem(400,"INVALID_REQUEST_KEY","내부 UUID로 조회하세요.");
        String value=mapper.detail(id);
        if(value==null) throw new ApiProblem(404,"REQUEST_NOT_FOUND","요청 이력을 찾을 수 없습니다.");
        return decode(value);
    }
    private void changed(int rows){if(rows!=1) throw new IllegalStateException("Request state transition rejected");}
    private String encode(Object value){try{return value==null?null:json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("History serialization failed",e);}}
    private JsonNode decode(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid stored history",e);}}
}
