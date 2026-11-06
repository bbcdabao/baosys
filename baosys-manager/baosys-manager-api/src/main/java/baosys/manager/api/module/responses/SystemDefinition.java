package baosys.manager.api.module.responses;

import java.util.List;
import java.util.Map;

/**
 * 子系统目录；名称按语言标识索引，权限列表顺序即展示顺序。
 * SystemDefinition
 * @param systemType
 * @param name
 * @param permissions
 * @param uiBaseUrl 子系统 UI 地址范围，以斜杠结尾
 * @param homePath 从来源根路径开始的默认主页路径
 */
public record SystemDefinition(
    String systemType,
    Map<String, String> name,
    List<PermissionDefinition> permissions,
    String uiBaseUrl,
    String homePath
) {
    public record PermissionDefinition(String code, Map<String, String> name) {}
}
