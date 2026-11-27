package baosys.common.autoconfigure;

import baosys.common.web.ReactiveApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 响应式业务 API 的默认异常出口。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(WebExceptionHandler.class)
public class ReactiveApiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ReactiveApiExceptionHandler reactiveApiExceptionHandler(ObjectProvider<ObjectMapper> mappers) {
        return new ReactiveApiExceptionHandler(mappers.getIfAvailable(
            () -> Jackson2ObjectMapperBuilder.json().build()));
    }
}
