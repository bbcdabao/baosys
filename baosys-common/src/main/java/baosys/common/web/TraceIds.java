package baosys.common.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 统一的请求追踪标识约定，供日志、API 响应和审计记录复用。
 */
public final class TraceIds {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String ATTRIBUTE = TraceIds.class.getName();

    private TraceIds() {}

    /**
     * 读取当前线程追踪 ID；缺失时生成新 ID，但不写入 MDC。
     */
    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return !StringUtils.hasText(traceId) ? create() : traceId;
    }

    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalize(String candidate) {
        return candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")
            ? candidate : create();
    }
}
