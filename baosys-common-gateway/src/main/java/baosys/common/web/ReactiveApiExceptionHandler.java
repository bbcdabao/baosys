package baosys.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/**
 * 网关业务 API 的路由及运行时错误；静态页面和标准协议端点交回原处理链。
 */
public final class ReactiveApiExceptionHandler implements WebExceptionHandler, Ordered {
    private final ObjectMapper mapper;
    public ReactiveApiExceptionHandler(ObjectMapper mapper) { this.mapper = mapper; }
    @Override
    public int getOrder() { return -2; }
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/") || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        int status = ex instanceof java.util.concurrent.RejectedExecutionException ? 503 : 500;
        if (ex instanceof ResponseStatusException error) {
            status = error.getStatusCode().value();
            exchange.getResponse().getHeaders().addAll(error.getHeaders());
        }
        if (status >= 500) LoggerFactory.getLogger(getClass()).error("Gateway request failed, traceId={}",
            TraceWebFilter.traceId(exchange), ex);
        return baosys.common.web.ReactiveApiResponses.write(exchange, status, mapper);
    }
}
