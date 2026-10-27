package baosys.common.security.gateway;

import baosys.common.security.resource.JwtPermissionConverter;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.*;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.logout.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/**
 * 浏览器 OAuth2 授权码登录及 WebSession 令牌续期配置，将访问令牌中的业务权限应用于网关请求。
 *
 * <p>仅在 {@code baosys.browser-oauth2.enabled=true} 时启用，并需要配置 Spring OAuth2 客户端注册。
 * 当前 manager-web 默认配置未启用此模式；其共享 Cookie 登录通过前端收到 401 后调用
 * {@code /api/baosys-manager/v1/browser-auth/refresh}，由 manager 的 SharedLoginService 刷新，
 * 不经过本类的客户端管理器。</p>
 *
 * <p>本类启用后的刷新链路：</p>
 * <ol>
 *   <li>OAuth2 登录成功后，Spring 将包含 access token 和 refresh token 的授权客户端保存到网关 WebSession。</li>
 *   <li>浏览器携带会话访问 API 时，本类调用本地客户端管理器，按需检查 access token 是否需要续期；
 *       这不是定时任务，也不是等待下游返回 401 后重试。</li>
 *   <li>当存在 refresh token 且访问令牌达到 Spring 的过期判定条件（含时钟偏差窗口）时，
 *       Spring 刷新提供器向客户端注册中的 tokenUri 发送 refresh_token 授权请求并执行客户端认证。
 *       访问令牌仍可用时直接复用，不会每次请求都调用授权服务器。</li>
 *   <li>当客户端 provider 指向 manager 授权服务器时，真正的刷新由 manager 微服务处理：
 *       标准 token 端点校验令牌，结合 AccountAuthorizationService 检查账户及持久化授权状态，
 *       签发新令牌并保存到数据库。manager 配置 reuse-refresh-tokens=false，因此刷新令牌也会轮换。</li>
 *   <li>刷新成功后，Spring 客户端管理器将新授权客户端保存回同一 WebSession；
 *       本类验签 access token 并转换业务权限，随后由 JwtTokenRelayFilter 向下游转发 Bearer 令牌。</li>
 * </ol>
 *
 * <p>字段 {@code manager} 是网关进程内的 Spring 对象，不是 manager 微服务的代理。
 * 远程刷新目标由 OAuth2 客户端的 tokenUri（可通过 provider 元数据发现）决定，
 * 不由网关业务路由的 MANAGER_SERVICE_URL 决定；这里既不签发令牌，也不直接操作 manager 数据库。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "baosys.browser-oauth2.enabled", havingValue = "true")
public class BrowserOAuth2Configuration {

    /**
     * OAuth2 客户端注册信息，包含客户端身份、授权地址及令牌端点。
     */
    private final ReactiveClientRegistrationRepository registrations;

    /**
     * 验证访问令牌的签名及声明，不负责刷新或签发令牌。
     */
    private final ReactiveJwtDecoder decoder;

    /**
     * 登录与刷新共享同一会话仓库；此模式下令牌由服务端 WebSession 持有。
     */
    private final ServerOAuth2AuthorizedClientRepository clients =
        new WebSessionServerOAuth2AuthorizedClientRepository();

    /**
     * 本地 OAuth2 客户端管理器：协调加载、按需刷新及保存授权客户端。
     */
    private final DefaultReactiveOAuth2AuthorizedClientManager manager;

    /**
     * 同一会话的在途授权操作；仅在当前网关实例内合并请求，不提供跨实例的分布式互斥。
     */
    private final ConcurrentHashMap<String, Mono<OAuth2AuthorizedClient>> pending =
        new ConcurrentHashMap<>();

    public BrowserOAuth2Configuration(
        ReactiveClientRegistrationRepository registrations,
        ReactiveJwtDecoder decoder
    ) {
        this.registrations = registrations;
        this.decoder = decoder;
        manager = new DefaultReactiveOAuth2AuthorizedClientManager(registrations, clients);
        // 使用 Spring 标准授权码/刷新提供器，刷新请求及成功后的仓库更新由框架完成。
        manager.setAuthorizedClientProvider(
            ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build()
        );
    }

    @Bean
    ServerOAuth2AuthorizedClientRepository browserAuthorizedClients() {
        return clients;
    }

    /**
     * 浏览器 OAuth2 会话模式的配置接入入口：将登录、按需续期和注销处理接入网关安全过滤链。
     *
     * <p><strong>调用方与调用时机：</strong>Spring 创建网关的 {@code SecurityWebFilterChain} Bean 时，
     * {@link GatewaySecurityConfig} 的 {@code gatewaySecurityWebFilterChain(...)} 方法通过
     * {@code browser.ifAvailable(config -> config.configure(http))} 显式调用本方法。
     * 本类仅在 {@code baosys.browser-oauth2.enabled=true} 时注册为 Bean；未启用时跳过调用。
     * 本方法没有访问修饰符，属于包级可见，供同包的网关配置类使用；它不是 Spring 按名称自动调用的回调。</p>
     *
     * <p><strong>配置阶段与请求阶段：</strong>本方法在构建过滤链时执行，负责注册处理逻辑，
     * 不会在此处发起登录、刷新或撤销令牌请求。浏览器后续请求到达时，才由相应过滤器执行这些逻辑。
     * 本方法只配置传入的 {@code http}，由调用方继续配置 CSRF、路径权限和资源服务器，最后调用
     * {@code http.build()} 构建完整过滤链。</p>
     *
     * <ol>
     *   <li><strong>登录：</strong>根据客户端注册信息解析 OAuth2 授权请求，启用授权码登录，
     *       并使用共享的 {@code clients} 仓库将授权客户端及令牌保存在网关 WebSession 中。</li>
     *   <li><strong>未认证响应：</strong>将认证入口点设为 HTTP 401，供前端处理需要登录的情况。</li>
     *   <li><strong>API 访问：</strong>在权限判断前执行 {@link #authorizeBrowser}。
     *       该处理器仅对没有显式 {@code Authorization} 头且已有 OAuth2 登录身份的 API 请求，
     *       按需取得或刷新访问令牌，验签并建立携带业务权限的 JWT 身份，供后续权限检查使用。
     *       共享 Cookie 的 JWT 身份不走这条 WebSession 续期流程。</li>
     *   <li><strong>注销：</strong>依次执行远程令牌撤销、安全上下文清理和 WebSession 销毁；
     *       成功后返回 HTTP 204。撤销失败会传播错误，不会作为注销成功处理。</li>
     * </ol>
     *
     * @param http 调用方创建并共享的网关安全配置构建器，本方法在其上追加浏览器 OAuth2 配置
     */
    void configure(ServerHttpSecurity http) {
        // 使用客户端注册信息解析浏览器发起的 OAuth2 授权请求。
        var resolver = new DefaultServerOAuth2AuthorizationRequestResolver(registrations);
        // 登录与后续续期共用同一个 WebSession 授权客户端仓库。
        http.oauth2Login(login ->
            login.authorizationRequestResolver(resolver).authorizedClientRepository(clients)
        )
            // 1.登录：需要认证时返回 401，由前端决定如何引导用户登录。
            .exceptionHandling(errors ->
                errors.authenticationEntryPoint(
                    new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)
                )
            )
            // 2.校验：此处仅注册回调；请求到达后，在 AUTHORIZATION 权限判断之前准备 JWT 身份。
            .addFilterBefore(this::authorizeBrowser, SecurityWebFiltersOrder.AUTHORIZATION)
            // 3.登出：先撤销远程令牌，再清理本地认证状态和会话；全部成功后返回 204。
            .logout(logout ->
                logout
                    .logoutHandler(
                        new DelegatingServerLogoutHandler(
                            this::revoke,
                            new SecurityContextServerLogoutHandler(),
                            new WebSessionServerLogoutHandler()
                        )
                    )
                    .logoutSuccessHandler(
                        new HttpStatusReturningServerLogoutSuccessHandler(HttpStatus.NO_CONTENT)
                    )
            );
    }

    /**
     * 在权限判断前取得可用访问令牌，并为当前请求建立携带业务权限的 JWT 身份。
     * @param exchange 当前请求及浏览器会话上下文
     * @param chain 后续安全过滤链
     * @return 后续处理的完成信号，认证失败时返回 401
     */
    private Mono<Void> authorizeBrowser(ServerWebExchange exchange, WebFilterChain chain) {
        // 仅处理无显式 Authorization 头的 API 请求；显式令牌请求交由资源服务器认证。
        if (
            !exchange.getRequest().getPath().value().startsWith("/api/") ||
            exchange.getRequest().getHeaders().containsKey("Authorization")
        ) {
            return chain.filter(exchange);
        }
        // 必须已有 OAuth2 登录身份；共享 Cookie 的 JWT 身份不走此 WebSession 刷新流程。
        return exchange
            .getPrincipal()
            .ofType(OAuth2AuthenticationToken.class)
            .flatMap(principal ->
                exchange.getSession().flatMap(session -> {
                    // 轮换后旧 refresh token 不再可用。同一会话的并发请求共享一次在途授权结果，
                    // 避免重复提交旧令牌；defer 延迟执行，cache 共享结果，结束时移除在途记录。
                    var authorized = pending.computeIfAbsent(session.getId(), id ->
                        Mono.defer(() ->
                            manager.authorize(
                                OAuth2AuthorizeRequest.withClientRegistrationId(
                                    principal.getAuthorizedClientRegistrationId()
                                )
                                    .principal(principal)
                                    .attribute(ServerWebExchange.class.getName(), exchange)
                                    .build()
                            )
                        )
                            .doFinally(signal -> pending.remove(id))
                            .cache()
                    );
                    // 无可用授权客户端时按授权失效处理；取得令牌后仍需验签并提取业务权限。
                    return authorized
                        .switchIfEmpty(
                            Mono.error(
                                new OAuth2AuthorizationException(new OAuth2Error("invalid_grant"))
                            )
                        )
                        .flatMap(client -> decoder.decode(client.getAccessToken().getTokenValue()))
                        .map(jwt -> JwtPermissionConverter.create().convert(jwt))
                        // OAuth2 授权异常或 JWT 验证失败：销毁本地会话，并在下方返回 401。
                        // 这里只捕获这两类异常，其他异常（例如部分网络故障）继续向外传播。
                        .onErrorResume(OAuth2AuthorizationException.class, error ->
                            session.invalidate().then(Mono.empty())
                        )
                        .onErrorResume(JwtException.class, error ->
                            session.invalidate().then(Mono.empty())
                        )
                        // 同时替换请求 principal 和 Reactor 安全上下文，供权限检查及令牌转发使用。
                        .map(authentication ->
                            chain
                                .filter(
                                    exchange.mutate().principal(Mono.just(authentication)).build()
                                )
                                .contextWrite(
                                    ReactiveSecurityContextHolder.withAuthentication(authentication)
                                )
                        )
                        .defaultIfEmpty(
                            Mono.defer(() -> {
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                return exchange.getResponse().setComplete();
                            })
                        );
                })
            )
            // 没有 OAuth2 登录身份时交给后续安全过滤链决定是否允许访问。
            .defaultIfEmpty(Mono.defer(() -> chain.filter(exchange)))
            .flatMap(result -> result);
    }

    /**
     * 注销时优先撤销刷新令牌；成功后由组合注销处理器继续清理安全上下文和 WebSession。
     * @param webExchange 当前注销请求及过滤链上下文
     * @param authentication 当前认证身份，仅处理 OAuth2 浏览器登录
     * @return 远程撤销的完成信号；非 OAuth2 身份直接完成
     */
    private Mono<Void> revoke(
        org.springframework.security.web.server.WebFilterExchange webExchange,
        org.springframework.security.core.Authentication authentication
    ) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            return Mono.empty();
        }
        return clients
            .<OAuth2AuthorizedClient>loadAuthorizedClient(
                oauth.getAuthorizedClientRegistrationId(),
                oauth,
                webExchange.getExchange()
            )
            .flatMap(client -> {
                // 从授权服务器元数据读取撤销地址；缺失或调用失败时传播错误，不伪装成撤销成功。
                String endpoint = (String) client
                    .getClientRegistration()
                    .getProviderDetails()
                    .getConfigurationMetadata()
                    .get("revocation_endpoint");
                if (endpoint == null) {
                    return Mono.error(
                        new IllegalStateException("Provider must advertise revocation_endpoint")
                    );
                }
                var token =
                    client.getRefreshToken() == null
                        ? client.getAccessToken().getTokenValue()
                        : client.getRefreshToken().getTokenValue();
                return WebClient.create()
                    .post()
                    .uri(endpoint)
                    .headers(headers ->
                        headers.setBasicAuth(
                            client.getClientRegistration().getClientId(),
                            client.getClientRegistration().getClientSecret()
                        )
                    )
                    .body(
                        BodyInserters.fromFormData("token", token).with(
                            "token_type_hint",
                            client.getRefreshToken() == null ? "access_token" : "refresh_token"
                        )
                    )
                    .retrieve()
                    .toBodilessEntity()
                    .then();
            });
    }
}
