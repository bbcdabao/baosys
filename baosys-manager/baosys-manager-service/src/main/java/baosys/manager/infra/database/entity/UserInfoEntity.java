package baosys.manager.infra.database.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("user_info")
public class UserInfoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userName;

    private String userPass;

    private String userPermissions;

    private String defaultSystemType;

    public String getDefaultSystemType() {
        return defaultSystemType;
    }

    public void setDefaultSystemType(String defaultSystemType) {
        this.defaultSystemType = defaultSystemType;
    }

    private boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPass() {
        return userPass;
    }

    public void setUserPass(String userPass) {
        this.userPass = userPass;
    }

    public String getUserPermissions() {
        return userPermissions;
    }

    public void setUserPermissions(String userPermissions) {
        this.userPermissions = userPermissions;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
