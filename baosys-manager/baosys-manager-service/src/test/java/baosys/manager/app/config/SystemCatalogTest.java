package baosys.manager.app.config;

import static org.assertj.core.api.Assertions.*;

import baosys.manager.api.dto.SystemDefinition;
import baosys.manager.api.dto.SystemDefinition.PermissionDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemCatalogTest {

    private static final Map<String, String> NAMES = Map.of("zh-CN", "测试", "en-US", "Test");

    @Test
    void loadsBundledRootFileWithLocalizedNames() {
        var catalog = CatalogFixture.load();
        assertThat(catalog.systems().get(0).name()).containsEntry("zh-CN", "后台管理");
        assertThat(catalog.codes("MGR")).contains("MGR:user:add", "MGR:user:session:kick");
        assertThat(catalog.contains("UNKNOWN")).isFalse();
    }

    @Test
    void rejectsDuplicateTypesAndMissingManager() {
        var manager = system("MGR", List.of());
        assertThatThrownBy(() -> new SystemCatalog(List.of(manager, manager)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate system-type");
        assertThatThrownBy(() -> new SystemCatalog(List.of(system("LAB", List.of()))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MGR");
    }

    @Test
    void rejectsInvalidCodesAndMissingTranslations() {
        for (String code : List.of("", " ")) {
            assertThatThrownBy(() ->
                new SystemCatalog(
                    List.of(system("MGR", List.of(new PermissionDefinition(code, NAMES))))
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }
        var permission = new PermissionDefinition("MGR:shared:read", NAMES);
        assertThatThrownBy(() ->
            new SystemCatalog(List.of(system("MGR", List.of(permission, permission))))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate permission");
        assertThatThrownBy(() ->
            new SystemCatalog(
                List.of(new SystemDefinition("MGR", Map.of("zh-CN", "管理"), List.of()))
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("en-US");
    }

    private static SystemDefinition system(String type, List<PermissionDefinition> permissions) {
        return new SystemDefinition(type, NAMES, permissions);
    }

    @Test
    void rejectsGloballyDuplicatePermissionCodes() {
        var duplicate = new PermissionDefinition("MGR:shared:read", NAMES);
        assertThatThrownBy(() ->
            new SystemCatalog(
                List.of(system("MGR", List.of(duplicate)), system("LAB", List.of(duplicate)))
            )
        )
            .hasMessageContaining("MGR:shared:read")
            .hasMessageContaining("LAB");
    }

    @Test
    void namingConventionsAreNotEnforcedByTheCatalog() {
        var catalog = new SystemCatalog(
            List.of(
                system("MGR", List.of()),
                system("custom-system", List.of(new PermissionDefinition("customOperation", NAMES)))
            )
        );
        assertThat(
            catalog.validateGrants(java.util.Set.of("custom-system", "customOperation"))
        ).containsExactlyInAnyOrder("custom-system", "customOperation");
    }

    @Test
    void validatesMultipleSystemEntriesAndRejectsOrphanApiGrants() {
        var catalog = CatalogFixture.load();
        var grants = java.util.Set.of("MGR", "SIM", "MGR:user:list");
        assertThat(catalog.validateGrants(grants)).isEqualTo(grants);
        assertThat(catalog.validateGrants(java.util.Set.of("SIM"))).containsExactly("SIM");
        assertThat(catalog.validateGrants(java.util.Set.of())).isEmpty();
        assertThatThrownBy(() ->
            catalog.validateGrants(java.util.Set.of("MGR:user:list"))
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.validateGrants(java.util.Set.of("UNKNOWN"))).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThat(
            catalog.effectiveGrants(
                java.util.Set.of("MGR", "MGR:user:list", "retired:code", "REMOVED")
            )
        ).containsExactlyInAnyOrder("MGR", "MGR:user:list");
    }
}
