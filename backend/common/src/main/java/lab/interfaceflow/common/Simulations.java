package lab.interfaceflow.common;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class Simulations {
    private Simulations() {}
    public enum Type { SUCCESS, HTTP_400, HTTP_500, DELAY, TIMEOUT, INVALID_RESPONSE, FAIL_THEN_SUCCESS, DUPLICATE, CONNECTION_FAILURE }
    public record Input(@NotNull Type type,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String interfaceId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String requestId,
        @Min(0) @Max(15000) int delayMs,@Min(0) @Max(5) int failureCount,@NotNull @Future Instant expiresAt) {}
    public record Scenario(String id,Type type,String interfaceId,String requestId,int delayMs,int failureCount,Instant expiresAt,int uses) {}
    public static boolean validId(String id){return id!=null && id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
}
