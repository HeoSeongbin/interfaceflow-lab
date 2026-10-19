package lab.interfaceflow.target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceiveController.class)
class HttpContractTest {
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String BODY = """
        {"interfaceId":"IF-USER-001","requestId":"REQ-1","payload":{"userId":"U001"}}
        """;
    @Autowired MockMvc http;
    @MockitoBean ReceiptMapper mapper;
    @MockitoBean SimulationService simulations;

    @Test void missingCorrelationHeadersAreBadRequest() throws Exception {
        http.perform(post("/api/receive").contentType("application/json")
                .header("X-Trace-Id", TRACE).content(BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value(TRACE));
        verifyNoInteractions(mapper);
        assertNull(MDC.get("traceId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"------------------------------------", "012345678901234567890123456789012345", "not-a-uuid"})
    void malformedAttemptNeverReachesDatabase(String attempt) throws Exception {
        http.perform(post("/api/receive").contentType("application/json")
                .header("X-Trace-Id", TRACE).header("X-Request-Id", "REQ-1")
                .header("X-Attempt-Id", attempt).content(BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(mapper);
    }

    @Test void unsupportedMediaAndMethodPreserveHttpSemantics() throws Exception {
        http.perform(post("/api/receive").contentType("text/plain").content(BODY))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        http.perform(get("/api/receive"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "POST"))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(mapper);
    }

    @Test void oversizedRequestHasJsonErrorAndTrace() throws Exception {
        http.perform(post("/api/receive").contentType("application/json")
                .header("X-Trace-Id", TRACE).content(" ".repeat(65537)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
            .andExpect(jsonPath("$.traceId").value(TRACE));
        verifyNoInteractions(mapper);
        assertNull(MDC.get("traceId"));
    }
}
