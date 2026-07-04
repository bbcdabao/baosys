package szs.baosys.common.security.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import szs.baosys.common.security.config.SzsSecurityProperties;

/**
 * 网关 CSRF 过滤器。
 *
 * <p>使用 access token Cookie 发起的非安全方法请求必须携带匹配的
 * {@code X-CSRF-Token} 请求头。公开路径由网关白名单统一放行。</p>
 */
public final class GatewayCsrfFilter implements GlobalFilter, Ordered {

    private final SzsSecurityProperties securityProperties;
    private final GatewaySecurityErrorHandler errorHandler;
    private final PermitAllPathMatcher permitAllPathMatcher;

    public GatewayCsrfFilter(
            SzsSecurityProperties securityProperties,
            ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.errorHandler = new GatewaySecurityErrorHandler(objectMapper);
        this.permitAllPathMatcher = new PermitAllPathMatcher(securityProperties.getGateway().getPermitAllPaths());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!requiresProtection(exchange)) {
            return chain.filter(exchange);
        }
        HttpCookie csrfCookie = exchange.getRequest().getCookies()
                .getFirst(securityProperties.getGateway().getCsrf().getCookieName());
        String csrfHeader = exchange.getRequest().getHeaders().getFirst("X-CSRF-Token");
        if (csrfCookie != null && csrfCookie.getValue().equals(csrfHeader)) {
            return chain.filter(exchange);
        }
        return errorHandler.write(
                exchange, HttpStatus.FORBIDDEN, "AUTH_403_002", "CSRF 校验失败");
    }

    @Override
    public int getOrder() {
        return -90;
    }

    private boolean requiresProtection(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        HttpCookie accessCookie = exchange.getRequest().getCookies()
                .getFirst(securityProperties.getAccessTokenCookieName());
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // 显式 Bearer 调用不依赖浏览器自动携带 Cookie，因此不受 Cookie CSRF 威胁。
        return path.startsWith("/api/")
                && method != null
                && method != HttpMethod.GET
                && method != HttpMethod.HEAD
                && method != HttpMethod.OPTIONS
                && accessCookie != null
                && (authorization == null || !authorization.startsWith("Bearer "))
                && !permitAllPathMatcher.matches(path);
    }
}
