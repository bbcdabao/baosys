package szs.baosys.manager.infra.database.converter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import szs.baosys.manager.app.model.PermissionCode;

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
