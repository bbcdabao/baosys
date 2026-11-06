package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SharedLoginPropertiesTest {
    @Test
    void requiresSecureCookiesForHttpsAndAnOriginWithoutPath() {
        assertThat(new SharedLoginProperties("http://127.0.0.1:8080", false).managerOrigin())
            .isEqualTo("http://127.0.0.1:8080");
        assertThatThrownBy(() -> new SharedLoginProperties("https://example.com", false))
            .isInstanceOf(IllegalArgumentException.class);
        for (String value : java.util.List.of("http://localhost:8080/path", "javascript:alert(1)",
            "http://user@localhost:8080", "http://localhost:8080?x=1")) {
            assertThatThrownBy(() -> new SharedLoginProperties(value, false))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
