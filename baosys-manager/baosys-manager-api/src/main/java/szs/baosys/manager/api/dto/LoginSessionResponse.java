package szs.baosys.manager.api.dto;

import java.time.OffsetDateTime;
import szs.baosys.manager.api.enums.SystemType;

public record LoginSessionResponse(
        long id,
        long userId,
        String userName,
        SystemType systemType,
        String loginClient,
        OffsetDateTime loginAt,
        OffsetDateTime expiresAt) {
}
