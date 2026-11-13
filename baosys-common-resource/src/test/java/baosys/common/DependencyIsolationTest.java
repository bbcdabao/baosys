package baosys.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import static org.assertj.core.api.Assertions.assertThat;

class DependencyIsolationTest {
    @Test
    void resourceHasOnlyServletAdapters() {
        var loader = getClass().getClassLoader();
        for (String resource : new String[]{
            "org/springframework/web/reactive/DispatcherHandler.class",
            "reactor/core/publisher/Mono.class", "io/micrometer/context/ContextRegistry.class",
            "baosys/common/web/TraceWebFilter.class",
            "baosys/common/web/ReactiveApiExceptionHandler.class",
            "baosys/common/web/ReactiveApiSecurityHandler.class",
            "baosys/common/web/SpaWebConfiguration.class"
        }) {
            assertThat(loader.getResource(resource)).as(resource).isNull();
        }
        assertThat(ImportCandidates.load(AutoConfiguration.class, loader))
            .contains("baosys.common.autoconfigure.TraceAutoConfiguration",
                "baosys.common.autoconfigure.MvcAsyncAutoConfiguration")
            .doesNotContain("baosys.common.autoconfigure.ReactiveTraceAutoConfiguration",
                "baosys.common.autoconfigure.ReactiveApiAutoConfiguration",
                "baosys.common.web.SpaWebConfiguration");
    }
}
