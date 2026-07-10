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
 *
 * <p>该过滤器只保护 Cookie token 场景：浏览器会自动携带 Cookie，因此写操作需要
 * 额外的 CSRF token 证明请求来自前端应用本身；显式传入 {@code Authorization}
 * 请求头的服务间调用不走这条校验路径。</p>
 */
public final class GatewayCsrfFilter implements GlobalFilter, Ordered {

    private final SzsSecurityProperties securityProperties;
    private final GatewaySecurityErrorHandler errorHandler;
    private final PermitAllPathMatcher permitAllPathMatcher;

    /**
     * 创建网关 CSRF 过滤器。
     *
     * @param securityProperties 平台安全配置，提供 access token Cookie 和 CSRF Cookie 名称
     * @param objectMapper 用于写出统一 JSON 错误响应
     */
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
        // 先于 Cookie 转 Authorization 过滤器执行，确保仍能判断请求是否依赖 Cookie 身份。
        return -90;
    }

    /**
     * 判断当前请求是否需要 CSRF 保护。
     *
     * <p>只有受保护 API、非安全 HTTP 方法、存在 access token Cookie、且调用方没有显式
     * 使用 Bearer 请求头时才校验。这样可以兼顾浏览器 Cookie 登录和服务间 Bearer 调用。</p>
     */
    private boolean requiresProtection(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        HttpCookie accessCookie = exchange.getRequest().getCookies()
                .getFirst(securityProperties.getAccessTokenCookieName());
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
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
