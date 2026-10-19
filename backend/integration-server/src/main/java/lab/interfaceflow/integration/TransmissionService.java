package lab.interfaceflow.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import lab.interfaceflow.common.Contracts.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class TransmissionService {
    public record Preview(int interfaceVersion,boolean valid,String processingStage,
        JsonNode originalPayload,JsonNode mappedPayload,List<FieldError> errors) {}
    private final RequestLifecycle lifecycle;
    private final InterfaceService interfaces;
    private final PayloadEngine engine;
    private final PayloadRedactor redactor;
    private final ObjectMapper json;
    private final String targetUrl;
    private final HttpClient httpClient;
    @Value("${lab.connection-failure-url:http://127.0.0.1:1}")
    private String connectionFailureUrl="http://127.0.0.1:1";

    public TransmissionService(RequestLifecycle lifecycle,InterfaceService interfaces,PayloadEngine engine,
            PayloadRedactor redactor,ObjectMapper json,@Value("${lab.target-url}") String targetUrl,
            @Value("${lab.timeout-ms}") int connectTimeoutMs) {
        this.lifecycle=lifecycle;this.interfaces=interfaces;this.engine=engine;this.redactor=redactor;
        this.json=json;this.targetUrl=targetUrl;
        httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    }
    public Preview preview(String id,JsonNode payload) {
        if(payload==null || !payload.isObject()) throw new ApiProblem(400,"VALIDATION_ERROR","payload는 JSON 객체여야 합니다.");
        var definition=interfaces.get(id);var prepared=engine.prepare(payload,definition);
        return new Preview(definition.version(),prepared.valid(),prepared.stage(),redactor.redact(payload),redactor.mapped(prepared.mapped(),definition),prepared.errors());
    }
    // The lifecycle bean commits registration before any validation/HTTP work starts.
    public SendResult send(SendRequest request,String traceId) {
        return send(request,traceId,null);
    }
    public SendResult send(SendRequest request,String traceId,lab.interfaceflow.common.Simulations.Scenario scenario) {
        long start=System.nanoTime();
        org.slf4j.MDC.put("requestId",request.requestId());
        var accepted=scenario==null?lifecycle.accept(request,traceId):lifecycle.acceptSimulation(request,traceId,scenario);
        var definition=accepted.definition();String id=accepted.id();
        var prepared=engine.prepare(request.payload(),definition);
        lifecycle.prepared(id,prepared,definition,elapsed(start));
        if(!prepared.valid()) return new SendResult(id,request.requestId(),traceId,null,prepared.status(),null,null,
            prepared.status().equals("MAPPING_FAILED")?"MAPPING_ERROR":"VALIDATION_ERROR",prepared.stage(),definition.version(),
            redactor.redact(request.payload()),redactor.mapped(prepared.mapped(),definition),prepared.errors());
        String attemptId=UUID.randomUUID().toString();
        lifecycle.sending(id,attemptId);org.slf4j.MDC.put("attemptId",attemptId);
        String status="UNKNOWN",error=null;Integer httpStatus=null;JsonNode response=null;
        var factory=new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(definition.timeoutMs()));
        boolean connectionFailure=scenario!=null && scenario.type()==lab.interfaceflow.common.Simulations.Type.CONNECTION_FAILURE;
        var client=RestClient.builder().baseUrl(connectionFailure?connectionFailureUrl:targetUrl).requestFactory(factory).build();
        try {
            var entity=client.post().uri(definition.path()).header("X-Trace-Id",traceId)
                .header("X-Request-Id",request.requestId()).header("X-Attempt-Id",attemptId)
                .headers(headers->{if(scenario!=null)headers.set("X-Simulation-Id",scenario.id());})
                .body(new SendRequest(request.interfaceId(),request.requestId(),prepared.mapped())).retrieve().toEntity(String.class);
            httpStatus=entity.getStatusCode().value();response=parseResponse(entity.getBody());
            if(httpStatus>=200 && httpStatus<300 && response!=null && response.isObject() && "OK".equals(response.path("code").asText())
                    && traceId.equals(response.path("traceId").asText()) && request.requestId().equals(response.path("requestId").asText())) status="SUCCESS";
            else error="INVALID_RESPONSE";
        } catch(RestClientResponseException e) {
            httpStatus=e.getStatusCode().value();error="TARGET_HTTP_ERROR";status=httpStatus>=500?"UNKNOWN":"FAILED";
            response=parseResponse(e.getResponseBodyAsString());
        } catch(ResourceAccessException e) {error="TARGET_IO_ERROR";}
        catch(RestClientException e) {error="INVALID_RESPONSE";}
        lifecycle.complete(id,status,httpStatus,error,response,elapsed(start));
        return new SendResult(id,request.requestId(),traceId,attemptId,status,httpStatus,redactor.redact(response),error,
            "RESPONSE_PROCESSED",definition.version(),redactor.redact(request.payload()),redactor.mapped(prepared.mapped(),definition),List.of());
    }
    private JsonNode parseResponse(String value) {
        if(value==null || value.isBlank()) return null;
        try{return json.readTree(value);}catch(Exception e){return json.createObjectNode().put("invalidJson",true);}
    }
    private long elapsed(long start){return (System.nanoTime()-start)/1_000_000;}
}
