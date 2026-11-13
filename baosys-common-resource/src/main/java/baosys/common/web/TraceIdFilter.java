package baosys.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 请求追踪：将有效的外部追踪 ID 或新生成的 ID 写入 MDC 和响应头。
 * 先于安全链运行，复用请求属性覆盖异步和错误分派，结束后恢复原 MDC。
 */
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override
    protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = traceId(request);
        String previous = MDC.get(TraceIds.MDC_KEY);
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove(TraceIds.MDC_KEY);
            else MDC.put(TraceIds.MDC_KEY, previous);
        }
    }

    public static String traceId(HttpServletRequest request) {
        String existing = (String) request.getAttribute(TraceIds.ATTRIBUTE);
        if (existing != null) return existing;
        String traceId = TraceIds.normalize(request.getHeader(TraceIds.HEADER_NAME));
        request.setAttribute(TraceIds.ATTRIBUTE, traceId);
        return traceId;
    }
}
