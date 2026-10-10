package baosys.manager.app.config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import baosys.manager.api.dto.SystemDefinition;
import baosys.manager.api.dto.SystemDefinition.PermissionDefinition;

/** 启动时加载并校验目录；不依赖数据库，不提供运行时修改入口。 */
@ConfigurationProperties("baosys")
public record SystemCatalog(List<SystemDefinition> systems) {
    public SystemCatalog {
        if (systems == null || systems.isEmpty()) {
            throw new IllegalArgumentException("baosys.systems must not be empty");
        }
        Set<String> types = new HashSet<>();
        systems = systems.stream().map(system -> {
            String type = system.systemType();
            if (type == null || !type.matches("[A-Z][A-Z0-9_]{0,31}") || !types.add(type)) {
                throw new IllegalArgumentException("Invalid or duplicate system-type: " + type);
            }
            Set<String> codes = new HashSet<>();
            List<PermissionDefinition> permissions = system.permissions() == null
                    ? List.of() : system.permissions();
            var copied = permissions.stream().map(permission -> {
                String code = permission.code();
                if (code == null || !code.matches("[a-zA-Z0-9][a-zA-Z0-9:_.*-]{0,127}")
                        || !codes.add(code)) {
                    throw new IllegalArgumentException("Invalid or duplicate permission in " + type + ": " + code);
                }
                return new PermissionDefinition(code, names(permission.name(), type + "/" + code));
            }).toList();
            return new SystemDefinition(type, names(system.name(), type), copied);
        }).toList();
        if (!types.contains("MANAGER")) {
            throw new IllegalArgumentException("MANAGER must be present in baosys.systems");
        }
    }

    private static Map<String, String> names(Map<String, String> names, String owner) {
        if (names == null || names.get("zh-CN") == null || names.get("zh-CN").isBlank()
                || names.get("en-US") == null || names.get("en-US").isBlank()) {
            throw new IllegalArgumentException("zh-CN and en-US names are required for " + owner);
        }
        return Map.copyOf(names);
    }

    public boolean contains(String systemType) {
        return systems.stream().anyMatch(system -> system.systemType().equals(systemType));
    }

    public Set<String> codes(String systemType) {
        return systems.stream().filter(system -> system.systemType().equals(systemType))
                .flatMap(system -> system.permissions().stream()).map(PermissionDefinition::code)
                .collect(Collectors.toUnmodifiableSet());
    }
}
