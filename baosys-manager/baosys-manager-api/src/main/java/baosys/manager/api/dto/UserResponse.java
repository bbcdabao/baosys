package baosys.manager.api.dto;

import java.util.List;


public record UserResponse(
        long id,
        String name,
        String systemType,
        List<String> permissions,
        boolean enabled,
        boolean status) {
}
