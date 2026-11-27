package baosys.common.autoconfigure;

import baosys.common.web.ApiExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Servlet 业务 API 的默认异常出口；业务侧提供 {@link ApiExceptionHandler} 子类时自动退让。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, ResponseEntityExceptionHandler.class})
public class ServletApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiExceptionHandler.class)
    ApiExceptionHandler defaultApiExceptionHandler() {
        return new DefaultApiExceptionHandler();
    }

    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class DefaultApiExceptionHandler extends ApiExceptionHandler {}
}
