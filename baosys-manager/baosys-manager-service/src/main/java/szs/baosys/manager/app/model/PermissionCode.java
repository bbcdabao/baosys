package szs.baosys.manager.app.model;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import szs.baosys.manager.api.enums.SystemType;

public enum PermissionCode {
    USER_LIST(SystemType.MANAGER, "user:list"),
    USER_ADD(SystemType.MANAGER, "user:add"),
    USER_UPDATE(SystemType.MANAGER, "user:update"),
    USER_DELETE(SystemType.MANAGER, "user:delete"),
    USER_SESSION_LIST(SystemType.MANAGER, "user:session:list"),
    USER_SESSION_KICK(SystemType.MANAGER, "user:session:kick");

    private final SystemType systemType;
    private final String code;

    PermissionCode(SystemType systemType, String code) {
        this.systemType = systemType;
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static boolean isValid(SystemType systemType, String code) {
        return code != null && codes(systemType).contains(code);
    }

    public static int order(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code.equals(code))
                .findFirst()
                .map(Enum::ordinal)
                .orElse(Integer.MAX_VALUE);
    }

    public static Set<String> codes(SystemType systemType) {
        return Arrays.stream(values())
                .filter(permission -> permission.systemType == systemType)
                .map(PermissionCode::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static List<String> orderedCodes(SystemType systemType) {
        return Arrays.stream(values())
                .filter(permission -> permission.systemType == systemType)
                .map(PermissionCode::code)
                .toList();
    }
}
