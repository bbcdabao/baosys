package baosys.common.security.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 JWT 转发过滤器：清除外部传入的用户、租户身份头，并将当前 JWT 身份中的令牌写入下游请求。
 *
 * <p><strong>接入方式：</strong>由 {@code AuthenticationCheckConfiguration} 注册为 Bean，
 * Spring Cloud Gateway 在请求匹配网关路由后，通过全局过滤器链调用本类。
 * 本类属于网关路由过滤链，与负责认证、CSRF 和权限检查的 Spring Security 过滤链分工不同。</p>
 *
 * <p><strong>令牌来源：</strong>只从请求的 {@link JwtAuthenticationToken} 身份中取得 JWT，
 * 不重新解析原始 Authorization 请求头或 Cookie。正常情况下，该身份由前面的资源服务器认证
 * （请求头 Bearer 或共享 Cookie）建立，也可以由 {@link BrowserOAuth2Configuration}
 * 在 OAuth2 会话续期并验签后建立。因此，使用 Cookie 或 WebSession 登录的浏览器请求，
 * 转发到下游时也可以携带统一的 {@code Authorization: Bearer ...} 请求头。</p>
 *
 * <p><strong>职责边界：</strong>本类不负责登录、验签、刷新令牌或判断业务权限，
 * 也不生成用户、租户身份头。下游服务仍应按自身安全配置验证 JWT 和检查权限，
 * 不应仅因为请求经过网关就信任外部身份信息。</p>
 */
public final class JwtTokenRelayFilter implements GlobalFilter, Ordered {

    /**
     * 清理可伪造的身份请求头，并在当前身份为 JWT 时补齐或覆盖 Bearer 请求头。
     *
     * <p>身份头清理对所有进入本过滤器的请求执行，不依赖是否存在 JWT 身份。
     * 如果没有 principal，或 principal 不是 {@code JwtAuthenticationToken}，
     * 则仅传递清理后的请求；本方法不会主动返回 401，也不会删除原有 Authorization 或 Cookie。
     * 请求是否允许访问由安全过滤链及下游服务的规则决定。</p>
     *
     * @param exchange 当前请求上下文，包含前序认证流程建立的 principal
     * @param chain 后续网关过滤器链，负责继续处理请求并执行路由转发
     * @return 后续过滤链的异步完成信号；发生错误时沿响应式链路传播
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 基于原请求构造清理后的 exchange，防止客户端伪造身份头供下游误用。
        // 清理范围：X-User-*、X-Tenant-*，以及 UserId、TenantId、X-User、X-Tenant。
        var sanitized = exchange
            .mutate()
            .request(builder ->
                builder.headers(headers ->
                    headers.keySet().removeIf(name -> {
                        // HTTP 头名不区分大小写；ROOT 避免服务器语言区域影响大小写转换。
                        String lower = name.toLowerCase(java.util.Locale.ROOT);
                        return (
                            lower.startsWith("x-user-") ||
                            lower.startsWith("x-tenant-") ||
                            lower.equals("userid") ||
                            lower.equals("tenantid") ||
                            lower.equals("x-user") ||
                            lower.equals("x-tenant")
                        );
                    })
                )
            )
            .build();
        // 从安全流程建立的 principal 获取 JWT；非 JWT 身份会被 ofType 过滤为空。
        return exchange
            .getPrincipal()
            .ofType(JwtAuthenticationToken.class)
            // 使用当前身份持有的原始 JWT 字符串设置 Bearer 头。
            // setBearerAuth 覆盖已有 Authorization，确保转发的令牌与当前 JWT 身份一致。
            .map(jwt ->
                sanitized
                    .mutate()
                    .request(builder ->
                        builder.headers(headers ->
                            headers.setBearerAuth(jwt.getToken().getTokenValue())
                        )
                    )
                    .build()
            )
            // 无 JWT 时也必须继续执行清理后的请求；flatMap 订阅后续过滤链返回的 Mono。
            .defaultIfEmpty(sanitized)
            .flatMap(chain::filter);
    }

    /**
     * 指定本过滤器在网关过滤链中的顺序。
     *
     * <p>前置处理按 order 从小到大执行，{@code -80} 让身份头清理和 Bearer 头设置
     * 先于 order 更大的过滤器执行。这里的顺序用于 Gateway 过滤链，
     * 与 Spring Security 的 {@code SecurityWebFiltersOrder} 不是同一套排序。</p>
     *
     * @return 网关过滤器排序值 {@code -80}
     */
    @Override
    public int getOrder() {
        return -80;
    }
}
