package baosys.manager.infra.security;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 浏览器可访问的公开来源配置，用于校验共享登录的返回地址。
 * SharedLoginProperties
 * @param managerOrigin
 * @param allowedOrigins
 * @param secureCookie
 */
@ConfigurationProperties("baosys.shared-login")
public record SharedLoginProperties(
    String managerOrigin,
    List<String> allowedOrigins,
    boolean secureCookie
) {
    public SharedLoginProperties {
        managerOrigin = managerOrigin == null ? "http://localhost:8080" : managerOrigin;
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        URI manager = origin(managerOrigin);
        if ("https".equals(manager.getScheme()) && !secureCookie) {
            throw new IllegalArgumentException("HTTPS shared login requires secure-cookie=true");
        }
        for (String value : allowedOrigins) {
            URI candidate = origin(value);
            if (
                !manager.getHost().equalsIgnoreCase(candidate.getHost()) ||
                !manager.getScheme().equals(candidate.getScheme())
            ) {
                throw new IllegalArgumentException(
                    "Shared login origins must use the Manager host and scheme"
                );
            }
        }
    }

    /**
     * 配置项必须是纯来源地址，不接受路径、用户信息、查询参数或片段。
     * @param value
     * @return
     */
    private static URI origin(String value) {
        URI uri = URI.create(value);
        if (
            !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) ||
            uri.getHost() == null ||
            uri.getUserInfo() != null ||
            uri.getRawQuery() != null ||
            uri.getRawFragment() != null ||
            (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
        ) {
            throw new IllegalArgumentException("Expected an HTTP(S) origin without path: " + value);
        }
        return uri;
    }

    /**
     * 校验登录返回地址，限制在同协议、同主机且显式允许的来源内。
     * @param value
     * @return
     */
    public String returnUrl(String value) {
        if (value == null || value.isBlank()) {
            return managerOrigin + "/users-management";
        }
        try {
            URI uri = URI.create(value);
            URI base = URI.create(managerOrigin);
            if (!uri.isAbsolute()) {
                uri = base.resolve(uri);
            }
            // 同时排除认证/API 路径及编码后的换行、反斜杠，防止跳转到不合适的入口。
            String targetOrigin = uri.getScheme() + "://" + uri.getRawAuthority();
            if (
                uri.getUserInfo() != null ||
                !base.getHost().equalsIgnoreCase(uri.getHost()) ||
                !base.getScheme().equals(uri.getScheme()) ||
                !(managerOrigin.equals(targetOrigin) || allowedOrigins.contains(targetOrigin)) ||
                uri.getRawPath().contains("\\") ||
                uri.getRawPath().matches("(?i).*(%0[ad]|%5c).*") ||
                uri.getPath().equals("/login") ||
                uri.getPath().contains("/oauth2/") ||
                uri.getPath().startsWith("/login/") ||
                uri.getPath().startsWith("/api/")
            ) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (RuntimeException invalid) {
            throw new baosys.manager.app.error.BusinessException(
                "AUTH_400_REDIRECT",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "不允许的登录返回地址"
            );
        }
    }
}
