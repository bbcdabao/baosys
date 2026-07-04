package szs.baosys.managerweb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpaWebConfigurationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldServeIndexForAnyClientRoute() {
        webTestClient.get()
                .uri("/future/nested-page")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("<div id=\"app\"></div>"));
    }

    @Test
    void shouldNotFallbackForMissingStaticAsset() {
        webTestClient.get()
                .uri("/assets/missing.js")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldNotFallbackWhenClientDoesNotAcceptHtml() {
        webTestClient.get()
                .uri("/future/nested-page")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldNotFallbackForApiPath() {
        webTestClient.get()
                .uri("/api/not-configured")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
