-- Fail before changing data if the old per-system usernames are ambiguous.
DO $$
DECLARE conflicts TEXT;
BEGIN
    SELECT string_agg(user_name, ', ' ORDER BY user_name) INTO conflicts
      FROM (SELECT btrim(user_name) AS user_name FROM user_info
             GROUP BY btrim(user_name) HAVING count(*) > 1) duplicates;
    IF conflicts IS NOT NULL THEN
        RAISE EXCEPTION 'Resolve duplicate usernames before multi-system migration: %', conflicts;
    END IF;
    IF EXISTS (SELECT 1 FROM user_info WHERE user_permissions ~ '(^|,)\s*(system:|SCOPE_)') THEN
        RAISE EXCEPTION 'Existing permissions use reserved system:/SCOPE_ prefixes; provide an explicit migration mapping';
    END IF;
END $$;

ALTER TABLE user_info ALTER COLUMN user_permissions TYPE TEXT;
ALTER TABLE permission_change_audit ALTER COLUMN before_permissions TYPE TEXT;
ALTER TABLE permission_change_audit ALTER COLUMN after_permissions TYPE TEXT;

UPDATE user_info u SET user_name = btrim(user_name), user_permissions = (
    SELECT string_agg(code, ',' ORDER BY code)
      FROM (SELECT DISTINCT btrim(code) AS code
              FROM unnest(string_to_array(u.user_permissions, ',') || ARRAY['system:' || u.system_type]) code
             WHERE btrim(code) <> '') grants
);

ALTER TABLE user_info DROP CONSTRAINT uk_user_info_user_name_system_type;
ALTER TABLE user_info DROP COLUMN system_type;
ALTER TABLE user_info ADD CONSTRAINT uk_user_info_user_name UNIQUE (user_name);
COMMENT ON COLUMN user_info.user_permissions IS 'Sorted comma-separated system entry and globally unique API permissions';

-- Old access-token subjects used user IDs; old refresh sessions must not survive this cutover.
DELETE FROM user_login;
ALTER TABLE user_login ADD COLUMN client_id VARCHAR(128) NOT NULL;
COMMENT ON COLUMN user_login.client_id IS 'Login client identity, independent of authorized subsystems';
