# AGENTS.md

## What this codebase is
- `Car-checker` is a modular monolith (Spring Boot 4, Java 25) split into 5 Maven modules and packaged by `carwatch-boot`.
- Runtime target is low-footprint local deployment (SQLite + file logs + systemd scripts in `ops/`).
- Core app behavior is schedule-driven: checks run, outcomes are logged, and obligation states / notifications are updated.

## Module boundaries (follow these)
- `carwatch-domain`: entities, enums, repository interfaces only (example: `carwatch-domain/src/main/java/com/carwatch/domain/schedule/CheckScheduleRepository.java`).
- `carwatch-application`: use-case orchestration and scheduling logic (example: `carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleDispatcher.java`).
- `carwatch-infrastructure`: Spring Data JDBC repos, adapters, SQLite/Flyway/mail/config (example: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/persistence/CarRepositoryAdapter.java`).
- `carwatch-web`: MVC controllers + Thymeleaf + forms + i18n bundles (example: `carwatch-web/src/main/java/com/carwatch/web/controller/CarController.java`).
- `carwatch-boot`: app entrypoint + profile config + logback (`carwatch-boot/src/main/resources/application*.yml`).

## Key runtime flows you should understand first
- Car creation (`CarManagementService#createCar`) seeds multiple tables at once: car, default insurance policies, schedules, obligation states, and vignette selections.
- Scheduler loop (`ScheduleDispatcher#pollAndExecute`) pulls due schedules, claims lock (`ScheduleClaimService`), executes provider (`CheckExecutor`), logs run, updates obligation, computes next run.
- Provider resolution is by `CheckType` map; add new check behavior by adding a `VehicleCheckProvider` bean and wiring in `ProviderConfiguration` when needed.
- Daily reminder/summary are implemented as schedule providers (`ScheduledWorkflowProvider`) and read recipients/locale from `app_setting`.

## Persistence and integration points
- SQLite schema + constraints/indexes are defined in `carwatch-infrastructure/src/main/resources/db/migration/V1__init.sql` (Flyway is enabled in all profiles).
- SQLite PRAGMAs (`WAL`, `foreign_keys`, `busy_timeout`) are applied at startup in `SqliteConfig`.
- Spring Data JDBC SQLite dialect is custom (`SqliteDialectProvider`, `SqliteDialect`) and registered via `META-INF/spring.factories`.
- Email is an adapter boundary: `EmailSender` (application) -> `SpringMailEmailSender` (infrastructure); daily summary HTML templates are in `carwatch-infrastructure/src/main/resources/templates/email/`.

## Web and i18n conventions
- This is server-rendered MVC, not REST-first. Controllers return template names (`cars/form`, `schedules/list`, etc.).
- Slovak is default UI locale (`WebConfig` sets `sk`), switch via `?lang=sk|en`.
- Keep backend/log identifiers in English; put user-facing text in `messages_sk.properties` / `messages_en.properties`.
- Form validation lives in `carwatch-web/src/main/java/com/carwatch/web/form/*` and uses message keys (e.g. `{validation.required}`).

## Build/test/debug workflows
```bash
mvn clean verify
mvn -pl carwatch-application test
mvn -pl carwatch-boot spring-boot:run -Dspring-boot.run.profiles=prod
```
- Aggregate JaCoCo report is generated at `target/site/jacoco-aggregate/index.html`.
- Local logs are file-based: `carwatch-boot/logs/app.log` and `carwatch-boot/logs/error.log`; logback config is in `carwatch-boot/src/main/resources/logback-spring.xml`.
- Linux/Raspberry Pi ops artifacts: `ops/run-prod.sh`, `ops/carwatch.service`, `ops/backup-db.sh`.

## Change rules for AI agents
- Keep dependency direction strict: web/infrastructure -> application -> domain (never reverse).
- Do not introduce JPA/Hibernate; this repo uses Spring Data JDBC + SQLite.
- When changing schedule behavior, update both orchestration (`carwatch-application/.../schedule`) and DB constraints/index assumptions in `V1__init.sql`.
- Preserve optimistic-locking semantics (`version` fields) used by update flows like `CarController` + `CarManagementService#updateCar`.
- Add/adjust focused tests near changed logic (see patterns in `carwatch-application/src/test/java/com/carwatch/application`).

