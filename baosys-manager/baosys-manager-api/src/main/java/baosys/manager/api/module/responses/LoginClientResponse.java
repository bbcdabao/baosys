package baosys.manager.api.module.responses;

import java.time.OffsetDateTime;

public record LoginClientResponse(
    long id, 
    String loginClient, 
    OffsetDateTime loginAt
) {}
