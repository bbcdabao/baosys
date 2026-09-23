package baosys.manager.app.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("auth")
public class AuthProperties {

    private boolean cookieSecure;
    @Min(0)
    private long accessTokenCookieMaxAgeSeconds = 1800;
    @Min(0)
    private long refreshTokenCookieMaxAgeSeconds = 2592000;
    private String refreshTokenCookiePath =
            "/api/baosys-manager/v1/auth";
    private boolean defaultAdminEnabled;
    private String defaultAdminUsername;
    private String defaultAdminPassword;
    private int refreshTokenExpireDays = 30;

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public long getAccessTokenCookieMaxAgeSeconds() {
        return accessTokenCookieMaxAgeSeconds;
    }

    public void setAccessTokenCookieMaxAgeSeconds(
            long accessTokenCookieMaxAgeSeconds) {
        this.accessTokenCookieMaxAgeSeconds = accessTokenCookieMaxAgeSeconds;
    }

    public long getRefreshTokenCookieMaxAgeSeconds() {
        return refreshTokenCookieMaxAgeSeconds;
    }

    public void setRefreshTokenCookieMaxAgeSeconds(
            long refreshTokenCookieMaxAgeSeconds) {
        this.refreshTokenCookieMaxAgeSeconds = refreshTokenCookieMaxAgeSeconds;
    }

    public String getRefreshTokenCookiePath() {
        return refreshTokenCookiePath;
    }

    public void setRefreshTokenCookiePath(String refreshTokenCookiePath) {
        this.refreshTokenCookiePath = refreshTokenCookiePath;
    }

    public boolean isDefaultAdminEnabled() {
        return defaultAdminEnabled;
    }

    public void setDefaultAdminEnabled(boolean defaultAdminEnabled) {
        this.defaultAdminEnabled = defaultAdminEnabled;
    }

    public String getDefaultAdminUsername() {
        return defaultAdminUsername;
    }

    public void setDefaultAdminUsername(String defaultAdminUsername) {
        this.defaultAdminUsername = defaultAdminUsername;
    }

    public String getDefaultAdminPassword() {
        return defaultAdminPassword;
    }

    public void setDefaultAdminPassword(String defaultAdminPassword) {
        this.defaultAdminPassword = defaultAdminPassword;
    }

    public int getRefreshTokenExpireDays() {
        return refreshTokenExpireDays;
    }

    public void setRefreshTokenExpireDays(int refreshTokenExpireDays) {
        this.refreshTokenExpireDays = refreshTokenExpireDays;
    }
}
