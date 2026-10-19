package lab.interfaceflow.integration;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TraceFilterTest {
    @Test void bodyLimitAppliesWithoutContentLengthAndMdcIsCleared() throws Exception {
        var request = requestWithUnknownLength(65537);
        var response = new MockHttpServletResponse();
        var calls = new AtomicInteger();
        new TraceFilter().doFilter(request, response, (req, res) -> calls.incrementAndGet());
        assertEquals(413, response.getStatus());
        assertEquals(0, calls.get());
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertEquals("PAYLOAD_TOO_LARGE", body.path("code").asText());
        assertEquals(response.getHeader("X-Trace-Id"), body.path("traceId").asText());
        assertNull(MDC.get("traceId"));
    }

    @Test void exactly64KiBIsAllowedAndReplayedIntact() throws Exception {
        var request = requestWithUnknownLength(65536);
        var response = new MockHttpServletResponse();
        new TraceFilter().doFilter(request, response, (req, res) ->
            assertEquals(65536, req.getInputStream().readAllBytes().length));
        assertEquals(200, response.getStatus());
        assertNull(MDC.get("traceId"));
    }

    @Test void identifiersPropagateAndMdcClearsEvenOnFailure() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/requests");
        String trace="0123456789abcdef0123456789abcdef",attempt=java.util.UUID.randomUUID().toString();
        request.addHeader("X-Trace-Id",trace);request.addHeader("X-Request-Id","REQ-TRACE");request.addHeader("X-Attempt-Id",attempt);
        assertThrows(jakarta.servlet.ServletException.class,()->new TraceFilter().doFilter(request,new MockHttpServletResponse(),(req,res)->{
            assertEquals(trace,MDC.get("traceId"));assertEquals(attempt,MDC.get("attemptId"));assertEquals("REQ-TRACE",MDC.get("requestId"));
            throw new jakarta.servlet.ServletException("test failure");
        }));
        assertNull(MDC.get("traceId"));assertNull(MDC.get("requestId"));assertNull(MDC.get("attemptId"));
    }
    private MockHttpServletRequest requestWithUnknownLength(int bytes) {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setMethod("POST"); request.setRequestURI("/api/requests");
        request.setContent(" ".repeat(bytes).getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
