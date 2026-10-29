package baosys.manager.api;

import baosys.common.api.ApiResponse;
import baosys.manager.api.dto.BrowserLoginRequest;
import baosys.manager.api.dto.BrowserLoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 浏览器共享登录契约；令牌通过 HttpOnly Cookie 传递，不进入响应正文。
 * BrowserAuthApi
 */
public interface BrowserAuthApi {
    String PREFIX = "/v1/browser-auth";

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<BrowserLoginResponse>> login(
        @Valid @RequestBody BrowserLoginRequest login,
        @CookieValue(name = "BAOSYS_REFRESH_TOKEN", required = false) String previous
    );

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<Void>> refresh(
        @CookieValue(name = "BAOSYS_REFRESH_TOKEN", required = false) String refresh
    );

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<Void>> logout(
        @CookieValue(name = "BAOSYS_REFRESH_TOKEN", required = false) String refresh
    );
}
