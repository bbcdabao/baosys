package baosys.common.security.resource;

import baosys.common.security.config.SecurityProperties;
import baosys.common.web.ServletApiSecurityHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.CollectionUtils;
import org.springframework.core.annotation.Order;

/**
 * 资源服务负责 JWT 认证，具体 API 权限由方法级鉴权校验；子系统入口权限由网关校验。
 */
public class ResourceServerSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(name = "resourceServerSecurityFilterChain")
    @Order(3)
    SecurityFilterChain resourceServerSecurityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter converter,
        SecurityProperties properties,
        ObjectProvider<ObjectMapper> mappers
    ) throws Exception {
        var resource = properties.getResource();
        var errors = new ServletApiSecurityHandler(mappers.getIfAvailable(
            () -> org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build()));
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> {
                if (!CollectionUtils.isEmpty(resource.getPermitAllPaths())) {
                    authorize
                        .requestMatchers(resource.getPermitAllPaths().toArray(String[]::new))
                        .permitAll();
                }
                authorize.requestMatchers("/actuator/**").hasAuthority("MGR");
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
