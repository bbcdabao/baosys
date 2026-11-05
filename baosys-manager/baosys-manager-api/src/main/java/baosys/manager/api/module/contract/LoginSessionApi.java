package baosys.manager.api.module.contract;

import baosys.common.api.ApiResponse;
import baosys.common.api.PageResponse;
import baosys.manager.api.module.responses.LoginSessionResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface LoginSessionApi {
    String PREFIX = "/v1/login-sessions";

    @GetMapping
    ApiResponse<PageResponse<LoginSessionResponse>> list(
        @Min(1) @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "userName", required = false) String userName,
        @Min(0) @RequestParam(name = "page", defaultValue = "0") int page,
        @Min(1) @Max(200) @RequestParam(name = "size", defaultValue = "20") int size
    );
}
