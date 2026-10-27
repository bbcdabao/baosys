package baosys.common.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import baosys.common.security.password.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PasswordAutoConfigurationTest {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(PasswordAutoConfiguration.class)
        );

    @Test
    void disabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PasswordHasher.class));
    }

    @Test
    void explicitlyEnabled() {
        runner
            .withPropertyValues("common.password.enabled=true", "common.password.strength=4")
            .run(context -> assertThat(context).hasSingleBean(PasswordHasher.class));
    }
}
