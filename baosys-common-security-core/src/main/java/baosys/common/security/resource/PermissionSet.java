package baosys.common.security.resource;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.lang.NonNull;

/**
 * 权限命名约定（不做格式校验）：大写字母系统缩写（MGR），或带系统前缀的接口权限（MGR:user:list）
 * （1）系统权限：仅由大写字母组成
 * （2）接口权限：系统权限 + 冒号 + 接口标识符
 * 提供系统权限提取、去重和排序；命名规范由开发维护。
 */
public final class PermissionSet {

    private static final Pattern SYSTEM_CODE_PATTERN = Pattern.compile("[A-Z]+");

    private PermissionSet() {}

    public static boolean isSystem(@NonNull String code) {
        return SYSTEM_CODE_PATTERN.matcher(code).matches();
    }

    /**
     * 提取系统入口权限，并按字典序排序、去重。
     * @param permissions 待分类的权限集合；null 视为空集合
     * @return 符合系统权限命名约定的有序列表
     */
    public static List<String> systems(Collection<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
            .filter(Objects::nonNull)
            .filter(PermissionSet::isSystem)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * 去除空元素、重复项并排序；不校验权限命名或编码长度。
     * @param permissions 待规范化的权限集合；null 视为空集合
     * @return 规范化后的有序列表
     */
    public static List<String> normalize(Collection<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    }
}
