package baosys.manager.app.config;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import baosys.manager.api.dto.SystemDefinition;
import baosys.manager.api.dto.SystemDefinition.PermissionDefinition;

class SystemCatalogTest {
    private static final Map<String, String> NAMES = Map.of("zh-CN", "测试", "en-US", "Test");

    @Test
    void loadsBundledRootFileWithLocalizedNames() {
        var catalog = CatalogFixture.load();
        assertThat(catalog.systems().get(0).name()).containsEntry("zh-CN", "后台管理");
        assertThat(catalog.codes("MANAGER")).contains("user:add", "user:session:kick");
        assertThat(catalog.contains("UNKNOWN")).isFalse();
    }

    @Test
    void rejectsDuplicateTypesAndMissingManager() {
        var manager = system("MANAGER", List.of());
        assertThatThrownBy(() -> new SystemCatalog(List.of(manager, manager)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate system-type");
        assertThatThrownBy(() -> new SystemCatalog(List.of(system("LAB", List.of()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MANAGER");
    }

    @Test
    void rejectsInvalidCodesAndMissingTranslations() {
        for (String code : List.of("a,b", "a b", "", "x".repeat(129))) {
            assertThatThrownBy(() -> new SystemCatalog(List.of(system("MANAGER",
                    List.of(new PermissionDefinition(code, NAMES))))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var permission = new PermissionDefinition("read", NAMES);
        assertThatThrownBy(() -> new SystemCatalog(List.of(system("MANAGER", List.of(permission, permission)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate permission");
        assertThatThrownBy(() -> new SystemCatalog(List.of(new SystemDefinition("MANAGER",
                Map.of("zh-CN", "管理"), List.of()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("en-US");
    }

    private static SystemDefinition system(String type, List<PermissionDefinition> permissions) {
        return new SystemDefinition(type, NAMES, permissions);
    }
}
