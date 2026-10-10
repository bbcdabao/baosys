package baosys.manager.infra.database.converter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;


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
                .sorted()
                .collect(Collectors.joining(","));
    }
}
