package baosys.manager.infra.database.entity;

import java.time.OffsetDateTime;

public class LoginSessionRow {

    private Long id;
    private Long userId;
    private String userName;
    private String systemType;
    private String loginClient;
    private OffsetDateTime loginAt;
    private OffsetDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getSystemType() { return systemType; }
    public void setSystemType(String systemType) { this.systemType = systemType; }
    public String getLoginClient() { return loginClient; }
    public void setLoginClient(String loginClient) { this.loginClient = loginClient; }
    public OffsetDateTime getLoginAt() { return loginAt; }
    public void setLoginAt(OffsetDateTime loginAt) { this.loginAt = loginAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
