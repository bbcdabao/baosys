package baosys.manager.api.module.responses;

import java.util.List;

public record UserResponse(
    long id,
    String name,
    List<String> permissions,
    boolean enabled,
    boolean status,
    String defaultSystemType
) {}
