package baosys.common.web;

import baosys.common.autoconfigure.ReactiveTraceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveTraceTest {
    @Test
    void concurrentRequestsKeepTraceAcrossSchedulerAndForwardIt() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveTraceAutoConfiguration.class))
            .run(context -> {
                var filter = context.getBean(TraceWebFilter.class);
                Flux.range(0, 40).flatMap(index -> {
                    String trace = "request-" + index;
                    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
                        .header(TraceIds.HEADER_NAME, trace));
                    return filter.filter(exchange, forwarded -> Mono.delay(Duration.ofMillis(1))
                        .publishOn(Schedulers.parallel()).doOnNext(ignored -> {
                            assertThat(MDC.get(TraceIds.MDC_KEY)).isEqualTo(trace);
                            assertThat(forwarded.getRequest().getHeaders().getFirst(TraceIds.HEADER_NAME)).isEqualTo(trace);
                            assertThat(exchange.getResponse().getHeaders().getFirst(TraceIds.HEADER_NAME)).isEqualTo(trace);
                        }).then());
                }).blockLast(Duration.ofSeconds(10));
                assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
                assertThat(Mono.fromCallable(() -> MDC.get(TraceIds.MDC_KEY))
                    .subscribeOn(Schedulers.parallel()).block()).isNull();
            });
    }
}
