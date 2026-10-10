package baosys.manager.api.dto;

import java.time.OffsetDateTime;

public record LoginSessionResponse(
        long id,
        long userId,
        String userName,
        String systemType,
        String loginClient,
        OffsetDateTime loginAt,
        OffsetDateTime expiresAt) {
}
