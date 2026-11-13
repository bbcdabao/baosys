package baosys.common.web;

import baosys.common.autoconfigure.MvcAsyncAutoConfiguration;
import baosys.common.concurrent.ManagedExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MvcAsyncTraceTest {
    @Test
    void callableUsesManagedExecutorAndKeepsTraceThroughRedispatch() {
        new WebApplicationContextRunner().withUserConfiguration(Mvc.class)
            .withConfiguration(AutoConfigurations.of(MvcAsyncAutoConfiguration.class))
            .withPropertyValues("common.mvc.async-executor.core-pool-size=1", "common.mvc.async-executor.max-pool-size=1")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean("mvcAsyncExecutor", ManagedExecutor.class).getCorePoolSize()).isEqualTo(1);
                var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new TraceIdFilter()).build();
                var pending = mvc.perform(get("/async").header("X-Trace-Id", "async-trace"))
                    .andExpect(request().asyncStarted()).andReturn();
                mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andExpect(content().string("async-trace"))
                    .andExpect(header().string("X-Trace-Id", "async-trace"));
            });
    }
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(Endpoint.class)
    static class Mvc {}
    @RestController
    static class Endpoint {
        @GetMapping("/async") Callable<String> trace() { return TraceIds::current; }
    }
}
