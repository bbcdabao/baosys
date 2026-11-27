package baosys.common.security.resource;

import baosys.common.security.config.SecurityProperties;
import baosys.common.web.ServletApiSecurityHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 资源服务负责 JWT 认证，具体 API 权限由方法级鉴权校验；子系统入口权限由网关校验。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HttpSecurity.class)
public class ResourceServerSecurityConfig {

    /**
     * 默认资源服务过滤链顺序，确保带 {@code securityMatcher} 的业务过滤链优先匹配。
     */
    public static final int DEFAULT_FILTER_CHAIN_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    @Bean
    @ConditionalOnMissingBean(name = "resourceServerSecurityFilterChain")
    @Order(DEFAULT_FILTER_CHAIN_ORDER)
    SecurityFilterChain resourceServerSecurityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter converter,
        SecurityProperties properties,
        ObjectProvider<ObjectMapper> mappers
    ) throws Exception {
        var resource = properties.getResource();
        var permitAllPaths = resource == null
            ? List.<String>of()
            : resource.normalizedPermitAllPaths("spring.security.resource.permit-all-paths");
        var errors = new ServletApiSecurityHandler(
            mappers.getIfAvailable(() -> Jackson2ObjectMapperBuilder.json().build())
        );
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> {
                if (!permitAllPaths.isEmpty()) {
                    authorize
                        .requestMatchers(permitAllPaths.toArray(String[]::new))
                        .permitAll();
                }
                authorize.anyRequest().authenticated();
            })
            .exceptionHandling(config -> config.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(server ->
                server.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
            )
            .build();
    }
}
