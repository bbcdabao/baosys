package baosys.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    private MockMvc mvc;
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Endpoints()).setControllerAdvice(new Errors())
            .addFilters(new TraceIdFilter()).build();
    }

    @Test
    void preservesHttpStatusHeadersAndTraceWithoutLeakingDetails() throws Exception {
        mvc.perform(get("/required").header("X-Trace-Id", "contract"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value("contract")).andExpect(header().string("X-Trace-Id", "contract"));
        mvc.perform(post("/required")).andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow")).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/json").contentType("text/plain").content("secret"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/json").contentType("application/json").content("{secret"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/missing")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/failure")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("服务器内部错误"));
        mvc.perform(get("/busy")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @RestControllerAdvice
    static class Errors extends ApiExceptionHandler {}
    @RestController
    static class Endpoints {
        @GetMapping("/required") String required(@RequestParam String value) { return value; }
        @PostMapping(value = "/json", consumes = "application/json") Object json(@RequestBody java.util.Map<?, ?> body) { return body; }
        @GetMapping("/missing") void missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "secret"); }
        @GetMapping("/failure") void failure() { throw new IllegalStateException("password=secret"); }
        @GetMapping("/busy") void busy() { throw new java.util.concurrent.RejectedExecutionException("full"); }
    }
}
