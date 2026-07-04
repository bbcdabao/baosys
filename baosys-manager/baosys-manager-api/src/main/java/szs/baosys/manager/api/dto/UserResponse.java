package szs.baosys.manager.api.dto;

import java.util.List;

import szs.baosys.manager.api.enums.SystemType;

public record UserResponse(
        long id,
        String name,
        SystemType systemType,
        List<String> permissions,
        boolean status) {
}
