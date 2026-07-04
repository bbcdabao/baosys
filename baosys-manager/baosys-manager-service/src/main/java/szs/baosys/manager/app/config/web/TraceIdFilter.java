package szs.baosys.manager.app.config.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 统一接收或生成链路标识，并同步到响应头和日志 MDC。
 *
 * <p>请求结束后必须清理 MDC，因为 Servlet 工作线程会被复用；否则后续请求
 * 可能错误继承前一个请求的链路标识。</p>
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final int MAX_TRACE_ID_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = normalize(request.getHeader(TraceIds.HEADER_NAME));
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    private String normalize(String candidate) {
        // 限制透传长度，避免不可信请求头污染日志或造成异常大的日志字段。
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_TRACE_ID_LENGTH) {
            return TraceIds.create();
        }
        return candidate;
    }
}
