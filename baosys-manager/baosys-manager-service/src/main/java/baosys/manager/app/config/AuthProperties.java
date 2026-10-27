package baosys.manager.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 仅配置业务初始化参数；OAuth2 客户端和令牌使用 Spring Boot 配置。 */
@ConfigurationProperties("auth")
public class AuthProperties {

    private boolean defaultAdminEnabled;
    private String defaultAdminUsername;
    private String defaultAdminPassword;

    public boolean isDefaultAdminEnabled() {
        return defaultAdminEnabled;
    }

    public void setDefaultAdminEnabled(boolean value) {
        defaultAdminEnabled = value;
    }

    public String getDefaultAdminUsername() {
        return defaultAdminUsername;
    }

    public void setDefaultAdminUsername(String value) {
        defaultAdminUsername = value;
    }

    public String getDefaultAdminPassword() {
        return defaultAdminPassword;
    }

    public void setDefaultAdminPassword(String value) {
        defaultAdminPassword = value;
    }
}
