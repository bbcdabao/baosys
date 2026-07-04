package szs.baosys.manager.infra.database.converter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import szs.baosys.manager.app.model.PermissionCode;

/**
 * 在领域权限集合与数据库逗号分隔格式之间转换。
 *
 * <p>写入时按权限目录顺序固定编码，减少无意义的数据变更；读取时去重，
 * 兼容历史数据中重复或带空白的权限项。</p>
 */
public final class PermissionPersistenceConverter {

    private PermissionPersistenceConverter() {
    }

    public static Set<String> decode(String encodedPermissions) {
        if (encodedPermissions == null || encodedPermissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(encodedPermissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String encode(Set<String> permissions) {
        return permissions.stream()
                .sorted(Comparator.comparingInt(PermissionCode::order))
                .collect(Collectors.joining(","));
    }
}
