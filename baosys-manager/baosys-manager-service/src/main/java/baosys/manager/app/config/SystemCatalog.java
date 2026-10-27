package baosys.manager.app.config;

import baosys.manager.api.dto.SystemDefinition;
import baosys.manager.api.dto.SystemDefinition.PermissionDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 启动时加载并校验目录；不依赖数据库，不提供运行时修改入口。 */
@ConfigurationProperties("baosys")
public record SystemCatalog(List<SystemDefinition> systems) {
    public SystemCatalog {
        if (CollectionUtils.isEmpty(systems)) {
            throw new IllegalArgumentException("baosys.systems must not be empty");
        }
        Set<String> types = new HashSet<>();
        Map<String, String> owners = new java.util.HashMap<>();
        systems = systems
            .stream()
            .map(system -> {
                String type = system.systemType();
                if (!StringUtils.hasText(type) || !types.add(type)) {
                    throw new IllegalArgumentException("Invalid or duplicate system-type: " + type);
                }
                List<PermissionDefinition> permissions =
                    system.permissions() == null ? List.of() : system.permissions();
                var copied = permissions
                    .stream()
                    .map(permission -> {
                        String code = permission.code();
                        if (!StringUtils.hasText(code)) {
                            throw new IllegalArgumentException(
                                "Invalid or duplicate permission in " + type + ": " + code
                            );
                        }
                        String previous = owners.putIfAbsent(code, type);
                        if (previous != null) {
                            throw new IllegalArgumentException(
                                "Duplicate permission " + code + " in " + previous + " and " + type
                            );
                        }
                        return new PermissionDefinition(
                            code,
                            names(permission.name(), type + "/" + code)
                        );
                    })
                    .toList();
                return new SystemDefinition(type, names(system.name(), type), copied);
            })
            .toList();
        if (!types.contains("MGR")) {
            throw new IllegalArgumentException("MGR must be present in baosys.systems");
        }
    }

    /** 校验中英文名称，并复制为不可变映射，防止配置加载后被修改。 */
    private static Map<String, String> names(Map<String, String> names, String owner) {
        if (
            CollectionUtils.isEmpty(names) ||
            !StringUtils.hasText(names.get("zh-CN")) ||
            !StringUtils.hasText(names.get("en-US"))
        ) {
            throw new IllegalArgumentException("zh-CN and en-US names are required for " + owner);
        }
        return Map.copyOf(names);
    }

    public boolean contains(String systemType) {
        return systems.stream().anyMatch(system -> system.systemType().equals(systemType));
    }

    /** 返回指定系统声明的接口权限，不包含系统入口权限。 */
    public Set<String> codes(String systemType) {
        return systems
            .stream()
            .filter(system -> system.systemType().equals(systemType))
            .flatMap(system -> system.permissions().stream())
            .map(PermissionDefinition::code)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** 汇总系统入口与接口权限；未知系统直接拒绝。 */
    public Set<String> grantsFor(String type) {
        if (!contains(type)) {
            throw new IllegalArgumentException("Unknown system: " + type);
        }
        var grants = new HashSet<>(codes(type));
        grants.add(type);
        return Set.copyOf(grants);
    }

    /** 签发时过滤未知或已停用的权限；接口写入权限时使用严格校验。 */
    public Set<String> effectiveGrants(Set<String> grants) {
        var effective = new HashSet<String>();
        for (var system : systems) {
            String entry = system.systemType();
            if (grants.contains(entry)) {
                effective.add(entry);
                system
                    .permissions()
                    .stream()
                    .map(PermissionDefinition::code)
                    .filter(grants::contains)
                    .forEach(effective::add);
            }
        }
        return Set.copyOf(effective);
    }

    /** 写入前校验权限目录归属，并要求接口权限具备对应的系统入口权限。 */
    public Set<String> validateGrants(Set<String> grants) {
        var normalized = Set.copyOf(
            baosys.common.security.resource.PermissionSet.normalize(grants)
        );
        if (!effectiveGrants(normalized).equals(normalized)) {
            throw new IllegalArgumentException(
                "Unknown permission or missing system entry permission"
            );
        }
        return normalized;
    }
}
