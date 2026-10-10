package baosys.common.security.gateway;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import baosys.common.api.ApiResponse;

/**
 * 网关统一安全错误响应处理器。
 *
 * <p>Spring Security WebFlux 的认证失败和权限不足默认响应格式不符合平台
 * {@link ApiResponse} 约定，因此这里统一转换为前端可直接处理的 JSON 结构。</p>
 */
public final class GatewaySecurityErrorHandler
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * 创建错误处理器。
     *
     * @param objectMapper 用于序列化平台统一错误响应
     */
    public GatewaySecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "AUTH_401_001", "登录状态缺失或已过期");
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException exception) {
        return write(exchange, HttpStatus.FORBIDDEN, "AUTH_403_001", "当前账户无权访问该资源");
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.failure(code, message, traceId));
        } catch (JsonProcessingException exception) {
            // 安全异常处理不能再向外抛序列化异常，兜底写出最小 JSON 便于前端识别错误。
            body = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
