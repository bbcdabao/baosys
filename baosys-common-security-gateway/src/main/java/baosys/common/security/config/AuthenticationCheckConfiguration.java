package baosys.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

import baosys.common.security.gateway.CookieTokenToAuthorizationFilter;
import baosys.common.security.gateway.GatewayCsrfFilter;
import baosys.common.security.gateway.GatewaySecurityConfig;

/**
 * 网关认证检查组合配置。
 *
 * <p>由 {@code @EnableAuthenticationCheck} 引入，负责装配 JWT 解码器、网关安全过滤链、
 * 可选 CSRF 校验过滤器，以及 Cookie 到 Authorization 请求头的转换过滤器。网关服务
 * 只需要启用该注解并配置公钥，就能获得统一的入口认证能力。</p>
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(SecurityProperties.class)
@Import({ReactiveJwtDecoderConfiguration.class, GatewaySecurityConfig.class})
public class AuthenticationCheckConfiguration {

    /**
     * 将 access token Cookie 转换为下游服务可识别的 Bearer 请求头。
     *
     * <p>该过滤器只在 Spring Cloud Gateway 环境存在时注册，避免普通 Servlet 资源服务
     * 引入无意义的 WebFlux 过滤器 Bean。</p>
     */
    @Bean
    @ConditionalOnClass(GlobalFilter.class)
    @ConditionalOnMissingBean
    CookieTokenToAuthorizationFilter cookieTokenToAuthorizationFilter(SecurityProperties properties) {
        return new CookieTokenToAuthorizationFilter(properties);
    }

    /**
     * 创建网关 CSRF 校验过滤器。
     *
     * <p>CSRF 默认关闭，只有部署方显式配置
     * {@code security.gateway.csrf.enabled=true} 时才启用。</p>
     */
    @Bean
    @ConditionalOnClass(GlobalFilter.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "security.gateway.csrf",
            name = "enabled",
            havingValue = "true")
    GatewayCsrfFilter gatewayCsrfFilter(SecurityProperties properties, ObjectMapper objectMapper) {
        return new GatewayCsrfFilter(properties, objectMapper);
    }
}

