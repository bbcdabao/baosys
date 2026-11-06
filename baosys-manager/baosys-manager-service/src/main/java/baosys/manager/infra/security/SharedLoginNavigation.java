package baosys.manager.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 共用 Vue 登录页，区分子系统页面回跳与精确配置的 OAuth2 授权端点。
 */
@Component
public class SharedLoginNavigation implements AuthenticationEntryPoint {
    private final SharedLoginProperties properties;
    private final SubsystemNavigation subsystems;
    private final URI authorizationUri;
    private final String authorizationPath;

    public SharedLoginNavigation(SharedLoginProperties properties, AuthorizationServerSettings settings, SubsystemNavigation subsystems) {
        this.properties = properties;
        this.subsystems = subsystems;
        this.authorizationPath = settings.getAuthorizationEndpoint();
        this.authorizationUri = URI.create(settings.getIssuer() + authorizationPath);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
        throws IOException {
        String target = subsystems.authorizedSystemsUrl();
        if (authorizationPath.equals(request.getRequestURI().substring(request.getContextPath().length()))) {
            var query = request.getParameterMap().entrySet().stream().flatMap(entry ->
                java.util.Arrays.stream(entry.getValue()).map(value ->
                    java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")
                    + "=" + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")))
                .collect(java.util.stream.Collectors.joining("&"));
            target = authorizationUri + "?" + query;
        }
        response.sendRedirect(UriComponentsBuilder.fromUriString(properties.managerOrigin() + "/login")
            .queryParam("redirect_url", "{target}").encode().buildAndExpand(target).toUriString());
    }

    public String destination(String target, String defaultSystem, java.util.Set<String> permissions) {
        target = returnUrl(target);
        if (target != null && isAuthorizationReturn(target)) return target;
        return subsystems.destination(target, defaultSystem, permissions);
    }

    private boolean isAuthorizationReturn(String value) {
        return value.startsWith(authorizationUri.toASCIIString() + "?") || value.equals(authorizationUri.toASCIIString());
    }

    public String returnUrl(String value) {
        if (value != null) {
            try {
                var uri = URI.create(value);
                var manager = URI.create(properties.managerOrigin());
                if (uri.getUserInfo() == null && uri.getRawFragment() == null
                    && authorizationUri.getScheme().equals(uri.getScheme())
                    && authorizationUri.getRawAuthority().equals(uri.getRawAuthority())
                    && authorizationUri.getRawPath().equals(uri.getRawPath())
                    && manager.getScheme().equals(uri.getScheme())
                    && manager.getHost().equalsIgnoreCase(uri.getHost())) {
                    return uri.toASCIIString();
                }
            } catch (IllegalArgumentException ignored) {
                // 由共用返回地址校验统一输出业务错误。
            }
        }
        return subsystems.validateReturnUrl(value);
    }
}
