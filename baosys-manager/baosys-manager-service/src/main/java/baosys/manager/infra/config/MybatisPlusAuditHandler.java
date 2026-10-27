package baosys.manager.infra.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Component
public class MybatisPlusAuditHandler implements MetaObjectHandler {

    private static final String SYSTEM_USER = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", String.class, SYSTEM_USER);
        String createdBy = (String) getFieldValByName("createdBy", metaObject);
        strictInsertFill(
            metaObject,
            "updatedBy",
            String.class,
            createdBy == null ? SYSTEM_USER : createdBy
        );
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", OffsetDateTime.now(ZoneOffset.UTC), metaObject);
        if (getFieldValByName("updatedBy", metaObject) == null) {
            setFieldValByName("updatedBy", SYSTEM_USER, metaObject);
        }
    }
}
