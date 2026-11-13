package baosys.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import static org.assertj.core.api.Assertions.assertThat;

class DependencyIsolationTest {
    @Test
    void gatewayHasOnlyReactiveAdapters() {
        var loader = getClass().getClassLoader();
        for (String resource : new String[]{
            "org/springframework/web/servlet/DispatcherServlet.class",
            "jakarta/servlet/Servlet.class",
            "baosys/common/web/TraceIdFilter.class",
            "baosys/common/web/ApiExceptionHandler.class",
            "baosys/common/web/ServletApiSecurityHandler.class",
            "baosys/common/autoconfigure/MvcAsyncAutoConfiguration.class"
        }) {
            assertThat(loader.getResource(resource)).as(resource).isNull();
        }
        assertThat(ImportCandidates.load(AutoConfiguration.class, loader))
            .contains("baosys.common.autoconfigure.ReactiveTraceAutoConfiguration",
                "baosys.common.autoconfigure.ReactiveApiAutoConfiguration",
                "baosys.common.web.SpaWebConfiguration")
            .doesNotContain("baosys.common.autoconfigure.TraceAutoConfiguration",
                "baosys.common.autoconfigure.MvcAsyncAutoConfiguration");
    }
}
