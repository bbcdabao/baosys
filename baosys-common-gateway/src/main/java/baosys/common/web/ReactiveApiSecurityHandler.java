package baosys.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.server.BearerTokenServerAccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 响应式业务 API 的认证授权错误，保留标准 Bearer challenge。
 */
public final class ReactiveApiSecurityHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {
    private final ObjectMapper mapper;
    public ReactiveApiSecurityHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        // 默认响应式处理器会完成响应；使用隔离的响应装饰器只捕获状态和协议头。
        return new BearerTokenServerAuthenticationEntryPoint().commence(uncommitted(exchange), ex)
            .then(Mono.defer(() -> ReactiveApiResponses.write(exchange, 401, mapper)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return new BearerTokenServerAccessDeniedHandler().handle(uncommitted(exchange), ex)
            .then(Mono.defer(() -> ReactiveApiResponses.write(exchange, 403, mapper)));
    }

    private ServerWebExchange uncommitted(ServerWebExchange exchange) {
        return exchange.mutate().response(new org.springframework.http.server.reactive.ServerHttpResponseDecorator(
            exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() { return Mono.empty(); }
        }).build();
    }

}
