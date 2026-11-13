package baosys.common.web;

import baosys.common.api.ApiResponse;

/**
 * 通用 HTTP 错误协议；业务专有错误码由业务模块维护。
 */
public final class ApiErrors {
    private ApiErrors() {}

    public static ApiResponse<Void> response(int status, String traceId) {
        return switch (status) {
            case 400 -> ApiResponse.failure("VALIDATION_ERROR", "请求参数不合法", traceId);
            case 401 -> ApiResponse.failure("UNAUTHORIZED", "请登录或重新登录", traceId);
            case 403 -> ApiResponse.failure("FORBIDDEN", "无权执行该操作", traceId);
            case 404 -> ApiResponse.failure("NOT_FOUND", "请求资源不存在", traceId);
            case 405 -> ApiResponse.failure("METHOD_NOT_ALLOWED", "请求方法不支持", traceId);
            case 409 -> ApiResponse.failure("CONFLICT", "请求资源已存在", traceId);
            case 415 -> ApiResponse.failure("UNSUPPORTED_MEDIA_TYPE", "请求内容类型不支持", traceId);
            case 429 -> ApiResponse.failure("TOO_MANY_REQUESTS", "请求过于频繁", traceId);
            case 503 -> ApiResponse.failure("SERVICE_UNAVAILABLE", "服务暂不可用", traceId);
            default -> status >= 500
                ? ApiResponse.failure("INTERNAL_ERROR", "服务器内部错误", traceId)
                : ApiResponse.failure("REQUEST_ERROR", "请求失败", traceId);
        };
    }
}
