package baosys.manager.infra.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 浏览器可访问的公开来源配置，用于校验共享登录的返回地址。
 * SharedLoginProperties
 * @param managerOrigin
 */
@ConfigurationProperties("baosys.shared-login")
public record SharedLoginProperties(
    String managerOrigin
) {
    public SharedLoginProperties {
        managerOrigin = managerOrigin == null ? "http://localhost:8080" : managerOrigin;
        origin(managerOrigin);
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

}
