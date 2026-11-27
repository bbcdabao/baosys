package baosys.common.security.gateway;

import baosys.common.security.config.SecurityProperties;
import baosys.common.security.resource.JwtPermissionConverter;
import baosys.common.security.resource.SharedTokenCookies;
import baosys.common.web.ReactiveApiResponses;
import baosys.common.web.ReactiveApiSecurityHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
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
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 安全配置。
 *
 * <p>网关负责保护 {@code /api/**}，优先读取标准 {@code Authorization} 请求头，
 * 浏览器通过同域共享 HttpOnly Cookie 提供令牌。公开路径由各网关服务通过配置声明。
 * 该配置只处理网关入口认证，转发给下游服务时的请求头补齐由
 * {@link JwtTokenRelayFilter} 完成。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ServerHttpSecurity.class)
public class GatewaySecurityConfig {

    private static final String API_PATH_PATTERN = "/api/**";
    private static final String ACTUATOR_PATH_PATTERN = "/actuator/**";

    /**
     * 创建网关安全过滤链。
     *
     * <p>API 按目标路由检查系统入口权限，未配置的 API 拒绝访问；未公开的 {@code /actuator/**}
     * 端点要求有效登录身份，其余静态资源、前端路由和已公开的健康检查等路径允许通过。
     * 服务可以通过 {@code spring.security.gateway.permit-all-paths} 精确声明额外公开路径。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
        ServerHttpSecurity http,
        SecurityProperties properties,
        ObjectProvider<BrowserOAuth2Configuration> browser,
        ObjectProvider<ObjectMapper> mappers
    ) {
        var gateway = properties.getGateway();
        var mapper = mappers.getIfAvailable(
            () -> Jackson2ObjectMapperBuilder.json().build());

        var errors = new ReactiveApiSecurityHandler(mapper);
        if (gateway.isCsrfEnabled() && (!StringUtils.hasText(gateway.getCsrfPath()) ||
            !gateway.getCsrfPath().matches("/api/[a-zA-Z0-9-]+/csrf") ||
            !StringUtils.hasText(gateway.getCsrfCookieName()) ||
            !gateway.getCsrfCookieName().matches("[a-zA-Z0-9_-]+"))) {
            throw new IllegalArgumentException("Each gateway requires its own csrf-path and csrf-cookie-name");
        }
        var publicPathList = normalizePermitAllPaths(gateway.getPermitAllPaths());
        if (StringUtils.hasText(gateway.getCsrfPath())) {
            publicPathList.add(gateway.getCsrfPath().trim());
        }

        String[] permitAllPaths = publicPathList.toArray(String[]::new);

        ServerWebExchangeMatcher publicPaths =
            permitAllPaths.length == 0
                ? exchange -> ServerWebExchangeMatcher.MatchResult.notMatch()
                : ServerWebExchangeMatchers.pathMatchers(permitAllPaths);

        var protectedApi = new AndServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(API_PATH_PATTERN, ACTUATOR_PATH_PATTERN),
            new NegatedServerWebExchangeMatcher(publicPaths)
        );

        var bearerConverter = new ServerBearerTokenAuthenticationConverter();
        var headerBearer = new AuthenticationConverterServerWebExchangeMatcher(bearerConverter);
        // 仅豁免显式携带 Bearer 令牌的 API 请求；浏览器注销仍受 CSRF 保护。
        var bearerApi = new AndServerWebExchangeMatcher(headerBearer, protectedApi);

        var accessRules = gateway.getAccessRules() == null ? List.<SecurityProperties.RoutePermission>of() : gateway.getAccessRules();
        for (var route : accessRules) {
            if (route == null || !StringUtils.hasText(route.getPath())) {
                throw new IllegalArgumentException("Each spring.security.gateway.access-rules entry requires a non-blank path");
            }
        }

        browser.ifAvailable(config -> config.configure(http));

        return http
            .csrf(csrf -> {
                if (!gateway.isCsrfEnabled()) {
                    csrf.disable();
                    return;
                }
                var repository = new CookieServerCsrfTokenRepository();
                repository.setCookieName(gateway.getCsrfCookieName());
                repository.setCookiePath("/");
                repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
                csrf
                    .accessDeniedHandler((exchange, ex) -> ReactiveApiResponses.write(exchange, 403, mapper))
                    .csrfTokenRepository(repository)
                    .requireCsrfProtectionMatcher(
                        new AndServerWebExchangeMatcher(
                            CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                            new NegatedServerWebExchangeMatcher(bearerApi)
                        )
                    );
            })
            .exceptionHandling(config -> config.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .authorizeExchange(authorize -> {
                if (permitAllPaths.length > 0) {
                    authorize.pathMatchers(permitAllPaths).permitAll();
                }
                for (var route : accessRules) {
                    // 按配置顺序首次匹配；共享接口只校验登录身份。
                    var path = authorize.pathMatchers(route.getPath().trim());
                    if (StringUtils.hasText(route.getPermission())) {
                        path.hasAuthority(route.getPermission().trim());
                    } else {
                        path.authenticated();
                    }
                }
                authorize
                    .pathMatchers(API_PATH_PATTERN).denyAll()
                    .pathMatchers(ACTUATOR_PATH_PATTERN).authenticated()
                    .anyExchange().permitAll();
            })
            .oauth2ResourceServer(resourceServer ->
                resourceServer
                    .authenticationEntryPoint(errors)
                    .accessDeniedHandler(errors)
                    // 显式 Authorization 请求头优先；即使无效，也不回退到 Cookie 认证。
                    .bearerTokenConverter(exchange ->
                        protectedApi.matches(exchange).flatMap(match ->
                            !match.isMatch()
                                ? Mono.empty()
                                : exchange.getRequest().getHeaders().containsKey("Authorization")
                                  ? bearerConverter.convert(exchange)
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

    private static List<String> normalizePermitAllPaths(List<String> configured) {
        if (configured == null) {
            return new ArrayList<>();
        }
        var normalized = new ArrayList<String>(configured.size());
        for (String raw : configured) {
            if (!StringUtils.hasText(raw)) {
                throw new IllegalArgumentException("spring.security.gateway.permit-all-paths entries must not be blank");
            }
            normalized.add(raw.trim());
        }
        return normalized;
    }
}
