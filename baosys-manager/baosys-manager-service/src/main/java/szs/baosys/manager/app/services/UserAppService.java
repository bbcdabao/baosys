package szs.baosys.manager.app.services;

import java.util.List;

import szs.baosys.common.api.PageResponse;
import szs.baosys.manager.api.dto.CurrentUserResponse;
import szs.baosys.manager.api.dto.LoginClientResponse;
import szs.baosys.manager.api.dto.UserCreateRequest;
import szs.baosys.manager.api.dto.UserResponse;
import szs.baosys.manager.api.dto.UserUpdateRequest;
import szs.baosys.manager.api.enums.SystemType;

public interface UserAppService {

    CurrentUserResponse currentUser(long userId);

    PageResponse<UserResponse> list(
            String name,
            SystemType systemType,
            int page,
            int size);

    UserResponse create(UserCreateRequest request);

    UserResponse update(UserUpdateRequest request);

    void delete(long id);

    List<LoginClientResponse> loginClients(long userId);

    void kickClient(long userId, long loginId);

    void kickAllClients(long userId);
}
