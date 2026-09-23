package baosys.common.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import baosys.common.security.config.JwtDecoderConfiguration;
import baosys.common.security.config.JwtEncoderConfiguration;
import baosys.common.security.jwt.JwtTokenService;
import baosys.common.security.password.PasswordHasher;

class CommonAutoConfigurationTest {

    private static final KeyMaterial KEY_MATERIAL = createKeyMaterial();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PasswordAutoConfiguration.class,
                    JwtDecoderConfiguration.class,
                    JwtEncoderConfiguration.class));

    @Test
    void shouldKeepOptionalInfrastructureDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PasswordHasher.class);
            assertThat(context).doesNotHaveBean(JwtTokenService.class);
            assertThat(context).doesNotHaveBean(JwtDecoder.class);
            assertThat(context).doesNotHaveBean(JwtEncoder.class);
        });
    }

    @Test
    void shouldEnableConfiguredInfrastructure() {
        contextRunner
                .withPropertyValues(
                        "common.password.enabled=true",
                        "common.password.strength=4",
                        "security.jwt.public-key=" + KEY_MATERIAL.publicKey(),
                        "security.jwt.private-key=" + KEY_MATERIAL.privateKey(),
                        "security.jwt.access-token-expire-seconds=900")
                .run(context -> {
                    assertThat(context).hasSingleBean(PasswordHasher.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtTokenService.class);
                });
    }

    private static KeyMaterial createKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            return new KeyMaterial(
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record KeyMaterial(String publicKey, String privateKey) {
    }
}
