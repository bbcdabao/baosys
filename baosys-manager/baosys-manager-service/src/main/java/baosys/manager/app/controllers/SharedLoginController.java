package baosys.manager.app.controllers;

import baosys.common.api.ApiResponse;
import baosys.common.security.resource.SharedTokenCookies;
import baosys.manager.api.module.contract.BrowserAuthApi;
import baosys.manager.api.module.payloads.BrowserLoginRequest;
import baosys.manager.api.module.responses.BrowserLoginResponse;
import baosys.manager.app.config.web.TraceIds;
import baosys.manager.infra.security.*;
import baosys.manager.app.services.BrowserAuthAppService;
import java.time.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * 共享登录接口：通过 HttpOnly Cookie 下发令牌，响应正文仅返回业务结果。
 * SharedLoginController
 */
@RestController
@RequestMapping(BrowserAuthApi.PREFIX)
public class SharedLoginController implements BrowserAuthApi {

    private final BrowserAuthAppService service;
    private final SharedLoginProperties properties;

    public SharedLoginController(BrowserAuthAppService service, SharedLoginProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public ResponseEntity<ApiResponse<BrowserLoginResponse>> login(
        BrowserLoginRequest login,
        String previous
    ) {
        var tokens = service.login(login.username(), login.password(), previous, login.redirectUrl());
        return response(tokens).body(
            ApiResponse.success(new BrowserLoginResponse(tokens.redirectUrl()), TraceIds.current())
        );
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> refresh(String refresh) {
        return response(service.refresh(refresh)).body(
            ApiResponse.success(null, TraceIds.current())
        );
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> logout(String refresh) {
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

    /**
     * 禁止缓存令牌响应，并分别设置访问令牌和刷新令牌的 Cookie 生命周期。
     * @param tokens
     * @return
     */
    private ResponseEntity.BodyBuilder response(BrowserAuthAppService.Tokens tokens) {
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

    /**
     * 统一 Cookie 安全属性；已过期的令牌以零有效期清除。
     * @param name
     * @param value
     * @param path
     * @param age
     * @return
     */
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
