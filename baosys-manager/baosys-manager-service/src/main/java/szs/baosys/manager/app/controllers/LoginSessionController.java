package szs.baosys.manager.app.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import szs.baosys.common.api.ApiResponse;
import szs.baosys.common.api.PageResponse;
import szs.baosys.manager.api.LoginSessionApi;
import szs.baosys.manager.api.dto.LoginSessionResponse;
import szs.baosys.manager.app.config.web.TraceIds;
import szs.baosys.manager.app.services.LoginSessionAppService;

@RestController
@RequestMapping(LoginSessionApi.PREFIX)
public class LoginSessionController implements LoginSessionApi {

    private final LoginSessionAppService loginSessionAppService;

    public LoginSessionController(LoginSessionAppService loginSessionAppService) {
        this.loginSessionAppService = loginSessionAppService;
    }

    @Override
    @PreAuthorize("hasAuthority('user:session:list')")
    public ApiResponse<PageResponse<LoginSessionResponse>> list(
            Long userId,
            String userName,
            int page,
            int size) {
        return ApiResponse.success(
                loginSessionAppService.list(userId, userName, page, size),
                TraceIds.current());
    }
}
