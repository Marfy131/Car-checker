INSERT INTO check_schedule (
    car_id,
    insurance_policy_id,
    check_type,
    cron_expression,
    zone_id,
    enabled,
    warning_days_before,
    next_run_at,
    lock_until,
    lock_owner,
    version
)
SELECT
    NULL,
    NULL,
    'DAILY_SUMMARY_EMAIL',
    COALESCE(
        (SELECT setting_value FROM app_setting WHERE setting_key = 'app.summary.cron' LIMIT 1),
        '0 0 0 * * *'
    ),
    'Europe/Bratislava',
    1,
    0,
    NULL,
    NULL,
    NULL,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM check_schedule
    WHERE car_id IS NULL
      AND insurance_policy_id IS NULL
      AND check_type = 'DAILY_SUMMARY_EMAIL'
);

INSERT INTO check_schedule (
    car_id,
    insurance_policy_id,
    check_type,
    cron_expression,
    zone_id,
    enabled,
    warning_days_before,
    next_run_at,
    lock_until,
    lock_owner,
    version
)
SELECT
    NULL,
    NULL,
    'DAILY_REMINDER_SCAN',
    COALESCE(
        (SELECT setting_value FROM app_setting WHERE setting_key = 'app.reminder.cron' LIMIT 1),
        '0 0 6 * * *'
    ),
    'Europe/Bratislava',
    1,
    0,
    NULL,
    NULL,
    NULL,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM check_schedule
    WHERE car_id IS NULL
      AND insurance_policy_id IS NULL
      AND check_type = 'DAILY_REMINDER_SCAN'
);
