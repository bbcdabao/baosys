package baosys.common.autoconfigure;

import baosys.common.web.TraceIds;
import baosys.common.web.TraceWebFilter;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Hooks;

/**
 * Reactor Context 与 MDC 的自动传播，仅在响应式 Web 应用启用。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFilter.class, Hooks.class})
public class ReactiveTraceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    TraceWebFilter traceWebFilter() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(TraceIds.MDC_KEY,
            () -> MDC.get(TraceIds.MDC_KEY), value -> MDC.put(TraceIds.MDC_KEY, value),
            () -> MDC.remove(TraceIds.MDC_KEY));
        Hooks.enableAutomaticContextPropagation();
        return new TraceWebFilter();
    }
}
