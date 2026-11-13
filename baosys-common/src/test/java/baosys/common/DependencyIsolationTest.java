package baosys.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DependencyIsolationTest {
    @Test
    void baseLibraryHasNoWebOrSecurityRuntime() {
        var loader = getClass().getClassLoader();
        for (String resource : new String[]{
            "org/springframework/web/servlet/DispatcherServlet.class",
            "org/springframework/web/reactive/DispatcherHandler.class",
            "jakarta/servlet/Servlet.class", "reactor/core/publisher/Mono.class",
            "org/springframework/security/core/Authentication.class",
            "io/micrometer/context/ContextRegistry.class",
            "baosys/common/web/TraceIdFilter.class", "baosys/common/web/TraceWebFilter.class",
            "baosys/common/web/SpaWebConfiguration.class"
        }) {
            assertThat(loader.getResource(resource)).as(resource).isNull();
        }
    }
}
