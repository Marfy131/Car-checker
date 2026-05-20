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

## Public Data Sources — Current Availability

As of 2025, **no free, officially documented public REST/JSON API exists** for any of the vehicle obligation types this application tracks. All checks currently run in `MANUAL` mode — expiry dates are entered by the user through the web UI and stored in the database. The scheduler then evaluates those stored dates and sends email reminders.

The table below summarises what was found after researching each obligation type.

| Obligation | Country | Free public API? | Best available option |
|---|---|---|---|
| PZP (mandatory insurance) | SK | No | Web form only at [skp.sk](https://www.skp.sk/vyhladat-poistovatela-vozidla-a-overit-platnost-pzp/) — old undocumented webservice (`ws.skp.sk`) is dead; direct agreement with SKP required for programmatic access |
| Collision insurance | SK/CZ | No | No public registry for collision policies in any country |
| STK (technical inspection) | SK | No | [data.slovensko.sk](https://data.slovensko.sk/datasety/api-pre-pristup-k-stk-vozidiel) lists an API in the national open data catalogue, but actual access requires a NASES integration agreement — intended for public-sector bodies, not self-service |
| STK | CZ | Partial | Czech Ministry of Transport publishes bulk CSV open data at [dataovozidlech.cz](https://dataovozidlech.cz/otevrenaData/vypisy); lookup is **VIN-only** (license plate blocked by GDPR); a semi-public API is consumed by third-party services (stkguru.cz) but the endpoint is not officially documented |
| EK (emission control) | SK | No | Web form at [seka.sk](https://www.seka.sk/verejnost/sluzby/overenie-emisnej-kontroly) only |
| Vignette | SK | No | Web form at [eznamka.sk](https://eznamka.sk) only; NDS has not published an API |
| Vignette | CZ | No | Web form at [edalnice.cz](https://edalnice.cz) only |
| Vignette | AT | No | Public web query at [evidenz.asfinag.at](https://evidenz.asfinag.at/en/) — publicly accessible but no documented developer API |
| Vignette | HU | No | Web form at [toll-charge.hu](https://toll-charge.hu/en/query-valid-e-vignettes) only |

### Commercial aggregators

The following paid services have real APIs and cover multiple countries including SK/CZ/AT/HU. They aggregate vehicle history, inspection records, and in some cases insurance data.

| Service | Coverage | Notes |
|---|---|---|
| [autoDNA](https://www.autodna.com/company/partners-area) | 26+ EU countries | Per-lookup pricing; partners portal |
| [Cebia](https://www.cebia.cz) | CZ + SK primary, 20 countries | Strong CZ/SK data; B2B API |
| [GlobalVIN](https://globalvin.co/european-api) | 27+ EU countries | MOT/inspection data; contact for pricing |

### Future integration path

If any of the above sources opens a public API, the application is already structured to accept new providers:
- `PolicyCheckProvider` interface — for PZP / collision insurance online checks
- `VehicleCheckProvider` interface — for STK, EK, vignette online checks
- `CheckMode.ONLINE` path in `ManualFirstPolicyVehicleCheckProvider` — falls back gracefully to stored date if provider is absent or fails

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
