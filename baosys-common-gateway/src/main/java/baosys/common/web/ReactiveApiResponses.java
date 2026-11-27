package baosys.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 响应式错误序列化，不依赖 Spring Security。
 */
public final class ReactiveApiResponses {

    private ReactiveApiResponses() {}

    public static Mono<Void> write(ServerWebExchange exchange, int status, ObjectMapper mapper) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        String traceId = TraceWebFilter.traceId(exchange);
        try {
            byte[] body = mapper.writeValueAsBytes(ApiErrors.response(status, traceId));
            response.setStatusCode(HttpStatusCode.valueOf(status));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setContentLength(body.length);
            response.getHeaders().set(TraceIds.HEADER_NAME, traceId);
            if (HttpMethod.HEAD.equals(exchange.getRequest().getMethod())) {
                return response.setComplete();
            }
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }
}
