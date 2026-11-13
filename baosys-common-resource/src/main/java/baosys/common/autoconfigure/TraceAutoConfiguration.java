package baosys.common.autoconfigure;

import baosys.common.web.TraceIdFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 请求追踪自动配置，不在响应式或非 Web 应用中注册过滤器。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class})
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
