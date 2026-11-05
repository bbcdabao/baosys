package baosys.manager.app.assembler;

import baosys.manager.api.module.responses.UserResponse;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.entity.UserInfoEntity;

public final class UserAssembler {

    private UserAssembler() {}

    public static UserResponse toResponse(UserInfoEntity user, boolean online) {
        return new UserResponse(
            user.getId(),
            user.getUserName(),
            PermissionPersistenceConverter.decode(user.getUserPermissions())
                .stream()
                .sorted()
                .toList(),
            user.isEnabled(),
            online
        );
    }
}
