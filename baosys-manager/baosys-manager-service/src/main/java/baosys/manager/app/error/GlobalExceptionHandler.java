package baosys.manager.app.error;

import baosys.common.api.ApiResponse;
import baosys.manager.app.config.web.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
        MethodArgumentNotValidException exception
    ) {
        String message = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(
            ApiResponse.failure("AUTH_400_001", message, TraceIds.current())
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
        HandlerMethodValidationException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.failure("REQUEST_400_001", "请求参数不正确", TraceIds.current())
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
        MissingServletRequestParameterException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.failure(
                "REQUEST_400_001",
                "缺少请求参数：" + exception.getParameterName(),
                TraceIds.current()
            )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
        HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.failure("AUTH_400_001", "请求内容格式不正确", TraceIds.current())
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
            ApiResponse.failure(exception.getCode(), exception.getMessage(), TraceIds.current())
        );
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(
        org.springframework.dao.DuplicateKeyException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.failure("USER_409_001", "账户名称已存在", TraceIds.current())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        // 将认证授权异常交给安全过滤链，按 Bearer 认证语义生成响应。
        if (exception instanceof AccessDeniedException denied) {
            throw denied;
        }
        if (exception instanceof org.springframework.security.core.AuthenticationException failed) {
            throw failed;
        }
        LOGGER.error("module=auth action=request result=failed errorCode=SYS_500_001", exception);
        return ResponseEntity.internalServerError().body(
            ApiResponse.failure("SYS_500_001", "系统内部错误", TraceIds.current())
        );
    }
}
