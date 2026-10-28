package baosys.manager.api.dto;

import java.util.List;

public record UserResponse(
    long id,
    String name,
    List<String> permissions,
    boolean enabled,
    boolean status
) {}
