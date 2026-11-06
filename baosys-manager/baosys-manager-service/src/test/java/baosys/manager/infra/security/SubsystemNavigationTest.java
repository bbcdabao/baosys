package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;
import baosys.manager.api.module.responses.SystemDefinition;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubsystemNavigationTest {
    private final SubsystemNavigation navigation = navigation("http://localhost:8081/", "/");
    private static final String FALLBACK = "http://localhost:8080/authorized-systems";

    private static SubsystemNavigation navigation(String base, String home) {
        var names = Map.of("zh-CN", "系统", "en-US", "System");
        return new SubsystemNavigation(new SystemCatalog(List.of(
            new SystemDefinition("MGR", names, List.of(), "http://localhost:8080/", "/users-management"),
            new SystemDefinition("CAT", names, List.of(), base, home)
        )), new SharedLoginProperties("http://localhost:8080", false));
    }

    @Test
    void preservesDeepLinksAndPrefersReturnAddressOverDefault() {
        String target = "http://localhost:8081/xxxx/zzzz/yyy?task=123#detail";
        assertThat(navigation.validateReturnUrl(target)).isEqualTo(target);
        assertThat(navigation.destination(target, "MGR", Set.of("CAT"))).isEqualTo(target);
        assertThat(navigation.validateReturnUrl("/login-sessions?page=2"))
            .isEqualTo("http://localhost:8080/login-sessions?page=2");
        assertThat(navigation.destination(null, "CAT", Set.of("CAT"))).isEqualTo("http://localhost:8081/");
        assertThat(navigation.destination(null, "MGR", Set.of("MGR", "CAT")))
            .isEqualTo("http://localhost:8080/users-management");
    }

    @Test
    void missingEntryPermissionAlwaysUsesFallbackWithoutSelectingAnotherSystem() {
        assertThat(navigation.destination("http://localhost:8081/task", "MGR", Set.of("MGR"))).isEqualTo(FALLBACK);
        assertThat(navigation.destination(null, "CAT", Set.of("MGR"))).isEqualTo(FALLBACK);
        assertThat(navigation.destination(null, "CAT", Set.of("CAT:task:list"))).isEqualTo(FALLBACK);
        assertThat(navigation.destination(null, "CAT", Set.of())).isEqualTo(FALLBACK);
        assertThat(navigation.destination(null, "REMOVED", Set.of("MGR"))).isEqualTo(FALLBACK);
        assertThat(navigation.destination(FALLBACK, "CAT", Set.of())).isEqualTo(FALLBACK);
    }

    @Test
    void rejectsUnregisteredOriginsLoopsAndAmbiguousPaths() {
        for (String url : List.of("https://evil.test/", "//evil.test/", "http://localhost:8082/orders",
            "http://localhost.evil.test:8081/", "http://user@localhost:8081/", "javascript:alert(1)",
            "/login", "/api/test", "/oauth2/authorize", "/auth-bridge", "orders", "/%6cogin",
            "http://localhost:8081/#/login", "http://localhost:8081/%5cevil.test",
            "http://localhost:8081/%0d%0aLocation:evil", "http://localhost:8081/%252e%252e/login",
            "http://localhost:8081/x/../login", "http://localhost:8081/x/%2e%2e/login", "http://localhost:8081/api%2ftest")) {
            assertThatThrownBy(() -> navigation.validateReturnUrl(url)).as(url).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void supportsPathScopedAndHashRouterSubsystems() {
        var scoped = navigation("http://localhost:8081/cat/", "/cat/#/tasks");
        assertThat(scoped.destination(null, "CAT", Set.of("CAT"))).isEqualTo("http://localhost:8081/cat/#/tasks");
        assertThat(scoped.validateReturnUrl("http://localhost:8081/cat/tasks?q=1#/detail"))
            .isEqualTo("http://localhost:8081/cat/tasks?q=1#/detail");
        for (String path : List.of("/catalog/task", "/other/task", "/cat/api/task", "/cat/login")) {
            assertThatThrownBy(() -> scoped.validateReturnUrl("http://localhost:8081" + path))
                .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void rejectsInvalidOrOverlappingDeploymentConfiguration() {
        for (String base : List.of("http://localhost:8080/", "http://localhost:8080/cat/",
            "http://other.test:8081/", "https://localhost:8081/", "http://localhost:8081/cat")) {
            assertThatThrownBy(() -> navigation(base, "/")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> navigation("http://localhost:8081/", "/login"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
