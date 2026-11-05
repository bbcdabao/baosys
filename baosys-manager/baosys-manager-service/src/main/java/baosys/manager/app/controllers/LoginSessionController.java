package baosys.manager.app.controllers;

import baosys.common.api.ApiResponse;
import baosys.common.api.PageResponse;
import baosys.manager.api.module.contract.LoginSessionApi;
import baosys.manager.api.module.responses.LoginSessionResponse;
import baosys.manager.app.config.web.TraceIds;
import baosys.manager.app.services.LoginSessionAppService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(LoginSessionApi.PREFIX)
public class LoginSessionController implements LoginSessionApi {

    private final LoginSessionAppService loginSessionAppService;

    public LoginSessionController(LoginSessionAppService loginSessionAppService) {
        this.loginSessionAppService = loginSessionAppService;
    }

    @Override
    @PreAuthorize("hasAuthority('MGR:user:session:list')")
    public ApiResponse<PageResponse<LoginSessionResponse>> list(
        Long userId,
        String userName,
        int page,
        int size
    ) {
        return ApiResponse.success(
            loginSessionAppService.list(userId, userName, page, size),
            TraceIds.current()
        );
    }
}
