-- Flyway migration draft for Car Watcher
-- Target database: SQLite
-- Notes:
-- 1) Store timestamps as UTC ISO-8601 text.
-- 2) `updated_at` should be maintained by the application service layer.
-- 3) Runtime PRAGMAs such as WAL / busy_timeout should be configured at startup,
--    not embedded here as permanent migration behavior.

CREATE TABLE car (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL,
    license_plate     TEXT    NOT NULL,
    registration_date TEXT    NOT NULL,
    vin               TEXT    NOT NULL,
    active            INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    version           INTEGER NOT NULL DEFAULT 0,
    created_at        TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_car_license_plate UNIQUE (license_plate),
    CONSTRAINT uk_car_vin UNIQUE (vin)
);

CREATE TABLE insurance_policy (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id                   INTEGER NOT NULL,
    policy_type              TEXT    NOT NULL CHECK (policy_type IN ('PZP', 'COLLISION')),
    enabled                  INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    insurer_name             TEXT,
    policy_number            TEXT,
    expiry_date              TEXT,
    status                   TEXT    NOT NULL DEFAULT 'UNKNOWN'
                                   CHECK (status IN ('VALID', 'EXPIRING', 'EXPIRED', 'UNKNOWN', 'ERROR')),
    check_mode               TEXT    NOT NULL DEFAULT 'MANUAL'
                                   CHECK (check_mode IN ('ONLINE', 'MANUAL')),
    warning_days_before      INTEGER NOT NULL DEFAULT 14 CHECK (warning_days_before >= 0),
    last_checked_at          TEXT,
    last_successful_check_at TEXT,
    notes                    TEXT,
    version                  INTEGER NOT NULL DEFAULT 0,
    created_at               TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_insurance_policy_car
        FOREIGN KEY (car_id) REFERENCES car(id) ON DELETE CASCADE,
    CONSTRAINT uk_insurance_policy_car_type UNIQUE (car_id, policy_type)
);

CREATE TABLE car_vignette_selection (
    car_id      INTEGER NOT NULL,
    country     TEXT    NOT NULL CHECK (country IN ('SK', 'CZ', 'AT')),
    enabled     INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (car_id, country),
    CONSTRAINT fk_car_vignette_selection_car
        FOREIGN KEY (car_id) REFERENCES car(id) ON DELETE CASCADE
);

CREATE TABLE obligation_state (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id                   INTEGER NOT NULL,
    obligation_type          TEXT    NOT NULL
                                   CHECK (obligation_type IN (
                                       'PZP',
                                       'COLLISION',
                                       'STK',
                                       'EK',
                                       'VIGNETTE_SK',
                                       'VIGNETTE_CZ',
                                       'VIGNETTE_AT'
                                   )),
    expiry_date              TEXT,
    status                   TEXT    NOT NULL DEFAULT 'UNKNOWN'
                                   CHECK (status IN ('VALID', 'EXPIRING', 'EXPIRED', 'UNKNOWN', 'ERROR')),
    last_checked_at          TEXT,
    last_successful_check_at TEXT,
    source_system            TEXT,
    details_json             TEXT,
    manual_override          INTEGER NOT NULL DEFAULT 0 CHECK (manual_override IN (0, 1)),
    created_at               TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_obligation_state_car
        FOREIGN KEY (car_id) REFERENCES car(id) ON DELETE CASCADE,
    CONSTRAINT uk_obligation_state_car_type UNIQUE (car_id, obligation_type)
);

CREATE TABLE check_schedule (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id               INTEGER,
    insurance_policy_id  INTEGER,
    check_type           TEXT    NOT NULL
                                CHECK (check_type IN (
                                    'PZP_CHECK',
                                    'COLLISION_INSURANCE_CHECK',
                                    'STK_CHECK',
                                    'EK_CHECK',
                                    'VIGNETTE_SK_CHECK',
                                    'VIGNETTE_CZ_CHECK',
                                    'VIGNETTE_AT_CHECK',
                                    'DAILY_SUMMARY_EMAIL',
                                    'DAILY_REMINDER_SCAN'
                                )),
    cron_expression      TEXT    NOT NULL,
    zone_id              TEXT    NOT NULL DEFAULT 'Europe/Bratislava',
    enabled              INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    warning_days_before  INTEGER NOT NULL DEFAULT 0 CHECK (warning_days_before >= 0),
    next_run_at          TEXT,
    lock_until           TEXT,
    lock_owner           TEXT,
    version              INTEGER NOT NULL DEFAULT 0,
    created_at           TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_check_schedule_car
        FOREIGN KEY (car_id) REFERENCES car(id) ON DELETE CASCADE,
    CONSTRAINT fk_check_schedule_insurance_policy
        FOREIGN KEY (insurance_policy_id) REFERENCES insurance_policy(id) ON DELETE CASCADE,
    CONSTRAINT ck_check_schedule_target_presence CHECK (
        (car_id IS NOT NULL) OR (insurance_policy_id IS NOT NULL) OR
        (check_type IN ('DAILY_SUMMARY_EMAIL', 'DAILY_REMINDER_SCAN'))
    )
);

