package szs.baosys.manager.app.controllers;

import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import szs.baosys.common.api.ApiResponse;
import szs.baosys.common.security.config.SzsSecurityProperties;
import szs.baosys.manager.api.AuthApi;
import szs.baosys.manager.app.config.AuthProperties;
import szs.baosys.manager.app.config.web.TraceIds;
import szs.baosys.manager.app.services.AuthAppService;
import szs.baosys.manager.app.services.LoginResult;
import szs.baosys.manager.api.dto.LoginRequest;

@RestController
@RequestMapping(AuthApi.PREFIX)
public class AuthController implements AuthApi {

    private final AuthAppService authAppService;
    private final AuthProperties authProperties;
    private final SzsSecurityProperties securityProperties;
    private final ObjectProvider<HttpServletResponse> responseProvider;

    public AuthController(
            AuthAppService authAppService,
            AuthProperties authProperties,
            SzsSecurityProperties securityProperties,
            ObjectProvider<HttpServletResponse> responseProvider) {
        this.authAppService = authAppService;
        this.authProperties = authProperties;
        this.securityProperties = securityProperties;
        this.responseProvider = responseProvider;
    }

    @Override
    public ApiResponse<Void> login(LoginRequest request) {
        LoginResult result = authAppService.login(request);
        writeCookies(responseProvider.getObject(), result);
        return ApiResponse.success(TraceIds.current());
    }

    @Override
    public ApiResponse<Void> refresh(String refreshToken) {
        LoginResult result = authAppService.refresh(refreshToken);
        writeCookies(responseProvider.getObject(), result);
        return ApiResponse.success(TraceIds.current());
    }

    @Override
    public ApiResponse<Void> logout(String refreshToken) {
        authAppService.logout(refreshToken);
        clearCookies(responseProvider.getObject());
        return ApiResponse.success(null, TraceIds.current());
    }

    private void writeCookies(HttpServletResponse response, LoginResult result) {
        ResponseCookie.ResponseCookieBuilder accessCookieBuilder = ResponseCookie.from(
                        securityProperties.getAccessTokenCookieName(),
                        result.accessToken())
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/");
        applyMaxAge(
                accessCookieBuilder,
                authProperties.getAccessTokenCookieMaxAgeSeconds());
        ResponseCookie accessCookie = accessCookieBuilder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie.ResponseCookieBuilder refreshCookieBuilder =
                ResponseCookie.from(
                                AuthApi.REFRESH_TOKEN_COOKIE_NAME,
                                result.refreshToken())
                        .httpOnly(true)
                        .secure(authProperties.isCookieSecure())
                        .sameSite("Strict")
                        .path(authProperties.getRefreshTokenCookiePath());
        applyMaxAge(
                refreshCookieBuilder,
                authProperties.getRefreshTokenCookieMaxAgeSeconds());
        ResponseCookie refreshCookie = refreshCookieBuilder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        if (!securityProperties.getGateway().getCsrf().isEnabled()) {
            return;
        }
        response.setHeader(AuthApi.CSRF_TOKEN_HEADER_NAME, result.csrfToken());
        ResponseCookie csrfCookie = ResponseCookie.from(
                        securityProperties.getGateway().getCsrf().getCookieName(),
                        result.csrfToken())
                .httpOnly(false)
                .secure(authProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(1))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    private static void applyMaxAge(
            ResponseCookie.ResponseCookieBuilder builder,
            long maxAgeSeconds) {
        builder.maxAge(Duration.ofSeconds(
                maxAgeSeconds == 0 ? -1 : maxAgeSeconds));
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie(
                        securityProperties.getAccessTokenCookieName(),
                        "/",
                        true).toString());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie(
                        AuthApi.REFRESH_TOKEN_COOKIE_NAME,
                        authProperties.getRefreshTokenCookiePath(),
                        true).toString());
        if (securityProperties.getGateway().getCsrf().isEnabled()) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    expiredCookie(
                            securityProperties.getGateway().getCsrf().getCookieName(),
                            "/",
                            false).toString());
        }
    }

    private ResponseCookie expiredCookie(
            String name,
            String path,
            boolean httpOnly) {
        return ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(authProperties.isCookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

}
