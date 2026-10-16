package lab.interfaceflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
class HttpContractTest {
    @Autowired MockMvc http;
    @MockitoBean TransmissionService service;

    @Test void invalidJsonHasStableErrorAndGeneratedTrace() throws Exception {
        http.perform(post("/api/requests").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{32}")));
        verifyNoInteractions(service);
    }

    @Test void unsupportedMediaIsNotInternalServerError() throws Exception {
        http.perform(post("/api/requests").contentType("text/plain").content("{}"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(service);
    }

    @Test void oversizedRequestHasJsonError() throws Exception {
        http.perform(post("/api/requests").contentType("application/json").content(" ".repeat(65537)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        verifyNoInteractions(service);
    }
}
