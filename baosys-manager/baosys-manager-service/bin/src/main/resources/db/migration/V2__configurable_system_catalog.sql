-- 系统及权限白名单由启动时加载的目录校验；保留账号所属系统和唯一约束。
ALTER TABLE user_info DROP CONSTRAINT ck_user_info_system_type;
ALTER TABLE user_info ADD CONSTRAINT ck_user_info_system_type_format
    CHECK (system_type ~ '^[A-Z][A-Z0-9_]{0,31}$');
COMMENT ON COLUMN user_info.system_type IS '所属子系统标识，对应 baosys-systems.yml 中的 system-type';
