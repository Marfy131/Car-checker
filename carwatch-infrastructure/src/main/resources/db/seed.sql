INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.default-locale', 'sk'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.default-locale');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.default-zone-id', 'Europe/Bratislava'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.default-zone-id');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.summary.cron', '0 0 0 * * *'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.summary.cron');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.reminder.cron', '0 0 6 * * *'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.reminder.cron');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.email.recipients', ''
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.email.recipients');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.warning-days.pzp', '14'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.warning-days.pzp');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.warning-days.collision', '14'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.warning-days.collision');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.warning-days.stk', '30'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.warning-days.stk');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.warning-days.ek', '30'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.warning-days.ek');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'app.warning-days.vignette', '7'
WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'app.warning-days.vignette');

INSERT INTO check_schedule (
    car_id, insurance_policy_id, check_type, cron_expression, zone_id,
    enabled, warning_days_before, next_run_at, lock_until, lock_owner, version
)
SELECT
    NULL, NULL, 'DAILY_SUMMARY_EMAIL',
    COALESCE((SELECT setting_value FROM app_setting WHERE setting_key = 'app.summary.cron' LIMIT 1), '0 0 0 * * *'),
    'Europe/Bratislava', 1, 0, NULL, NULL, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM check_schedule
    WHERE car_id IS NULL AND insurance_policy_id IS NULL AND check_type = 'DAILY_SUMMARY_EMAIL'
);

INSERT INTO check_schedule (
    car_id, insurance_policy_id, check_type, cron_expression, zone_id,
    enabled, warning_days_before, next_run_at, lock_until, lock_owner, version
)
SELECT
    NULL, NULL, 'DAILY_REMINDER_SCAN',
    COALESCE((SELECT setting_value FROM app_setting WHERE setting_key = 'app.reminder.cron' LIMIT 1), '0 0 6 * * *'),
    'Europe/Bratislava', 1, 0, NULL, NULL, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM check_schedule
    WHERE car_id IS NULL AND insurance_policy_id IS NULL AND check_type = 'DAILY_REMINDER_SCAN'
);
