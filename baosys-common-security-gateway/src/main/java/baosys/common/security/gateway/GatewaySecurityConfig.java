package baosys.common.security.gateway;

import baosys.common.security.config.SecurityProperties;
import baosys.common.security.resource.JwtPermissionConverter;
import baosys.common.security.resource.SharedTokenCookies;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationConverterServerWebExchangeMatcher;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.StringUtils;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 安全配置。
 *
 * <p>网关负责保护 {@code /api/**}，优先读取标准 {@code Authorization} 请求头，
 * 浏览器通过同域共享 HttpOnly Cookie 提供令牌。公开路径由各网关服务通过配置声明。
 * 该配置只处理网关入口认证，转发给下游服务时的请求头补齐由
 * {@link JwtTokenRelayFilter} 完成。</p>
 */
@ConditionalOnClass(ServerHttpSecurity.class)
public class GatewaySecurityConfig {

    private static final String API_PATH_PATTERN = "/api/**";

    /**
     * 创建网关安全过滤链。
     *
     * <p>API 按目标路由检查系统入口权限，未配置的 API 拒绝访问，其余静态资源、前端路由和健康检查等路径
     * 允许通过。服务可以通过 {@code spring.security.gateway.permit-all-paths} 精确声明
     * 额外公开 API。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
        ServerHttpSecurity http,
        SecurityProperties properties,
        ObjectProvider<BrowserOAuth2Configuration> browser
    ) {
        String[] permitAllPaths = properties
            .getGateway()
            .getPermitAllPaths()
            .toArray(String[]::new);
        ServerWebExchangeMatcher publicPaths =
            permitAllPaths.length == 0
                ? exchange -> ServerWebExchangeMatcher.MatchResult.notMatch()
                : ServerWebExchangeMatchers.pathMatchers(permitAllPaths);
        var protectedApi = new AndServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(API_PATH_PATTERN),
            new NegatedServerWebExchangeMatcher(publicPaths)
        );
        var headerBearer = new AuthenticationConverterServerWebExchangeMatcher(
            new ServerBearerTokenAuthenticationConverter()
        );
        // 仅豁免显式携带 Bearer 令牌的 API 请求；浏览器注销仍受 CSRF 保护。
        var bearerApi = new AndServerWebExchangeMatcher(headerBearer, protectedApi);
        browser.ifAvailable(config -> config.configure(http));
        return http
            .csrf(csrf ->
                csrf
                    .csrfTokenRepository(new CookieServerCsrfTokenRepository())
                    .requireCsrfProtectionMatcher(
                        new AndServerWebExchangeMatcher(
                            CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                            new NegatedServerWebExchangeMatcher(bearerApi)
                        )
                    )
            )
            .authorizeExchange(authorize -> {
                if (permitAllPaths.length > 0) {
                    authorize.pathMatchers(permitAllPaths).permitAll();
                }
                for (var route : properties.getGateway().getAccessRules()) {
                    // 按配置顺序首次匹配；共享接口只校验登录身份。
                    var path = authorize.pathMatchers(route.getPath());
                    if (StringUtils.hasText(route.getPermission())) {
                        path.hasAuthority(route.getPermission());
                    } else {
                        path.authenticated();
                    }
                }
                authorize.pathMatchers(API_PATH_PATTERN).denyAll().anyExchange().permitAll();
            })
            .oauth2ResourceServer(resourceServer ->
                resourceServer
                    // 显式 Authorization 请求头优先；即使无效，也不回退到 Cookie 认证。
                    .bearerTokenConverter(exchange ->
                        protectedApi.matches(exchange).flatMap(match ->
                            !match.isMatch()
                                ? Mono.empty()
                                : exchange.getRequest().getHeaders().containsKey("Authorization")
                                  ? new ServerBearerTokenAuthenticationConverter().convert(exchange)
                                  : Mono.justOrEmpty(
                                        exchange
                                            .getRequest()
                                            .getCookies()
                                            .getFirst(
                                                SharedTokenCookies.ACCESS
                                            )
                                    )
                                        .filter(cookie -> !cookie.getValue().isBlank())
                                        .map(cookie ->
                                            new BearerTokenAuthenticationToken(
                                                cookie.getValue()
                                            )
                                        )
                        )
                    )
                    .jwt(jwt ->
                        jwt.jwtAuthenticationConverter(
                            new ReactiveJwtAuthenticationConverterAdapter(
                                JwtPermissionConverter.create()
                            )
                        )
                    )
            )
            .build();
    }
}
