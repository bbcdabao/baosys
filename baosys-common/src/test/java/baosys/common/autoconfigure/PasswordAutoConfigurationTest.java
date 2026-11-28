package baosys.common.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordAutoConfigurationTest {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(PasswordAutoConfiguration.class)
        );

    private final ApplicationContextRunner enabled = runner.withPropertyValues(
        "common.password.enabled=true",
        "common.password.strength=4"
    );

    @Test
    void disabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PasswordEncoder.class));
    }

    @Test
    void explicitlyEnabledEncodesWithPrefixedBcrypt() {
        enabled.run(context -> {
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            var encoder = context.getBean(PasswordEncoder.class);
            String encoded = encoder.encode("secret");
            assertThat(encoded).startsWith("{bcrypt}$2a$04$");
            assertThat(encoder.matches("secret", encoded)).isTrue();
            assertThat(encoder.matches("wrong", encoded)).isFalse();
        });
    }

    @Test
    void matchesLegacyUnprefixedBcryptAndStandardPrefixes() {
        enabled.run(context -> {
            var encoder = context.getBean(PasswordEncoder.class);
            String legacy = new BCryptPasswordEncoder(4).encode("secret");
            assertThat(encoder.matches("secret", legacy)).isTrue();
            assertThat(encoder.matches("wrong", legacy)).isFalse();
            assertThat(encoder.matches("test-secret", "{noop}test-secret")).isTrue();
            assertThat(encoder.matches("other", "{noop}test-secret")).isFalse();
        });
    }

    @Test
    void rejectsStrengthOutsideBcryptRange() {
        for (String strength : new String[] {"3", "32"}) {
            runner
                .withPropertyValues("common.password.enabled=true", "common.password.strength=" + strength)
                .run(context -> assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(IllegalArgumentException.class));
        }
    }

    @Test
    void backsOffWhenApplicationProvidesEncoder() {
        enabled
            .withBean(PasswordEncoder.class, () -> new BCryptPasswordEncoder(4))
            .run(context -> {
                assertThat(context).hasSingleBean(PasswordEncoder.class);
                assertThat(context.getBean(PasswordEncoder.class)).isInstanceOf(BCryptPasswordEncoder.class);
            });
    }
}
