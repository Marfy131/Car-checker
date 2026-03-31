# Car Watcher

Car Watcher is a low-memory Spring Boot application for Raspberry Pi 3 that tracks vehicle-related obligations and notifies users by email.

The project is designed for a **local network**, **very low concurrency**, and **small operational footprint**. It focuses on:
- car management
- insurance tracking
- STK / EK tracking
- vignette tracking for SK / CZ / AT
- scheduled checks
- email reminders and daily summaries
- local admin dashboard in **Slovak** with **English** switch
- monitoring with **JavaMelody**

## Status

This repository currently contains the initial planning artifacts:
- `plan.md` – implementation plan
- `V1__init.sql` – first Flyway migration draft for SQLite

## Runtime profiles and operations artifacts

- `application-prod.yml` provides production-oriented defaults with environment-variable overrides.
- `ops/carwatch.service` is a systemd unit file for Raspberry Pi/Linux deployments.
- `ops/run-prod.sh` starts the packaged application with production profile defaults.
- `ops/backup-db.sh` creates timestamped SQLite backups.

## Goals

- run comfortably on **Raspberry Pi 3**
- use **Java 25** and **Spring Boot 4.x**
- keep memory usage low
- avoid unnecessary infrastructure
- provide an easy-to-maintain modular monolith
- keep backend code in English
- keep the UI Slovak-first with English translation support
- maintain **90%+ aggregated test coverage**

## Planned Feature Set

### Vehicle management
- add car
- edit car
- deactivate car
- store:
  - name
  - license plate
  - registration date
  - VIN

### Insurance management
When a car is created, the application automatically creates two insurance policy records:
- **PZP**
- **collision insurance**

Both are later editable.

Supported insurance capabilities:
- policy metadata editing
- expiry date tracking
- warning window configuration
- per-policy schedule configuration
- manual or online check mode where applicable

### Technical and legal checks
- STK expiry tracking
- EK expiry tracking
- vignette tracking for:
  - Slovakia
  - Czech Republic
  - Austria

### Scheduling and reminders
- cron-based checks
- per-check editable cron expressions
- daily summary email
- daily reminder for near-expiry items
- storage of every cron run and its result in DB
- manual “run now” support

### Dashboard and monitoring
- Thymeleaf admin dashboard
- overview of all cars and expiries
- history of checks and findings
- notification log
- JavaMelody monitoring available from the main menu

## Recommended Architecture

### Style
A **modular monolith** with a single deployable Spring Boot application.

### Why this architecture
- simplest deployment model on Raspberry Pi
- low runtime overhead
- easier local maintenance
- enough structure for future growth

### Planned Maven modules
- `carwatch-parent` – dependency management, plugin management, JaCoCo aggregation
- `carwatch-boot` – Spring Boot entrypoint
- `carwatch-domain` – domain model and business rules
- `carwatch-application` – use cases, scheduler orchestration, reminder logic
- `carwatch-infrastructure` – SQLite, Flyway, HTTP adapters, email adapters, monitoring config
- `carwatch-web` – controllers, Thymeleaf UI, i18n

## Technology Stack

- **Java 25**
- **Spring Boot 4.x**
- **Spring MVC**
- **Thymeleaf**
- **Spring Data JDBC**
- **SQLite**
- **Flyway**
- **JavaMelody**
- **Micrometer / Spring Boot observability**
- **JUnit 5**
- **Mockito**
- **JaCoCo**

## Database Choice

The recommended database is **SQLite**.

### Why SQLite
- embedded single-file database
- low memory use
- very small operational burden
- suitable for tiny local workloads
- good match for Raspberry Pi 3

### Runtime recommendations
At startup, configure:

```sql
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;
PRAGMA busy_timeout=5000;
```

## Core Domain Model

### Main entities
- `Car`
- `InsurancePolicy`
- `CarVignetteSelection`
- `ObligationState`
- `CheckSchedule`
- `CheckRunLog`
- `NotificationLog`
- `AppSetting`

### Important design rules
- a car automatically gets **PZP** and **collision insurance** records when created
- successful online checks update stored expiry dates
- a failed online check must **not erase** a previously known good expiry date
- every scheduler run must be stored in DB
- per-car and per-policy schedules are editable

## Scheduling Design

The preferred design is a **small DB-backed scheduler dispatcher** instead of a heavyweight job framework.

### Approach
- one lightweight dispatcher runs periodically
- it loads due schedules from DB
- claims one schedule atomically
- executes the check
- stores run log and any expiry updates
- computes and stores the next execution time

### Why
- lower memory footprint than Quartz-first design
- dynamic cron expressions editable from UI
- easier auditability in database
- enough for max 2 concurrent users and small number of cars

## Check Providers

Provider integrations should be isolated behind interfaces.

### Planned categories
- PZP checker
- collision insurance checker
- STK checker
- EK checker
- vignette checkers for SK / CZ / AT

### Recommended rule
- implement providers as adapters
- prefer direct HTTP/form integrations over browser automation
- treat collision insurance as **manual mode first**, unless a stable insurer-specific integration is available later

## Internationalization

### Language policy
- **backend**: English
- **logs**: English
- **database values / enums**: English
- **frontend default**: Slovak
- **optional UI language**: English

### Suggested message bundles
- `messages_sk.properties`
- `messages_en.properties`

## Security

No authentication is planned because the application is intended only for a trusted local network.

This can be revisited later if remote access is introduced.

## Logging, Monitoring, and Operations

### Logging
Use Spring Boot + Logback with:
- rolling daily logs
- 30-day retention
- structured log fields where practical
- trace/correlation IDs for scheduler runs

### Monitoring
- JavaMelody available from main menu
- Actuator health/info endpoints
- DB-backed run history for business-level audit

## Testing Strategy

Target: **at least 90% aggregated coverage**.

### Planned test layers
- unit tests for domain logic
- parser tests for provider adapters
- repository tests against real SQLite
- MVC tests for controllers and forms
- integration tests for scheduler flows
- fake email sender / fake providers for deterministic tests

### Coverage tooling
Use **JaCoCo** with:
- module-level reports
- aggregated root report
- coverage gate in Maven build

## Initial Files in This Draft

### `plan.md`
Contains the implementation plan, architecture, domain model, rollout phases, testing approach, and operational guidance.

### `V1__init.sql`
Contains the first schema draft for:
- cars
- insurance policies
- vignette selections
- obligation states
- schedules
- run logs
- notifications
- application settings

## Suggested Development Order

1. project skeleton and parent Maven build
2. SQLite + Flyway + first migration
3. basic Thymeleaf layout and i18n
4. car CRUD
5. insurance auto-creation during car creation
6. schedule management
7. DB-backed scheduler
8. manual reminder evaluation
9. provider adapters
10. email notifications
11. dashboard and reporting polish
12. full automated test suite and coverage hardening

## Local Development Notes

### Suggested JVM settings for Raspberry Pi 3
A conservative starting point:

```bash
-Xms128m -Xmx384m
```

### Suggested packaging
- single executable JAR
- optional systemd service on Raspberry Pi
- nightly DB backup of SQLite file

## Future Additions

Potential follow-up artifacts:
- multi-module `pom.xml`
- `docker-compose.yml` for development only
- Spring Data JDBC entities
- repository interfaces
- Flyway `V2__seed_demo_data.sql`
- DTOs and Thymeleaf templates
- scheduler implementation skeleton
- email templates in Slovak and English

## Notes

This repository is intentionally optimized for simplicity and reliability over distributed scalability.

That tradeoff is the correct one for:
- Raspberry Pi 3
- local network use
- two-person concurrency
- small number of monitored vehicles

## License

Private project draft.
