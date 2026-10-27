package baosys.manager.infra.database.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("permission_change_audit")
public class PermissionChangeAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorUserId;

    private Long targetUserId;

    private String beforePermissions;

    private String afterPermissions;

    private boolean beforeEnabled;

    private boolean afterEnabled;

    private String traceId;

    private OffsetDateTime occurredAt;

    public Long getId() {
        return id;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getBeforePermissions() {
        return beforePermissions;
    }

    public void setBeforePermissions(String beforePermissions) {
        this.beforePermissions = beforePermissions;
    }

    public String getAfterPermissions() {
        return afterPermissions;
    }

    public void setAfterPermissions(String afterPermissions) {
        this.afterPermissions = afterPermissions;
    }

    public boolean isBeforeEnabled() {
        return beforeEnabled;
    }

    public void setBeforeEnabled(boolean beforeEnabled) {
        this.beforeEnabled = beforeEnabled;
    }

    public boolean isAfterEnabled() {
        return afterEnabled;
    }

    public void setAfterEnabled(boolean afterEnabled) {
        this.afterEnabled = afterEnabled;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
