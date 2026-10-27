package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SharedLoginPropertiesTest {

    private final SharedLoginProperties properties = new SharedLoginProperties(
        "http://localhost:8080",
        List.of("http://localhost:8081"),
        false
    );

    @Test
    void allowsRegisteredPortsAndPreservesQueryAndFragment() {
        assertThat(properties.returnUrl("http://localhost:8081/orders?page=2#row-3")).isEqualTo(
            "http://localhost:8081/orders?page=2#row-3"
        );
        assertThat(properties.returnUrl("/login-sessions?page=2")).isEqualTo(
            "http://localhost:8080/login-sessions?page=2"
        );
        assertThat(properties.returnUrl(null)).isEqualTo("http://localhost:8080/users-management");
    }

    @Test
    void rejectsForeignOriginsCredentialsAndLoginLoops() {
        for (String url : List.of(
            "https://evil.test/",
            "//evil.test/",
            "http://localhost:8082/orders",
            "http://user@localhost:8081/",
            "javascript:alert(1)",
            "/login",
            "/api/test",
            "/oauth2/authorization/baosys",
            "http://localhost:8081/%5cevil.test",
            "http://localhost:8081/%0d%0aLocation:evil"
        )) {
            assertThatThrownBy(() -> properties.returnUrl(url))
                .as(url)
                .isInstanceOf(baosys.manager.app.error.BusinessException.class);
        }
    }

    @Test
    void supportsIpAndRejectsMixedHostsOrHttpsWithoutSecureCookies() {
        assertThat(
            new SharedLoginProperties(
                "http://127.0.0.1:8000",
                List.of("http://127.0.0.1:8001"),
                false
            ).returnUrl("http://127.0.0.1:8001/orders")
        ).isEqualTo("http://127.0.0.1:8001/orders");
        assertThatThrownBy(() ->
            new SharedLoginProperties("https://example.com", List.of(), false)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            new SharedLoginProperties("http://localhost:8080", List.of("http://other.test"), false)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
