package szs.baosys.manager.app.assembler;

import java.util.Comparator;

import szs.baosys.manager.app.model.PermissionCode;
import szs.baosys.manager.api.dto.UserResponse;
import szs.baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import szs.baosys.manager.infra.database.entity.UserInfoEntity;

public final class UserAssembler {

    private UserAssembler() {
    }

    public static UserResponse toResponse(UserInfoEntity user, boolean online) {
        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getSystemType(),
                PermissionPersistenceConverter.decode(user.getUserPermissions()).stream()
                        .sorted(Comparator.comparingInt(PermissionCode::order))
                        .toList(),
                online);
    }
}
