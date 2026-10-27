package baosys.common.security.config;

import baosys.common.security.gateway.BrowserOAuth2Configuration;
import baosys.common.security.gateway.CsrfController;
import baosys.common.security.gateway.GatewaySecurityConfig;
import baosys.common.security.gateway.JwtTokenRelayFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 网关认证检查组合配置。
 *
 * <p>由 {@code @EnableAuthenticationCheck} 引入，负责装配 JWT 解码器、网关安全过滤链、
 * Spring CSRF 防护，以及 已认证 JWT 的 Bearer 转发过滤器。网关服务
 * 只需要启用该注解并配置公钥，就能获得统一的入口认证能力。</p>
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(SecurityProperties.class)
@Import({
    ReactiveJwtDecoderConfiguration.class,
    GatewaySecurityConfig.class,
    CsrfController.class,
    BrowserOAuth2Configuration.class
})
public class AuthenticationCheckConfiguration {

    /**
     * 将 Spring 已认证 JWT 转发为下游服务可识别的 Bearer 请求头。
     *
     * <p>该过滤器只在 Spring Cloud Gateway 环境存在时注册，避免普通 Servlet 资源服务
     * 引入无意义的 WebFlux 过滤器 Bean。</p>
     */
    @Bean
    @ConditionalOnClass(GlobalFilter.class)
    @ConditionalOnMissingBean
    JwtTokenRelayFilter jwtTokenRelayFilter() {
        return new JwtTokenRelayFilter();
    }
}
