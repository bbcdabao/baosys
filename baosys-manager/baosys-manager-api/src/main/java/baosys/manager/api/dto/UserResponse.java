package baosys.manager.api.dto;

import java.util.List;

public record UserResponse(
    long id,
    String name,
    java.util.List<String> systemTypes,
    List<String> permissions,
    boolean enabled,
    boolean status
) {}
