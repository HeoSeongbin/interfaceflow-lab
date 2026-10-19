package lab.interfaceflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class InterfaceModels {
    private InterfaceModels() {}
    public enum Transform { COPY, STRING, NUMBER, BOOLEAN, DATE }
    public enum NullPolicy { KEEP, OMIT, ERROR, DEFAULT }
    public enum Stage { SOURCE, TARGET }
    public enum DataType { STRING, NUMBER, BOOLEAN, OBJECT, ARRAY }

    public record MappingRule(String source, @NotBlank String target, @NotNull Transform transform,
        JsonNode defaultValue, boolean required, @NotNull NullPolicy nullPolicy,
        String sourceFormat, String targetFormat) {}

    public record ValidationRule(@NotBlank String field, @NotNull Stage stage,
        @NotNull DataType type, boolean required, boolean nullable,
        @Min(0) Integer minLength, @Min(0) Integer maxLength,
        BigDecimal minValue, BigDecimal maxValue, @Size(max=100) List<JsonNode> enumValues,
        @Size(max=100) String dateFormat, @Size(max=256) String pattern) {}

    public record Definition(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String interfaceId,
        @NotBlank @Size(max=120) String interfaceName, @Size(max=1000) String description,
        @NotBlank String sourceSystem, @NotBlank String targetSystem,
        @NotNull @Pattern(regexp="POST") String method,
        @NotNull @Pattern(regexp="/api/receive") String path, boolean enabled,
        @Min(100) @Max(10000) int timeoutMs, @Min(1) @Max(5) int retryMaxAttempts,
        @Min(0) int version,
        @NotNull @Size(max=100) List<@NotNull @Valid MappingRule> mappings,
        @NotNull @Size(max=100) List<@NotNull @Valid ValidationRule> validations) {
        public Definition next(boolean active, List<MappingRule> fields, List<ValidationRule> rules) {
            return new Definition(interfaceId,interfaceName,description,sourceSystem,targetSystem,
                method,path,active,timeoutMs,retryMaxAttempts,version+1,fields,rules);
        }
    }

    public record SystemInfo(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String systemId,
        @NotBlank @Size(max=120) String systemName, @NotNull @Pattern(regexp="SOURCE|TARGET") String role) {}
    public record StatusChange(boolean enabled, @Min(1) int version) {}
    public record MappingChange(@Min(1) int version, @NotNull @Size(max=100) List<@NotNull @Valid MappingRule> rules) {}
    public record ValidationChange(@Min(1) int version, @NotNull @Size(max=100) List<@NotNull @Valid ValidationRule> rules) {}
}
