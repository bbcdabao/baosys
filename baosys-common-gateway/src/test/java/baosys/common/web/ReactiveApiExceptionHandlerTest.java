package baosys.common.web;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.MethodNotAllowedException;
import reactor.netty.http.client.PrematureCloseException;

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

    @Test
    void mapsUpstreamTransportAndTimeoutErrorsToGatewayStatuses() {
        for (Throwable badGateway : List.of(
            new ConnectException("Connection refused"),
            new UnknownHostException("downstream.internal"),
            new RuntimeException("wrapped", org.mockito.Mockito.mock(PrematureCloseException.class))
        )) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/upstream"));
            handler.handle(exchange, badGateway).block();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(exchange.getResponse().getHeaders().getContentLength()).isPositive();
            assertThat(exchange.getResponse().getBodyAsString().block()).contains("BAD_GATEWAY");
        }

        for (Throwable timeout : List.of(
            new TimeoutException("timed out"),
            new ConnectTimeoutException("connect timed out"),
            new RuntimeException("wrapped", ReadTimeoutException.INSTANCE)
        )) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/upstream"));
            handler.handle(exchange, timeout).block();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(exchange.getResponse().getBodyAsString().block()).contains("GATEWAY_TIMEOUT");
        }
    }

    @Test
    void headRequestsOmitErrorBodyWhilePreservingContentLength() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.head("/api/test"));
        handler.handle(exchange, new RejectedExecutionException("busy")).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentLength()).isPositive();
        assertThat(exchange.getResponse().getBodyAsString().block()).isEmpty();
    }
}
