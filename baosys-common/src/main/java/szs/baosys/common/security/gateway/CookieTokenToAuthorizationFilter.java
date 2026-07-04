package szs.baosys.common.security.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import szs.baosys.common.security.config.SzsSecurityProperties;

/**
 * 将 access token Cookie 转换为 Authorization 请求头的网关全局过滤器。
 *
 * <p>网关自身完成认证后，下游 Servlet 资源服务仍然按标准 Bearer Token 读取请求头。
 * 因此该过滤器负责补齐 {@code Authorization: Bearer ...}，让下游服务无需感知 Cookie。</p>
 */
public final class CookieTokenToAuthorizationFilter implements GlobalFilter, Ordered {

    private final SzsSecurityProperties properties;
    private final PermitAllPathMatcher permitAllPathMatcher;

    /**
     * 创建过滤器。
     */
    public CookieTokenToAuthorizationFilter(SzsSecurityProperties properties) {
        this.properties = properties;
        this.permitAllPathMatcher = new PermitAllPathMatcher(properties.getGateway().getPermitAllPaths());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (permitAllPathMatcher.matches(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // 调用方已经显式传入 Bearer Token 时不覆盖，便于服务间调用和调试。
            return chain.filter(exchange);
        }

        HttpCookie cookie = exchange.getRequest().getCookies()
                .getFirst(properties.getAccessTokenCookieName());
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return chain.filter(exchange);
        }

        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(builder -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
                .build();
        return chain.filter(authenticatedExchange);
    }

    @Override
    public int getOrder() {
        // 在自定义 CSRF 过滤之后、路由转发之前执行。
        return -80;
    }
}
