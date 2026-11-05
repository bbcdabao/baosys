package baosys.manager.app.controllers;

import baosys.common.api.ApiResponse;
import baosys.common.api.PageResponse;
import baosys.manager.api.module.contract.UserApi;
import baosys.manager.api.module.responses.CurrentUserResponse;
import baosys.manager.api.module.responses.LoginClientResponse;
import baosys.manager.api.module.payloads.UserCreateRequest;
import baosys.manager.api.module.responses.UserResponse;
import baosys.manager.api.module.payloads.UserUpdateRequest;
import baosys.manager.app.config.web.TraceIds;
import baosys.manager.app.services.UserAppService;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(UserApi.PREFIX)
public class UserController implements UserApi {

    private final UserAppService userAppService;

    public UserController(UserAppService userAppService) {
        this.userAppService = userAppService;
    }

    @Override
    public ApiResponse<CurrentUserResponse> currentUser(Principal principal) {
        return ApiResponse.success(
            userAppService.currentUser(parseUserId(principal)),
            TraceIds.current()
        );
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:list')")
    public ApiResponse<PageResponse<UserResponse>> list(
        String name,
        String systemType,
        int page,
        int size
    ) {
        return ApiResponse.success(
            userAppService.list(name, systemType, page, size),
            TraceIds.current()
        );
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:add')")
    public ApiResponse<UserResponse> create(UserCreateRequest request, Principal principal) {
        return ApiResponse.success(
            userAppService.create(request, parseUserId(principal)),
            TraceIds.current()
        );
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:update')")
    public ApiResponse<UserResponse> update(UserUpdateRequest request, Principal principal) {
        return ApiResponse.success(
            userAppService.update(request, parseUserId(principal)),
            TraceIds.current()
        );
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:delete')")
    public ApiResponse<Void> delete(long id, Principal principal) {
        userAppService.delete(id, parseUserId(principal));
        return ApiResponse.success(null, TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:session:list')")
    public ApiResponse<List<LoginClientResponse>> loginClients(long id) {
        return ApiResponse.success(userAppService.loginClients(id), TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:session:kick')")
    public ApiResponse<Void> kickClient(long id, long loginId) {
        userAppService.kickClient(id, loginId);
        return ApiResponse.success(null, TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:session:kick')")
    public ApiResponse<Void> kickAllClients(long id) {
        userAppService.kickAllClients(id);
        return ApiResponse.success(null, TraceIds.current());
    }

    private long parseUserId(Principal principal) {
        try {
            return userAppService.userIdForLogin(Long.parseLong(principal.getName()));
        } catch (NullPointerException | NumberFormatException exception) {
            throw new baosys.manager.app.error.BusinessException(
                "AUTH_401_001",
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "登录状态缺失或已过期"
            );
        }
    }
}
