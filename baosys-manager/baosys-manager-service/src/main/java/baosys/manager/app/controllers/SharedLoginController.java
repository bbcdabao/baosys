package baosys.manager.app.controllers;

import baosys.common.api.ApiResponse;
import baosys.common.security.resource.SharedTokenCookies;
import baosys.manager.app.config.web.TraceIds;
import baosys.manager.infra.security.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.*;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 共享登录接口：通过 HttpOnly Cookie 下发令牌，响应正文仅返回业务结果。 */
@RestController
@RequestMapping("/v1/browser-auth")
public class SharedLoginController {

    private final SharedLoginService service;
    private final SharedLoginProperties properties;

    public SharedLoginController(SharedLoginService service, SharedLoginProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    public record Login(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 256) String password,
        @Size(max = 8192) String redirectUrl
    ) {}

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
        @Valid @RequestBody Login login,
        @CookieValue(name = SharedTokenCookies.REFRESH, required = false) String previous
    ) {
        // 在创建会话前验证返回地址，避免无效跳转请求留下新授权。
        String redirect = properties.returnUrl(login.redirectUrl());
        var tokens = service.login(login.username(), login.password(), previous);
        return response(tokens).body(
            ApiResponse.success(Map.of("redirectUrl", redirect), TraceIds.current())
        );
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> refresh(
        @CookieValue(name = SharedTokenCookies.REFRESH, required = false) String refresh
    ) {
        return response(service.refresh(refresh)).body(
            ApiResponse.success(null, TraceIds.current())
        );
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> logout(
        @CookieValue(name = SharedTokenCookies.REFRESH, required = false) String refresh
    ) {
        service.revoke(refresh);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.SET_COOKIE,
                cookie(SharedTokenCookies.ACCESS, "", "/", Duration.ZERO),
                cookie(
                    SharedTokenCookies.REFRESH,
                    "",
                    SharedTokenCookies.REFRESH_PATH,
                    Duration.ZERO
                )
            )
            .body(ApiResponse.success(null, TraceIds.current()));
    }

    /** 禁止缓存令牌响应，并分别设置访问令牌和刷新令牌的 Cookie 生命周期。 */
    private ResponseEntity.BodyBuilder response(SharedLoginService.Tokens tokens) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.SET_COOKIE,
                cookie(
                    SharedTokenCookies.ACCESS,
                    tokens.accessToken(),
                    "/",
                    Duration.between(Instant.now(), tokens.accessExpiry())
                ),
                cookie(
                    SharedTokenCookies.REFRESH,
                    tokens.refreshToken(),
                    SharedTokenCookies.REFRESH_PATH,
                    Duration.between(Instant.now(), tokens.refreshExpiry())
                )
            );
    }

    /** 统一 Cookie 安全属性；已过期的令牌以零有效期清除。 */
    private String cookie(String name, String value, String path, Duration age) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite("Lax")
            .path(path)
            .maxAge(age.isNegative() ? Duration.ZERO : age)
            .build()
            .toString();
    }
}
