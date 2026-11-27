package baosys.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 业务 API 的认证授权错误：保留 Bearer 响应头，并写入统一 JSON。
 */
public final class ServletApiSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;
    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler accessDeniedHandler = new BearerTokenAccessDeniedHandler();

    public ServletApiSecurityHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException ex) throws IOException, ServletException {
        if (response.isCommitted()) return;
        entryPoint.commence(request, response, ex);
        write(request, response, resolveStatus(response, HttpServletResponse.SC_UNAUTHORIZED));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException ex) throws IOException, ServletException {
        if (response.isCommitted()) return;
        accessDeniedHandler.handle(request, response, ex);
        write(request, response, resolveStatus(response, HttpServletResponse.SC_FORBIDDEN));
    }

    private static int resolveStatus(HttpServletResponse response, int fallbackStatus) {
        int status = response.getStatus();
        return status >= 400 && status < 500 ? status : fallbackStatus;
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        String traceId = TraceIdFilter.traceId(request);
        response.setStatus(status);
        response.setHeader(TraceIds.HEADER_NAME, traceId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiErrors.response(status, traceId));
    }
}
