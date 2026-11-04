package baosys.manager.infra.security;

import baosys.common.security.resource.SharedTokenCookies;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

/**
 * 授权端点复用框架的 Bearer 认证流程，只适配内部 Cookie 和账户身份。
 */
public final class SharedLoginAuthorizationFilter extends BearerTokenAuthenticationFilter {
    public SharedLoginAuthorizationFilter(
        JwtDecoder decoder, InternalLoginAuthenticator subjects, AuthorizationServerSettings settings,
        SharedLoginNavigation navigation
    ) {
        super(authenticationManager(decoder, subjects));
        var html = new org.springframework.security.web.util.matcher.MediaTypeRequestMatcher(
            org.springframework.http.MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(java.util.Set.of(org.springframework.http.MediaType.ALL));
        var bearerError = new org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint();
        setAuthenticationEntryPoint((request, response, error) -> {
            if (html.matches(request)) navigation.commence(request, response, error);
            else bearerError.commence(request, response, error);
        });
        var bearer = new DefaultBearerTokenResolver();
        setBearerTokenResolver(request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!settings.getAuthorizationEndpoint().equals(path)) return null;
            // 显式请求头优先，无效时由框架拒绝，不能回退到 Cookie。
            if (request.getHeader("Authorization") != null) return bearer.resolve(request);
            var cookie = WebUtils.getCookie(request, SharedTokenCookies.ACCESS);
            return cookie != null && StringUtils.hasText(cookie.getValue()) ? cookie.getValue() : null;
        });
        // 不另存 Session，后续授权请求仍需检查原内部登录是否有效。
        setSecurityContextRepository(new NullSecurityContextRepository());
    }

    private static AuthenticationManager authenticationManager(
        JwtDecoder decoder, InternalLoginAuthenticator subjects
    ) {
        var provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(subjects::authenticateInternalLogin);
        return provider::authenticate;
    }
}