CREATE TABLE check_run_log (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id          INTEGER NOT NULL,
    car_id               INTEGER,
    insurance_policy_id  INTEGER,
    check_type           TEXT    NOT NULL,
    started_at           TEXT    NOT NULL,
    finished_at          TEXT,
    status               TEXT    NOT NULL CHECK (status IN ('SUCCESS', 'WARNING', 'ERROR', 'NO_DATA')),
    message              TEXT,
    expiry_date_found    TEXT,
    findings_json        TEXT,
    duration_ms          INTEGER CHECK (duration_ms IS NULL OR duration_ms >= 0),
    trace_id             TEXT,
    CONSTRAINT fk_check_run_log_schedule
        FOREIGN KEY (schedule_id) REFERENCES check_schedule(id) ON DELETE CASCADE,
    CONSTRAINT fk_check_run_log_car
        FOREIGN KEY (car_id) REFERENCES car(id) ON DELETE SET NULL,
    CONSTRAINT fk_check_run_log_insurance_policy
        FOREIGN KEY (insurance_policy_id) REFERENCES insurance_policy(id) ON DELETE SET NULL
);

CREATE TABLE notification_log (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id               INTEGER,
    insurance_policy_id  INTEGER,
    notification_type    TEXT    NOT NULL
                                CHECK (notification_type IN (
                                    'EXPIRY_WARNING',
                                    'EXPIRED',
                                    'DAILY_SUMMARY',
                                    'CHECK_FAILURE'
                                )),
    channel              TEXT    NOT NULL CHECK (channel IN ('EMAIL')),
    subject              TEXT    NOT NULL,
    recipient            TEXT    NOT NULL,
    sent_at              TEXT,
    status               TEXT    NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    provider_message_id  TEXT,
    check_run_id         INTEGER,
    CONSTRAINT fk_notification_log_car
        FOREIGN KEY (car_id) REFERENCES car(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_log_insurance_policy
        FOREIGN KEY (insurance_policy_id) REFERENCES insurance_policy(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_log_check_run
        FOREIGN KEY (check_run_id) REFERENCES check_run_log(id) ON DELETE SET NULL
);

CREATE TABLE app_setting (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key   TEXT NOT NULL,
    setting_value TEXT NOT NULL,
    updated_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_setting_key UNIQUE (setting_key)
);

-- Scheduler uniqueness: one schedule per target and check type.
CREATE UNIQUE INDEX uk_check_schedule_global_check_type
    ON check_schedule(check_type)
    WHERE car_id IS NULL
      AND insurance_policy_id IS NULL;

CREATE UNIQUE INDEX uk_check_schedule_car_check_type
    ON check_schedule(car_id, check_type)
    WHERE car_id IS NOT NULL
      AND insurance_policy_id IS NULL;

CREATE UNIQUE INDEX uk_check_schedule_policy_check_type
    ON check_schedule(insurance_policy_id, check_type)
    WHERE insurance_policy_id IS NOT NULL;

-- Query and reporting indexes.
CREATE INDEX idx_insurance_policy_car_id
    ON insurance_policy(car_id);

CREATE INDEX idx_obligation_state_car_id
    ON obligation_state(car_id);

CREATE INDEX idx_obligation_state_expiry_date
    ON obligation_state(expiry_date);

CREATE INDEX idx_check_schedule_due
    ON check_schedule(enabled, next_run_at);

CREATE INDEX idx_check_schedule_lock_until
    ON check_schedule(lock_until);

CREATE INDEX idx_check_run_log_schedule_started_at
    ON check_run_log(schedule_id, started_at DESC);

CREATE INDEX idx_check_run_log_car_started_at
    ON check_run_log(car_id, started_at DESC);

CREATE INDEX idx_check_run_log_status_started_at
    ON check_run_log(status, started_at DESC);

CREATE INDEX idx_notification_log_sent_at
    ON notification_log(sent_at DESC);

CREATE INDEX idx_notification_log_status_sent_at
    ON notification_log(status, sent_at DESC);

-- Seed baseline application settings.
INSERT INTO app_setting (setting_key, setting_value)
VALUES
    ('app.default-locale', 'sk'),
    ('app.default-zone-id', 'Europe/Bratislava'),
    ('app.summary.cron', '0 0 0 * * *'),
    ('app.reminder.cron', '0 0 6 * * *'),
    ('app.email.recipients', ''),
    ('app.warning-days.pzp', '14'),
    ('app.warning-days.collision', '14'),
    ('app.warning-days.stk', '30'),
    ('app.warning-days.ek', '30'),
    ('app.warning-days.vignette', '7');
