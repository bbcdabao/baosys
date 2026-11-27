package baosys.common.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import static org.assertj.core.api.Assertions.*;

class SpaConfigurationTest {
    @TempDir Path resources;
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class Web {}

    @Test
    void defaultsToHashModeWithoutClientRouteFallback() throws Exception {
        Files.writeString(resources.resolve("index.html"), "<html>custom-ui</html>");
        Files.writeString(resources.resolve("app.js"), "const custom = true;");
        new ReactiveWebApplicationContextRunner().withUserConfiguration(Web.class)
            .withConfiguration(AutoConfigurations.of(SpaWebConfiguration.class))
            .withPropertyValues("common.spa.enabled=true", "common.spa.base-path=/custom",
                "common.spa.resource-location=" + resources.toUri())
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SpaProperties.class).getFallback().isEnabled()).isFalse();
                var client = WebTestClient.bindToApplicationContext(context).build();
                client.get().uri(java.net.URI.create("/custom?q=a%20b")).exchange().expectStatus().isPermanentRedirect()
                    .expectHeader().valueEquals("Location", "/custom/?q=a%20b");
                for (String path : new String[]{"/custom/", "/"}) {
                    client.get().uri(path).accept(MediaType.TEXT_HTML).exchange().expectStatus().isOk()
                        .expectBody(String.class).isEqualTo("<html>custom-ui</html>");
                }
                client.get().uri("/custom/app.js").exchange().expectStatus().isOk();
                client.get().uri("/app.js").exchange().expectStatus().isOk();
                for (String path : new String[]{"/custom/users/1", "/users/1", "/custom/missing.js"}) {
                    client.get().uri(path).accept(MediaType.TEXT_HTML).exchange().expectStatus().isNotFound();
                }
            });
    }

    @Test
    void customPrefixAndFallbackExclusionsPreserveFallbackBoundaries() throws Exception {
        Files.writeString(resources.resolve("index.html"), "<html>custom-ui</html>");
        Files.writeString(resources.resolve("app.js"), "const custom = true;");
        new ReactiveWebApplicationContextRunner().withUserConfiguration(Web.class)
            .withConfiguration(AutoConfigurations.of(SpaWebConfiguration.class))
            .withPropertyValues(
                "common.spa.enabled=true",
                "common.spa.base-path=/custom",
                "common.spa.resource-location=" + resources.toUri(),
                "common.spa.fallback.enabled=true",
                "common.spa.fallback.excluded-paths[0]=/ws/",
                "common.spa.fallback.excluded-paths[1]=swagger-ui",
                "common.spa.fallback.excluded-prefixes[0]=/baosys-"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                var client = WebTestClient.bindToApplicationContext(context).build();
                client.get().uri(java.net.URI.create("/custom?q=a%20b")).exchange().expectStatus().isPermanentRedirect()
                    .expectHeader().valueEquals("Location", "/custom/?q=a%20b");
                for (String path : new String[]{"/custom/", "/", "/custom/users/1", "/users/1", "/workspace"}) {
                    client.get().uri(path).accept(MediaType.TEXT_HTML).exchange().expectStatus().isOk()
                        .expectBody(String.class).isEqualTo("<html>custom-ui</html>");
                }
                client.get().uri("/custom/app.js").exchange().expectStatus().isOk();
                for (String path : new String[]{
                    "/api/missing", "/actuator/missing", "/oauth2/missing",
                    "/custom/assets/missing.js", "/custom/../secret.txt", "/missing.js",
                    "/ws", "/ws/stream", "/custom/ws/stream", "/swagger-ui/docs",
                    "/baosys-other/tasks"
                }) {
                    client.get().uri(path).accept(MediaType.TEXT_HTML).exchange().expectStatus().is4xxClientError();
                }
                client.get().uri("/unknown").accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isNotFound();
                client.post().uri("/custom/users").accept(MediaType.TEXT_HTML).exchange().expectStatus().is4xxClientError();
            });
    }

    @Test
    void disabledByDefaultAndRejectsInvalidConfiguration() {
        new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpaWebConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean(SpaWebConfiguration.class));
        assertThatThrownBy(() -> new SpaProperties().setBasePath("/ui/../api")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpaProperties().setResourceLocation("https://example.com/"))
            .isInstanceOf(IllegalArgumentException.class);
        var fallback = new SpaProperties.Fallback();
        assertThatThrownBy(() -> fallback.setExcludedPaths(List.of("/"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fallback.setExcludedPaths(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fallback.setExcludedPaths(List.of("../api"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fallback.setExcludedPrefixes(List.of("/"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fallback.setExcludedPrefixes(List.of("baosys//x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
