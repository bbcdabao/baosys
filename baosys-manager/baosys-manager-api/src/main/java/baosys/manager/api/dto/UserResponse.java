package baosys.manager.api.dto;

import java.util.List;

import baosys.manager.api.enums.SystemType;

public record UserResponse(
        long id,
        String name,
        SystemType systemType,
        List<String> permissions,
        boolean enabled,
        boolean status) {
}
