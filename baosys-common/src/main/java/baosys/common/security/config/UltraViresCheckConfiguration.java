package baosys.common.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import baosys.common.security.resource.JwtPermissionConverter;
import baosys.common.security.resource.ResourceServerSecurityConfig;

/**
 * 资源服务越权检查组合配置。
 *
 * <p>由 {@code @EnableUltraViresCheck} 引入，开启方法级鉴权，并把 JWT 中的平台权限
 * 编码转换为 Spring Security 的 {@code GrantedAuthority}。该配置面向认证中心和
 * 下游业务服务，用于完成“已登录用户是否有权调用当前接口”的判断。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@Import({JwtDecoderConfiguration.class, ResourceServerSecurityConfig.class})
public class UltraViresCheckConfiguration {

    /**
     * 创建 JWT 权限转换器，供资源服务器过滤链使用。
     *
     * <p>转换器只负责从 token 中提取权限；具体权限表达式仍由业务接口上的
     * {@code @PreAuthorize} 声明。</p>
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(SecurityProperties properties) {
        return JwtPermissionConverter.create(
                properties.getPermissionsClaimName(),
                properties.getAuthorityPrefix());
    }
}
