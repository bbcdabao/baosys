package baosys.common.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * MVC 通用异常映射。业务 Advice 继承此类，仅添加业务专有异常。
 * 保留框架提供的状态与 Allow 等响应头，不向客户端回传内部异常详情。
 */
public abstract class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var servlet = (ServletWebRequest) request;
        if (servlet.getResponse() != null && servlet.getResponse().isCommitted()) return null;
        if (status.is5xxServerError()) LOG.error("HTTP request failed, status={}", status.value(), ex);
        return new ResponseEntity<>(ApiErrors.response(status.value(),
            TraceIdFilter.traceId(servlet.getRequest())), headers, status);
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(ApiErrors.response(400, TraceIds.current()));
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<?> overloaded(RejectedExecutionException ex) {
        LOG.warn("Task rejected", ex);
        return ResponseEntity.status(503).body(ApiErrors.response(503, TraceIds.current()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex, HttpServletResponse response) {
        if (response.isCommitted()) {
            LOG.error("Request failed after response was committed", ex);
            return null;
        }
        // 保留 Spring Security 的 Bearer challenge 与 insufficient_scope 语义。
        if (ex instanceof AccessDeniedException denied) throw denied;
        if (ex instanceof AuthenticationException failed) throw failed;
        LOG.error("Request failed", ex);
        return ResponseEntity.internalServerError().body(ApiErrors.response(500, TraceIds.current()));
    }
}
