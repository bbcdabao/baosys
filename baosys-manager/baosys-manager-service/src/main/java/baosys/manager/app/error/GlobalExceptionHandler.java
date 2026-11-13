package baosys.manager.app.error;

import baosys.common.api.ApiResponse;
import baosys.common.web.ApiExceptionHandler;
import baosys.common.web.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manager 专有业务错误；通用 HTTP 错误由公共基类处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
            ApiResponse.failure(exception.getCode(), exception.getMessage(), TraceIds.current()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(org.springframework.dao.DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.failure("USER_409_001", "账户名称已存在", TraceIds.current()));
    }
}