package baosys.manager.infra.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 为共享 Cookie 登录端点配置无状态认证链。
 * SharedLoginConfiguration
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SharedLoginProperties.class)
public class SharedLoginConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain sharedLoginChain(HttpSecurity http)
        throws Exception {
        return http
            .securityMatcher("/v1/browser-auth/**")
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Manager 部署于网关后的内网，CSRF 令牌统一由网关校验。
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .build();
    }
}
