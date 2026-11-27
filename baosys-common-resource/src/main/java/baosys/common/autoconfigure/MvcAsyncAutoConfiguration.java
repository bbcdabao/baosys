package baosys.common.autoconfigure;

import baosys.common.concurrent.ExecutorProperties;
import baosys.common.concurrent.ManagedExecutor;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC Callable 使用有界执行器，继承请求 MDC；异步超时仍使用 Spring MVC 配置。
 * 在 {@link TaskExecutionAutoConfiguration} 之后装配，避免覆盖应用默认 {@code applicationTaskExecutor}。
 */
@AutoConfiguration(after = TaskExecutionAutoConfiguration.class)
@EnableConfigurationProperties
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class MvcAsyncAutoConfiguration {
    @Bean
    @ConfigurationProperties("common.mvc.async-executor")
    ExecutorProperties mvcAsyncExecutorProperties() {
        var properties = new ExecutorProperties();
        properties.setCorePoolSize(8);
        properties.setMaxPoolSize(32);
        properties.setQueueCapacity(256);
        properties.setThreadNamePrefix("mvc-async-");
        return properties;
    }

    @Bean
    @ConditionalOnMissingBean(name = "mvcAsyncExecutor")
    ManagedExecutor mvcAsyncExecutor(@Qualifier("mvcAsyncExecutorProperties") ExecutorProperties properties) {
        return new ManagedExecutor(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "tracingAsyncConfigurer")
    WebMvcConfigurer tracingAsyncConfigurer(@Qualifier("mvcAsyncExecutor") Executor executor) {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(new TaskExecutorAdapter(executor));
            }
        };
    }
}
