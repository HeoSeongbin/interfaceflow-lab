package lab.interfaceflow.common;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public final class Contracts {
    private Contracts() {}
    public record SendRequest(
        @Pattern(regexp="IF-USER-001") @NotBlank String interfaceId,
        @Pattern(regexp="[A-Za-z0-9_-]{1,80}") @NotBlank String requestId,
        @NotNull JsonNode payload) {
        @AssertTrue(message="payload must be a JSON object")
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isPayloadObject(){return payload!=null && payload.isObject();}
    }
    public record TargetResponse(String code, String requestId, String traceId) {}
    public record SendResult(String id, String requestId, String traceId, String attemptId,
        String status, Integer httpStatus, JsonNode response, String errorCode) {}
}
