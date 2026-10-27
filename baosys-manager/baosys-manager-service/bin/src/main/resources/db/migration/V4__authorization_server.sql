-- Spring Authorization Server 1.5 JDBC schema, PostgreSQL text/timestamptz variants.
CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes text DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value text DEFAULT NULL,
    authorization_code_issued_at timestamptz DEFAULT NULL,
    authorization_code_expires_at timestamptz DEFAULT NULL,
    authorization_code_metadata text DEFAULT NULL,
    access_token_value text DEFAULT NULL,
    access_token_issued_at timestamptz DEFAULT NULL,
    access_token_expires_at timestamptz DEFAULT NULL,
    access_token_metadata text DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,
    oidc_id_token_value text DEFAULT NULL,
    oidc_id_token_issued_at timestamptz DEFAULT NULL,
    oidc_id_token_expires_at timestamptz DEFAULT NULL,
    oidc_id_token_metadata text DEFAULT NULL,
    refresh_token_value text DEFAULT NULL,
    refresh_token_issued_at timestamptz DEFAULT NULL,
    refresh_token_expires_at timestamptz DEFAULT NULL,
    refresh_token_metadata text DEFAULT NULL,
    user_code_value text DEFAULT NULL,
    user_code_issued_at timestamptz DEFAULT NULL,
    user_code_expires_at timestamptz DEFAULT NULL,
    user_code_metadata text DEFAULT NULL,
    device_code_value text DEFAULT NULL,
    device_code_issued_at timestamptz DEFAULT NULL,
    device_code_expires_at timestamptz DEFAULT NULL,
    device_code_metadata text DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- Cutover requires reauthentication; legacy refresh tokens are not OAuth2 grants.
DELETE FROM user_login;
ALTER TABLE user_login DROP COLUMN refresh_token_hash;
ALTER TABLE user_login ADD COLUMN authorization_id VARCHAR(100) NOT NULL UNIQUE
    REFERENCES oauth2_authorization(id) ON DELETE CASCADE;
ALTER TABLE user_login ADD COLUMN authentication_session_id VARCHAR(36);
CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization(principal_name);

CREATE FUNCTION revoke_login_authorization() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$
BEGIN
    DELETE FROM oauth2_authorization WHERE id=OLD.authorization_id;
    DELETE FROM spring_session WHERE session_id=OLD.authentication_session_id;
    RETURN OLD;
END;
$$;
CREATE TRIGGER revoke_login_authorization AFTER DELETE ON user_login
FOR EACH ROW EXECUTE FUNCTION revoke_login_authorization();

CREATE FUNCTION revoke_changed_account_authorizations() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$
BEGIN
    IF TG_OP='DELETE' OR OLD.user_name IS DISTINCT FROM NEW.user_name
        OR OLD.user_pass IS DISTINCT FROM NEW.user_pass
        OR OLD.enabled IS DISTINCT FROM NEW.enabled
        OR OLD.user_permissions IS DISTINCT FROM NEW.user_permissions THEN
        DELETE FROM oauth2_authorization WHERE principal_name=OLD.id::text;
        DELETE FROM spring_session WHERE principal_name=OLD.id::text;
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER revoke_changed_account_authorizations AFTER UPDATE OR DELETE ON user_info
FOR EACH ROW EXECUTE FUNCTION revoke_changed_account_authorizations();

