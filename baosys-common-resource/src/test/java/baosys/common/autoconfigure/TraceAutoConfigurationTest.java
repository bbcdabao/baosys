package baosys.common.autoconfigure;

import baosys.common.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TraceAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

    @Test
    void isDiscoverableAndRegistersSingleServletFilter() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
            .contains(TraceAutoConfiguration.class.getName());
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(TraceIdFilter.class));
    }

    @Test
    void backsOffForApplicationFilter() {
        var custom = new TraceIdFilter();
        runner.withBean("customTraceIdFilter", TraceIdFilter.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TraceIdFilter.class);
            assertThat(context.getBean(TraceIdFilter.class)).isSameAs(custom);
        });
    }

    @Test
    void doesNotRegisterInNonServletApplication() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class))
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TraceIdFilter.class));
    }

    @Test
    void worksWithoutServletDependencies() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("jakarta.servlet", "org.springframework.web"))
            .withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class))
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("traceIdFilter"));
    }
}
