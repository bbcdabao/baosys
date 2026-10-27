package baosys.manager.infra.database.converter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class PermissionPersistenceConverter {

    private PermissionPersistenceConverter() {}

    public static Set<String> decode(String encodedPermissions) {
        if (!StringUtils.hasText(encodedPermissions)) {
            return Set.of();
        }
        return Arrays.stream(encodedPermissions.split(","))
            .map(String::trim)
            .filter(StringUtils::hasLength)
            .collect(Collectors.toUnmodifiableSet());
    }

    public static String encode(Set<String> permissions) {
        return permissions.stream().sorted().collect(Collectors.joining(","));
    }
}
