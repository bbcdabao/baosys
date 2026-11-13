package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SharedLoginPropertiesTest {
    @Test
    void acceptsHttpAndHttpsOriginsWithoutPath() {
        assertThat(new SharedLoginProperties("http://127.0.0.1:8080").managerOrigin())
            .isEqualTo("http://127.0.0.1:8080");
        assertThat(new SharedLoginProperties("https://example.com").managerOrigin())
            .isEqualTo("https://example.com");
        for (String value : java.util.List.of("http://localhost:8080/path", "javascript:alert(1)",
            "http://user@localhost:8080", "http://localhost:8080?x=1")) {
            assertThatThrownBy(() -> new SharedLoginProperties(value))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
