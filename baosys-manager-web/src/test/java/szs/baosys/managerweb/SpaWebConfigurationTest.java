package szs.baosys.managerweb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = ManagerWebApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpaWebConfigurationTest {

    private static final String TEST_STYLESHEET_PATH = "/assets/test.css";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

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
    void shouldServeStaticAssetWhenInvalidAccessTokenCookieIsPresent() {
        webTestClient.get()
                .uri(TEST_STYLESHEET_PATH)
                .cookie("access_token", "stale-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.valueOf("text/css"))
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .isNotBlank());

        org.mockito.Mockito.verify(jwtDecoder, org.mockito.Mockito.never())
                .decode("stale-token");
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
