package baosys.manager.app.config.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class TraceIds {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private TraceIds() {}

    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return !StringUtils.hasText(traceId) ? create() : traceId;
    }

    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
