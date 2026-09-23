-- PostgreSQL database-side scheduler for user_login cleanup.
-- Prerequisite: pg_cron must be installed and enabled by the database administrator.
-- Run this script in the application database after Flyway has created
-- cleanup_inactive_user_login_sessions(). The function schema is discovered instead
-- of assuming public, because Manager uses its own configurable schema.

CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.unschedule(jobid)
  FROM cron.job
 WHERE jobname = 'user_login_cleanup_daily';

DO $$
DECLARE
    cleanup_schema text;
BEGIN
    SELECT n.nspname
      INTO cleanup_schema
      FROM pg_proc AS p
      JOIN pg_namespace AS n ON n.oid = p.pronamespace
     WHERE p.proname = 'cleanup_inactive_user_login_sessions'
       AND pg_get_function_identity_arguments(p.oid) = ''
     ORDER BY (n.nspname = current_schema()) DESC,
              n.nspname
     LIMIT 1;

    IF cleanup_schema IS NULL THEN
        RAISE EXCEPTION 'cleanup_inactive_user_login_sessions() was not found';
    END IF;

    PERFORM cron.schedule(
        'user_login_cleanup_daily',
        '0 0 * * *',
        format(
            'SELECT %I.cleanup_inactive_user_login_sessions();',
            cleanup_schema));
END;
$$;
