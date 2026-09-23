package baosys.common.security.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpCookie;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import reactor.core.publisher.Mono;

import baosys.common.security.config.SecurityProperties;
import baosys.common.security.resource.JwtPermissionConverter;

/**
 * Spring Cloud Gateway 安全配置。
 *
 * <p>网关负责保护 {@code /api/**}，优先读取标准 {@code Authorization} 请求头，
 * 没有请求头时再读取平台 access token Cookie。公开路径由各网关服务通过配置声明。
 * 该配置只处理网关入口认证，转发给下游服务时的请求头补齐由
 * {@link CookieTokenToAuthorizationFilter} 完成。</p>
 */
@ConditionalOnClass(ServerHttpSecurity.class)
public class GatewaySecurityConfig {

    /**
     * 创建网关安全过滤链。
     *
     * <p>默认只要求 {@code /api/**} 认证，其余静态资源、前端路由和健康检查等路径
     * 允许通过。服务可以通过 {@code security.gateway.permit-all-paths} 精确声明
     * 额外公开 API。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            SecurityProperties properties,
            ObjectMapper objectMapper) {
        GatewaySecurityErrorHandler errorHandler = new GatewaySecurityErrorHandler(objectMapper);
        String[] permitAllPaths = properties.getGateway().getPermitAllPaths().toArray(String[]::new);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> {
                    if (permitAllPaths.length > 0) {
                        authorize.pathMatchers(permitAllPaths).permitAll();
                    }
                    authorize.pathMatchers("/api/**")
                            .authenticated()
                            .anyExchange()
                            .permitAll();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        // 统一支持请求头和 Cookie 两种 token 来源，便于浏览器端使用 HttpOnly Cookie。
                        .bearerTokenConverter(cookieBearerTokenConverter(properties))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(properties))))
                .build();
    }

    /**
     * 将 JWT 中的权限编码转换为响应式认证对象。
     *
     * <p>转换逻辑与 Servlet 资源服务共用 {@link JwtPermissionConverter}，保证网关和
     * 下游服务看到的权限集合一致。</p>
     */
    private static Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter(
            SecurityProperties properties) {
        return new ReactiveJwtAuthenticationConverterAdapter(JwtPermissionConverter.create(
                properties.getPermissionsClaimName(),
                properties.getAuthorityPrefix()));
    }

    /**
     * 网关 token 提取规则：公开路径不提取，其他路径优先请求头，其次 access token Cookie。
     *
     * <p>公开路径直接返回空认证结果，避免无意义地解析 Cookie，也避免登录、健康检查等
     * 接口因携带过期 Cookie 而被误判为认证失败。</p>
     */
    private static ServerAuthenticationConverter cookieBearerTokenConverter(SecurityProperties properties) {
        ServerBearerTokenAuthenticationConverter headerConverter = new ServerBearerTokenAuthenticationConverter();
        PermitAllPathMatcher permitAllPathMatcher = new PermitAllPathMatcher(
                properties.getGateway().getPermitAllPaths());
        return exchange -> {
            String path = exchange.getRequest().getURI().getPath();
            if (!isProtectedApiPath(path) || permitAllPathMatcher.matches(path)) {
                return Mono.empty();
            }
            return headerConverter.convert(exchange)
                    .switchIfEmpty(Mono.defer(() -> {
                        // 浏览器请求通常依赖 HttpOnly Cookie；没有 Authorization 时再从 Cookie 兜底读取。
                        HttpCookie cookie = exchange.getRequest().getCookies()
                                .getFirst(properties.getAccessTokenCookieName());
                        if (cookie == null || cookie.getValue().isBlank()) {
                            return Mono.empty();
                        }
                        return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
                    }));
        };
    }

    private static boolean isProtectedApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

}
