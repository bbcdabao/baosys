package baosys.manager.api.module.responses;

import java.time.OffsetDateTime;
import java.util.List;

public record LoginSessionResponse(
    long id,
    long userId,
    String userName,
    List<String> permissions,
    String loginClient,
    OffsetDateTime loginAt,
    OffsetDateTime expiresAt
) {}
