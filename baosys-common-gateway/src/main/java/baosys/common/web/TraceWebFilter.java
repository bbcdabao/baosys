package baosys.common.web;

import org.springframework.core.Ordered;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/**
 * 在响应式安全链之前建立追踪上下文，并将同一 ID 转发给下游。
 */
public final class TraceWebFilter implements WebFilter, Ordered {
    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    public static String traceId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().computeIfAbsent(TraceIds.ATTRIBUTE,
            key -> TraceIds.normalize(exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER_NAME)));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = traceId(exchange);
        exchange.getResponse().getHeaders().set(TraceIds.HEADER_NAME, traceId);
        var forwarded = exchange.mutate().request(builder ->
            builder.headers(headers -> headers.set(TraceIds.HEADER_NAME, traceId))).build();
        return Mono.defer(() -> chain.filter(forwarded))
            .contextWrite(context -> context.put(TraceIds.MDC_KEY, traceId));
    }
}
