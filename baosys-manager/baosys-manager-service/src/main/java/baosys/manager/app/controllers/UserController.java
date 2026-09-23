package baosys.manager.app.controllers;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import baosys.common.api.ApiResponse;
import baosys.common.api.PageResponse;
import baosys.manager.api.UserApi;
import baosys.manager.app.config.web.TraceIds;
import baosys.manager.app.services.UserAppService;
import baosys.manager.api.dto.CurrentUserResponse;
import baosys.manager.api.dto.LoginClientResponse;
import baosys.manager.api.dto.UserCreateRequest;
import baosys.manager.api.dto.UserResponse;
import baosys.manager.api.dto.UserUpdateRequest;
import baosys.manager.api.enums.SystemType;

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
                TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:list')")
    public ApiResponse<PageResponse<UserResponse>> list(
            String name,
            SystemType systemType,
            int page,
            int size) {
        return ApiResponse.success(
                userAppService.list(name, systemType, page, size),
                TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:add')")
    public ApiResponse<UserResponse> create(
            UserCreateRequest request,
            Principal principal) {
        return ApiResponse.success(
                userAppService.create(request, parseUserId(principal)),
                TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserResponse> update(
            UserUpdateRequest request,
            Principal principal) {
        return ApiResponse.success(
                userAppService.update(request, parseUserId(principal)),
                TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:delete')")
    public ApiResponse<Void> delete(long id, Principal principal) {
        userAppService.delete(id, parseUserId(principal));
        return ApiResponse.success(null, TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:session:list')")
    public ApiResponse<List<LoginClientResponse>> loginClients(long id) {
        return ApiResponse.success(userAppService.loginClients(id), TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:session:kick')")
    public ApiResponse<Void> kickClient(long id, long loginId) {
        userAppService.kickClient(id, loginId);
        return ApiResponse.success(null, TraceIds.current());
    }

    @Override
    @PreAuthorize("hasAuthority('user:session:kick')")
    public ApiResponse<Void> kickAllClients(long id) {
        userAppService.kickAllClients(id);
        return ApiResponse.success(null, TraceIds.current());
    }

    private static long parseUserId(Principal principal) {
        try {
            return Long.parseLong(principal.getName());
        } catch (NullPointerException | NumberFormatException exception) {
            throw new IllegalArgumentException("access token subject must be a user id");
        }
    }
}
