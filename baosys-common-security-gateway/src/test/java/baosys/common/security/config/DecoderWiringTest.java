package baosys.common.security.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import reactor.core.publisher.Mono;

class DecoderWiringTest {

    private final ReactiveWebApplicationContextRunner runner =
        new ReactiveWebApplicationContextRunner().withBean(
            org.springframework.security.web.server.SecurityWebFilterChain.class,
            () ->
                org.mockito.Mockito.mock(
                    org.springframework.security.web.server.SecurityWebFilterChain.class
                )
        );

    @Test
    void dependencyAloneDoesNotCreateDecoder() {
        runner.run(context ->
            assertThat(context)
                .doesNotHaveBean(ReactiveJwtDecoder.class)
                .doesNotHaveBean(JwtDecoder.class)
        );
    }

    @Test
    void enabledWithoutSourceFailsAtStartup() {
        runner.withUserConfiguration(Enabled.class).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void userDecoderBacksOffWithoutRequiringKeyProperties() {
        runner.withUserConfiguration(Enabled.class, Custom.class).run(context -> {
            assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ReactiveJwtDecoder.class)
                .doesNotHaveBean(JwtDecoder.class);
            assertThat(context.getBeanNamesForType(ReactiveJwtDecoder.class)).containsExactly(
                "customDecoder"
            );
        });
    }

    @Test
    void remoteModeDoesNotFetchAtStartup() {
        runner
            .withUserConfiguration(Enabled.class)
            .withPropertyValues(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=auth-server",
                "spring.security.oauth2.resourceserver.jwt.audiences[0]=def"
            )
            .run(context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(ReactiveJwtDecoder.class)
                    .doesNotHaveBean(JwtDecoder.class)
            );
    }

    @Configuration(proxyBeanMethods = false)
    @baosys.common.security.annotation.EnableAuthenticationCheck
    static class Enabled {}

    @Configuration(proxyBeanMethods = false)
    static class Custom {

        @Bean
        ReactiveJwtDecoder customDecoder() {
            return token -> Mono.error(new UnsupportedOperationException());
        }
    }
}
