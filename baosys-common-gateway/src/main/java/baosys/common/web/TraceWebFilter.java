package baosys.common.web;

import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 在响应式安全链之前建立追踪上下文，并将同一 ID 转发给下游。
 */
public final class TraceWebFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    public static String traceId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().computeIfAbsent(TraceIds.ATTRIBUTE,
            key -> TraceIds.normalize(exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER_NAME)));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = traceId(exchange);
        exchange.getResponse().getHeaders().set(TraceIds.HEADER_NAME, traceId);
        // 下游服务返回响应头后可能携带同名 X-Trace-Id，在提交前统一覆写为单值，避免响应头重复。
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TraceIds.HEADER_NAME, traceId);
            return Mono.empty();
        });
        var forwarded = exchange.mutate().request(builder ->
            builder.headers(headers -> headers.set(TraceIds.HEADER_NAME, traceId))).build();
        return Mono.defer(() -> chain.filter(forwarded))
            .contextWrite(context -> context.put(TraceIds.MDC_KEY, traceId));
    }
}
