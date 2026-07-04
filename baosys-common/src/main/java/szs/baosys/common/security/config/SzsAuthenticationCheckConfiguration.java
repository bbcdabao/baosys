package szs.baosys.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import szs.baosys.common.security.gateway.CookieTokenToAuthorizationFilter;
import szs.baosys.common.security.gateway.GatewayCsrfFilter;
import szs.baosys.common.security.gateway.GatewaySecurityConfig;

/**
 * 网关认证检查组合配置。
 *
 * <p>由 {@code @SzsEnableAuthenticationCheck} 引入，负责装配 JWT 解码器、网关安全过滤链、
 * 可选 CSRF 校验过滤器，以及 Cookie 到 Authorization 请求头的转换过滤器。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SzsSecurityProperties.class)
@Import({SzsJwtDecoderConfiguration.class, GatewaySecurityConfig.class})
public class SzsAuthenticationCheckConfiguration {

    /**
     * 将 access token Cookie 转换为下游服务可识别的 Bearer 请求头。
     */
    @Bean
    @ConditionalOnClass(GlobalFilter.class)
    @ConditionalOnMissingBean
    CookieTokenToAuthorizationFilter cookieTokenToAuthorizationFilter(SzsSecurityProperties properties) {
        return new CookieTokenToAuthorizationFilter(properties);
    }

    /**
     * 创建网关 CSRF 校验过滤器。
     */
    @Bean
    @ConditionalOnClass(GlobalFilter.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "szs.security.gateway.csrf",
            name = "enabled",
            havingValue = "true")
    GatewayCsrfFilter gatewayCsrfFilter(SzsSecurityProperties properties, ObjectMapper objectMapper) {
        return new GatewayCsrfFilter(properties, objectMapper);
    }
}
