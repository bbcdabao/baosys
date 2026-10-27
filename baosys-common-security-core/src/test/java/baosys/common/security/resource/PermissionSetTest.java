package baosys.common.security.resource;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PermissionSetTest {

    @Test
    void handlesNullCollectionsAndElements() {
        assertThat(PermissionSet.systems(null)).isEmpty();
        assertThat(PermissionSet.normalize(null)).isEmpty();

        var permissions = Arrays.asList("SIM", null, "MGR:user:list", "MGR", "SIM");
        assertThat(PermissionSet.systems(permissions)).containsExactly("MGR", "SIM");
        assertThat(PermissionSet.normalize(permissions))
            .containsExactly("MGR", "MGR:user:list", "SIM");
    }

    @Test
    void recognizesOnlyCompleteUppercaseSystemCodes() {
        assertThat(PermissionSet.isSystem("MGR")).isTrue();
        for (String code : List.of("", "mgr", "MGR1", "MGR:user:list", " MGR", "MGR\n")) {
            assertThat(PermissionSet.isSystem(code)).as("code: %s", code).isFalse();
        }
    }

    @Test
    void normalizesMultipleSystemsAndAllowsEmptyGrants() {
        var grants = PermissionSet.normalize(List.of("SIM", "MGR:user:list", "MGR", "MGR"));
        assertThat(grants).containsExactly("MGR", "MGR:user:list", "SIM");
        assertThat(PermissionSet.systems(grants)).containsExactly("MGR", "SIM");
        assertThat(PermissionSet.normalize(List.of())).isEmpty();
    }

    @Test
    void doesNotEnforceNamingConventions() {
        for (String code : List.of(
            "SCOPE_MGR",
            "system:MANAGER",
            "system:manager",
            "system:",
            "user:list",
            "mgr:user:list",
            "MGR:User:list",
            "MGR:user",
            "MGR:user:*",
            "MGR1",
            "M_GR",
            "a,b",
            "a b"
        )) {
            assertThat(PermissionSet.normalize(List.of(code))).containsExactly(code);
        }
    }

    @Test
    void apiPrefixDoesNotGrantSystemEntry() {
        assertThat(PermissionSet.systems(List.of("MGR:user:list"))).isEmpty();
        assertThat(PermissionSet.normalize(List.of("MGR:user:session:kick"))).containsExactly(
            "MGR:user:session:kick"
        );
    }
}
