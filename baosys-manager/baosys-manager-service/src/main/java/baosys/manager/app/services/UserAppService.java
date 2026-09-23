package baosys.manager.app.services;

import java.util.List;

import baosys.common.api.PageResponse;
import baosys.manager.api.dto.CurrentUserResponse;
import baosys.manager.api.dto.LoginClientResponse;
import baosys.manager.api.dto.UserCreateRequest;
import baosys.manager.api.dto.UserResponse;
import baosys.manager.api.dto.UserUpdateRequest;
import baosys.manager.api.enums.SystemType;

public interface UserAppService {

    CurrentUserResponse currentUser(long userId);

    PageResponse<UserResponse> list(
            String name,
            SystemType systemType,
            int page,
            int size);

    UserResponse create(UserCreateRequest request, long operatorUserId);

    UserResponse update(UserUpdateRequest request, long operatorUserId);

    void delete(long id, long operatorUserId);

    List<LoginClientResponse> loginClients(long userId);

    void kickClient(long userId, long loginId);

    void kickAllClients(long userId);
}
