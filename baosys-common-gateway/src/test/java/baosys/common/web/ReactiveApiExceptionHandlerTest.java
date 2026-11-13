package baosys.common.web;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.MethodNotAllowedException;

import static org.assertj.core.api.Assertions.*;

class ReactiveApiExceptionHandlerTest {
    private final ReactiveApiExceptionHandler handler = new ReactiveApiExceptionHandler(
        Jackson2ObjectMapperBuilder.json().build());

    @Test
    void preservesMethodAllowHeaderAndTrace() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/test")
            .header(TraceIds.HEADER_NAME, "error-trace"));
        handler.handle(exchange, new MethodNotAllowedException("POST", List.of(HttpMethod.GET))).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(exchange.getResponse().getHeaders().getAllow()).containsExactly(HttpMethod.GET);
        assertThat(exchange.getResponse().getBodyAsString().block())
            .contains("METHOD_NOT_ALLOWED", "error-trace");
    }

    @Test
    void rejectionReturnsServiceUnavailableWithoutExceptionDetails() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));
        handler.handle(exchange, new RejectedExecutionException("private pool details")).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
            .contains("SERVICE_UNAVAILABLE").doesNotContain("private pool details");
    }

    @Test
    void leavesNonApiAndCommittedResponsesToOriginalChain() {
        var error = new IllegalStateException("failure");
        var page = MockServerWebExchange.from(MockServerHttpRequest.get("/login"));
        assertThatThrownBy(() -> handler.handle(page, error).block()).isSameAs(error);
        var committed = MockServerWebExchange.from(MockServerHttpRequest.get("/api/stream"));
        committed.getResponse().setComplete().block();
        assertThatThrownBy(() -> handler.handle(committed, error).block()).isSameAs(error);
        assertThat(committed.getResponse().getBodyAsString().block()).isEmpty();
    }
}
