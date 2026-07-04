package szs.baosys.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 平台统一的 API 响应结构。
 *
 * <p>公共安全模块在认证失败、权限不足等场景也复用该结构，保证网关、认证中心
 * 和后续业务服务返回给前端的字段一致。</p>
 *
 * @param code 业务响应码，成功固定为 {@code SUCCESS}
 * @param message 面向前端展示的响应消息
 * @param data 业务数据；失败时通常为空
 * @param traceId 请求追踪标识，便于排查日志，后续扩展对接SkyWalking
 * @param timestamp UTC 时间戳
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp) {

    public static ApiResponse<Void> success(String traceId) {
        return success(null, traceId);
    }

    /**
     * 创建成功响应。
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(
                "SUCCESS",
                "操作成功",
                data,
                traceId,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 创建失败响应。
     */
    public static <T> ApiResponse<T> failure(String code, String message, String traceId) {
        return new ApiResponse<>(
                code,
                message,
                null,
                traceId,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
