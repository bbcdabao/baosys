package szs.baosys.common.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import szs.baosys.common.security.resource.JwtPermissionConverter;
import szs.baosys.common.security.resource.ResourceServerSecurityConfig;

/**
 * 资源服务越权检查组合配置。
 *
 * <p>由 {@code @SzsEnableUltraViresCheck} 引入，开启方法级鉴权，并把 JWT 中的平台权限
 * 编码转换为 Spring Security 的 {@code GrantedAuthority}。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SzsSecurityProperties.class)
@Import({SzsJwtDecoderConfiguration.class, ResourceServerSecurityConfig.class})
public class SzsUltraViresCheckConfiguration {

    /**
     * 创建 JWT 权限转换器，供资源服务器过滤链使用。
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(SzsSecurityProperties properties) {
        return JwtPermissionConverter.create(
                properties.getPermissionsClaimName(),
                properties.getAuthorityPrefix());
    }
}
