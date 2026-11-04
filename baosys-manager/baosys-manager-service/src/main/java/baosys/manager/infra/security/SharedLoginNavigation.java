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
 * 共用 Vue 登录页，登录成功只允许恢复到配置中的授权端点。
 */
@Component
public class SharedLoginNavigation implements AuthenticationEntryPoint {
    private final SharedLoginProperties properties;
    private final URI authorizationUri;
    private final String authorizationPath;

    public SharedLoginNavigation(SharedLoginProperties properties, AuthorizationServerSettings settings) {
        this.properties = properties;
        this.authorizationPath = settings.getAuthorizationEndpoint();
        this.authorizationUri = URI.create(settings.getIssuer() + authorizationPath);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
        throws IOException {
        String target = properties.returnUrl(null);
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
        return properties.returnUrl(value);
    }
}
