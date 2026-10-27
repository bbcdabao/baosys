package baosys.manager.infra.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为共享 Cookie 登录端点配置无状态认证链和来源检查。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SharedLoginProperties.class)
public class SharedLoginConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain sharedLoginChain(HttpSecurity http, SharedLoginProperties properties)
        throws Exception {
        return http
            .securityMatcher("/v1/browser-auth/**")
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // CSRF 令牌由网关校验；直接访问服务的写请求还必须匹配 Manager 的公开 Origin。
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(
                new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain chain
                    ) throws ServletException, IOException {
                        if (
                            !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(
                                request.getMethod()
                            ) &&
                            !properties.managerOrigin().equals(request.getHeader("Origin"))
                        ) {
                            response.setStatus(403);
                            return;
                        }
                        chain.doFilter(request, response);
                    }
                },
                UsernamePasswordAuthenticationFilter.class
            )
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .build();
    }
}
