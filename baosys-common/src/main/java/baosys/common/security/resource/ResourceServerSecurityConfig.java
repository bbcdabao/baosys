package baosys.common.security.resource;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import baosys.common.api.ApiResponse;
import baosys.common.security.config.SecurityProperties;

/**
 * Servlet 资源服务安全配置。
 *
 * <p>认证中心和后续业务服务使用该配置保护 API。默认所有请求都必须携带有效 JWT；
 * 需要公开的路径由各服务通过配置声明。具体按钮级或接口级权限由方法上的
 * {@code @PreAuthorize} 继续判断。</p>
 *
 * <p>该配置保持无状态：服务端不创建 HTTP Session，每个请求都通过 Bearer token
 * 独立完成身份解析，便于网关、认证中心和业务服务横向扩展。</p>
 */
public class ResourceServerSecurityConfig {

    /**
     * 创建无状态资源服务器过滤链。
     *
     * <p>认证失败和权限不足都会写出平台统一的 {@code ApiResponse} 结构，前端无需区分
     * 错误来自网关还是下游资源服务。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProperties properties,
            ObjectMapper objectMapper) throws Exception {
        String[] permitAllPaths = properties.getResource().getPermitAllPaths().toArray(String[]::new);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    if (permitAllPaths.length > 0) {
                        authorize.requestMatchers(permitAllPaths).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> write(
                                objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                                "AUTH_401_001", "登录状态缺失或已过期"))
                        .accessDeniedHandler((request, response, exception) -> write(
                                objectMapper, response, HttpServletResponse.SC_FORBIDDEN,
                                "AUTH_403_001", "当前账户无权访问该资源")))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    /**
     * 写出统一 JSON 错误响应。
     *
     * <p>Servlet 安全异常发生时请求通常还没有进入控制器，因此这里直接写响应体，
     * 保持与业务异常处理器一致的错误格式。</p>
     */
    private static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message, traceId));
    }
}
