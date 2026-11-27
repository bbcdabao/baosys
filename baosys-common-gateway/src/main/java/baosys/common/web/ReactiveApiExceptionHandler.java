package baosys.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

/**
 * 网关业务 API 的路由及运行时错误；静态页面和标准协议端点交回原处理链。
 */
public final class ReactiveApiExceptionHandler implements WebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReactiveApiExceptionHandler.class);
    private static final int MAX_CAUSE_DEPTH = 16;

    private final ObjectMapper mapper;

    public ReactiveApiExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/") || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        int status;
        if (ex instanceof ErrorResponse error) {
            status = error.getStatusCode().value();
            exchange.getResponse().getHeaders().putAll(error.getHeaders());
        } else {
            status = resolveStatus(ex);
        }
        if (status >= 500) {
            log.error("Gateway request failed, status={}, traceId={}", status, TraceWebFilter.traceId(exchange), ex);
        }
        return ReactiveApiResponses.write(exchange, status, mapper);
    }

    private static int resolveStatus(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof RejectedExecutionException) {
                return 503;
            }
            if (
                current instanceof java.util.concurrent.TimeoutException ||
                current instanceof SocketTimeoutException ||
                current instanceof TimeoutException ||
                current instanceof ConnectTimeoutException
            ) {
                return 504;
            }
            if (
                current instanceof ConnectException ||
                current instanceof UnknownHostException ||
                current instanceof PrematureCloseException
            ) {
                return 502;
            }
            current = current.getCause();
        }
        return 500;
    }
}
