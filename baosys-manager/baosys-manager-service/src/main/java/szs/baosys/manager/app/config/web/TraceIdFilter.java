package szs.baosys.manager.app.config.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_TRACE_ID_LENGTH) {
            return TraceIds.create();
        }
        return candidate;
    }
}
