package baosys.manager.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import baosys.common.api.ApiResponse;
import baosys.manager.api.dto.LoginRequest;

public interface AuthApi {

    String SERVICE_NAME = "baosys-manager";
    String PREFIX = "/v1/auth";
    String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    String CSRF_TOKEN_HEADER_NAME = "X-CSRF-Token";

    @PostMapping("/login")
    ApiResponse<Void> login(
                @Valid
                @RequestBody
                LoginRequest request);

    @PostMapping("/refresh")
    ApiResponse<Void> refresh(
                @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME,required = false)
                String refreshToken);

    @PostMapping("/logout")
    ApiResponse<Void> logout(
                @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME,required = false)
                String refreshToken);
}
