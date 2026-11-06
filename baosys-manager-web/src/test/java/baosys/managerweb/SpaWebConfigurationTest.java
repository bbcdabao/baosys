package baosys.managerweb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@org.springframework.context.annotation.Import(TestOAuth2Configuration.class)
@SpringBootTest(
    classes = ManagerWebApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class SpaWebConfigurationTest {

    @Autowired
    private org.springframework.context.ApplicationContext diagnosticContext;

    @org.junit.jupiter.api.BeforeEach
    void resourceDiagnostics() {
        System.out.println(
            "SPA_CONFIG=" + diagnosticContext.getBeansOfType(SpaWebConfiguration.class)
        );
        for (String path : java.util.List.of(
            "static/",
            "static/index.html",
            "static/assets/test.css"
        )) {
            System.out.println(
                "SPA_RESOURCE=" + path + " -> " + getClass().getClassLoader().getResource(path)
            );
        }
        diagnosticContext
            .getBeansOfType(org.springframework.web.reactive.handler.SimpleUrlHandlerMapping.class)
            .forEach((name, mapping) ->
                mapping.getHandlerMap().forEach((pattern, handler) -> {
                    if (
                        handler instanceof
                            org.springframework.web.reactive.resource.ResourceWebHandler resource
                    ) {
                        System.out.println(
                            "SPA_HANDLER=" +
                                pattern +
                                " resolvers=" +
                                resource.getResourceResolvers()
                        );
                    }
                })
            );
    }

    private static final String TEST_STYLESHEET_PATH = "/assets/test.css";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void servesPrefixedAndProxyStrippedRoutes() {
        for (String path : java.util.List.of("/baosys-manager/", "/baosys-manager/users-management",
            "/baosys-manager/future/nested", "/users-management")) {
            webTestClient.get().uri(path).accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isOk().expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
        }
        webTestClient.get().uri("/baosys-manager/assets/test.css").exchange().expectStatus().isOk();
        for (String path : java.util.List.of("/baosys-manager/assets/missing.js",
            "/baosys-manager/assets/missing", "/baosys-other/tasks")) {
            webTestClient.get().uri(path).accept(MediaType.TEXT_HTML).exchange().expectStatus().isNotFound();
        }
        webTestClient.get().uri("/baosys-manager?task=1").exchange()
            .expectStatus().isPermanentRedirect().expectHeader().valueEquals("Location", "/baosys-manager/?task=1");
    }

    @Test
    void shouldServeIndexForAnyClientRoute() {
        webTestClient
            .get()
            .uri("/future/nested-page")
            .accept(MediaType.TEXT_HTML)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
            .value(body ->
                org.assertj.core.api.Assertions.assertThat(body).contains("<div id=\"app\"></div>")
            );
    }

    @Test
    void shouldNotFallbackForMissingStaticAsset() {
        webTestClient.get().uri("/assets/missing.js").exchange().expectStatus().isNotFound();
    }

    @Test
    void shouldServeStaticAssetWhenInvalidAccessTokenCookieIsPresent() {
        webTestClient
            .get()
            .uri(TEST_STYLESHEET_PATH)
            .cookie("access_token", "stale-token")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.valueOf("text/css"))
            .expectBody(String.class)
            .value(body -> org.assertj.core.api.Assertions.assertThat(body).isNotBlank());

        org.mockito.Mockito.verify(jwtDecoder, org.mockito.Mockito.never()).decode("stale-token");
    }

    @Test
    void shouldNotFallbackWhenClientDoesNotAcceptHtml() {
        webTestClient
            .get()
            .uri("/future/nested-page")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void shouldNotFallbackForApiPath() {
        webTestClient.get().uri("/api/not-configured").exchange().expectStatus().isUnauthorized();
    }
}
