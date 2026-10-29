package baosys.manager.api.dto;

import java.time.OffsetDateTime;

public record LoginClientResponse(
    long id, 
    String loginClient, 
    OffsetDateTime loginAt
) {}
