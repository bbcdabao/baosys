-- PostgreSQL database-side scheduler for user_login cleanup.
-- Prerequisite: pg_cron must be installed and enabled by the database administrator.
-- Run this script in the application database after Flyway has created
-- cleanup_inactive_user_login_sessions().

CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.unschedule(jobid)
  FROM cron.job
 WHERE jobname = 'user_login_cleanup_daily';

SELECT cron.schedule(
    'user_login_cleanup_daily',
    '0 0 * * *',
    'SELECT public.cleanup_inactive_user_login_sessions();'
);
