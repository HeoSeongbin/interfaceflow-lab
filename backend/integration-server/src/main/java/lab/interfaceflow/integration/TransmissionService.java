package lab.interfaceflow.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lab.interfaceflow.common.Contracts.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class TransmissionService {
    private final RequestMapper mapper;
    private final RestClient client;
    private final ObjectMapper json;
    public TransmissionService(RequestMapper mapper,ObjectMapper json,
            @Value("${lab.target-url}") String targetUrl,@Value("${lab.timeout-ms}") int timeoutMs) {
        this.mapper=mapper; this.json=json;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        client=RestClient.builder().baseUrl(targetUrl).requestFactory(factory).build();
    }
    // Each mapper statement commits independently: no DB transaction spans HTTP.
    public SendResult send(SendRequest request,String traceId) {
        String id=UUID.randomUUID().toString(),attemptId=UUID.randomUUID().toString();
        org.slf4j.MDC.put("requestId",request.requestId());
        org.slf4j.MDC.put("attemptId",attemptId);
        mapper.insert(id,request.requestId(),traceId,attemptId);
        String status="FAILED",error=null; Integer httpStatus=null; JsonNode response=null;
        try {
            var entity=client.post().uri("/api/receive").header("X-Trace-Id",traceId)
                .header("X-Request-Id",request.requestId()).header("X-Attempt-Id",attemptId)
                .body(request).retrieve().toEntity(TargetResponse.class);
            httpStatus=entity.getStatusCode().value();
            var body=entity.getBody(); response=json.valueToTree(body);
            if(body!=null && "OK".equals(body.code()) && traceId.equals(body.traceId()) && request.requestId().equals(body.requestId())) status="SUCCESS";
            else {status="UNKNOWN";error="INVALID_RESPONSE";}
        } catch(RestClientResponseException e) {httpStatus=e.getStatusCode().value();error="TARGET_HTTP_ERROR";status=httpStatus>=500?"UNKNOWN":"FAILED";}
        catch(ResourceAccessException e) {status="UNKNOWN";error="TARGET_IO_ERROR";}
        catch(org.springframework.web.client.RestClientException e) {status="UNKNOWN";error="INVALID_RESPONSE";}
        mapper.complete(id,status,httpStatus,error);
        return new SendResult(id,request.requestId(),traceId,attemptId,status,httpStatus,response,error);
    }
}
