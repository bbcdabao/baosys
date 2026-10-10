package baosys.manager.api.dto;

import java.util.List;
import java.util.Map;

/** 子系统目录；名称按语言标识索引，权限列表顺序即展示顺序。 */
public record SystemDefinition(String systemType, Map<String, String> name,
        List<PermissionDefinition> permissions) {
    public record PermissionDefinition(String code, Map<String, String> name) {}
}
