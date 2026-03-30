# Car Watcher – Implementation Plan

## 0. GitHub Copilot CLI / AI Agent Instructions

When executing this plan via GitHub Copilot CLI or other AI agents, adhere to the following strict guidelines:
1. **No JPA/Hibernate:** Strictly use **Spring Data JDBC** and **JdbcTemplate** for data access. 
2. **Database:** Use **SQLite** exclusively. Configure `WAL` mode. Do not use H2 or PostgreSQL.
3. **Architecture:** Maintain the single-application modular monolith structure defined in Section 3. Do not create separate deployable microservices.
4. **Step-by-step Execution:** Follow the atomic tasks defined in **Section 24 (Prompt-Ready Tasks)** sequentially. Complete one prompt fully, verify it, and wait for the user to proceed to the next.
5. **Testing First/Along:** Always generate JUnit 5 and Mockito tests alongside the implementation to maintain the 90% coverage requirement.
6. **Language:** Keep all code, variables, logs, and comments in English. Only use Slovak for Thymeleaf UI text and Email templates as specified.

## 1. Goal

Build a low-memory Spring Boot 4.x application on Raspberry Pi 3 using Java 25 for tracking vehicle-related obligations and notifying users by email.

The application will:
- manage cars and their related obligations
- periodically check online statuses where feasible
- store expiry dates and check history in a small embedded database
- send email alerts and daily summaries
- provide a local-network admin dashboard in Slovak with optional English language switch
- expose JavaMelody monitoring from the main menu

## 2. Key Constraints

- Hardware: Raspberry Pi 3, 1 GB RAM
- Runtime: Java 25
- Framework: Spring Boot 4.x
- Frontend: Thymeleaf
- No authentication/security needed, app only on local network
- Very low concurrency: max 2 users
- Low memory footprint is a priority
- Backend code and logs in English
- Frontend default language: Slovak, with English toggle
- Test coverage target: at least 90% aggregated across all modules

## 3. Recommended Architecture

### 3.1 Architectural Style

Use a **modular monolith** with one deployable Spring Boot application.

Reason:
- lowest operational complexity
- fits Raspberry Pi resource limits
- easy deployment as a single JAR
- still keeps code organized for future growth

### 3.2 Maven Module Layout

Recommended multi-module Maven project:

- `carwatch-parent`
  - parent pom, dependency management, plugin management, JaCoCo aggregate
- `carwatch-boot`
  - Spring Boot entrypoint and runtime assembly
- `carwatch-domain`
  - domain model, enums, policies, business rules
- `carwatch-application`
  - use cases, orchestration, scheduler logic, notification rules
- `carwatch-infrastructure`
  - persistence, Flyway, SQLite access, external HTTP adapters, email adapters, monitoring config
- `carwatch-web`
  - controllers, Thymeleaf pages, form models, i18n wiring

## 4. Database Recommendation

### 4.1 Chosen Database

Use **SQLite**.

Reason:
- embedded single-file DB
- very small memory footprint
- good fit for small writes and reads
- simpler than running PostgreSQL on Raspberry Pi
- more suitable than H2 for actual persistent application data

### 4.2 Database Access Strategy

Use:
- **Spring Data JDBC** for repositories
- `JdbcTemplate` for custom queries where needed
- **Flyway** for schema migrations

Avoid Hibernate/JPA unless a later requirement truly needs it.

### 4.3 SQLite Runtime Settings

At startup, configure:
- WAL mode
- foreign keys enabled
- busy timeout

Example startup SQL:

```sql
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;
PRAGMA busy_timeout=5000;
```

## 5. Functional Scope

### 5.1 Managed Data

Each car can have:
- basic vehicle data
- PZP insurance policy
- collision insurance policy
- STK validity
- EK validity
- vignette monitoring for selected countries: SK, CZ, AT
- per-check schedules and warning windows

### 5.2 Checks and Notifications

The app will support:
- cron-based automatic checks
- manual run-now actions
- storing each run result in DB
- updating expiry dates from successful checks
- sending reminders when expiry is nearing
- sending a daily summary email at configurable time
- sending daily reminders while an item remains close to expiry or expired

## 6. Domain Model

## 6.1 Core Entities

### Car

Fields:
- `id`
- `name`
- `licensePlate`
- `registrationDate`
- `vin`
- `active`
- `version`
- `createdAt`
- `updatedAt`

### InsurancePolicy

A car gets two insurance policy records automatically when created.

Fields:
- `id`
- `carId`
- `policyType` (`PZP`, `COLLISION`)
- `enabled`
- `insurerName`
- `policyNumber`
- `expiryDate`
- `status` (`VALID`, `EXPIRING`, `EXPIRED`, `UNKNOWN`, `ERROR`)
- `checkMode` (`ONLINE`, `MANUAL`)
- `warningDaysBefore`
- `lastCheckedAt`
- `lastSuccessfulCheckAt`
- `notes`
- `version`
- `createdAt`
- `updatedAt`

### CarVignetteSelection

Fields:
- `carId`
- `country` (`SK`, `CZ`, `AT`)
- `enabled`

### ObligationState

Represents the latest known state for a monitored obligation.

Fields:
- `id`
- `carId`
- `obligationType` (`PZP`, `COLLISION`, `STK`, `EK`, `VIGNETTE_SK`, `VIGNETTE_CZ`, `VIGNETTE_AT`)
- `expiryDate`
- `status`
- `lastCheckedAt`
- `lastSuccessfulCheckAt`
- `sourceSystem`
- `detailsJson`
- `manualOverride`
- `createdAt`
- `updatedAt`

### CheckSchedule

Fields:
- `id`
- `carId` nullable for global jobs
- `insurancePolicyId` nullable
- `checkType`
- `cronExpression`
- `zoneId`
- `enabled`
- `warningDaysBefore`
- `nextRunAt`
- `lockUntil`
- `lockOwner`
- `version`
- `createdAt`
- `updatedAt`

### CheckRunLog

Fields:
- `id`
- `scheduleId`
- `carId`
- `insurancePolicyId` nullable
- `checkType`
- `startedAt`
- `finishedAt`
- `status` (`SUCCESS`, `WARNING`, `ERROR`, `NO_DATA`)
- `message`
- `expiryDateFound`
- `findingsJson`
- `durationMs`
- `traceId`

### NotificationLog

Fields:
- `id`
- `carId`
- `insurancePolicyId` nullable
- `notificationType`
- `channel` (`EMAIL`)
- `subject`
- `recipient`
- `sentAt`
- `status`
- `providerMessageId`
- `checkRunId` nullable

### AppSetting

Fields:
- `id`
- `settingKey`
- `settingValue`
- `updatedAt`

## 6.2 Enumerations

Recommended enums:
- `PolicyType`
- `CheckType`
- `ObligationType`
- `CountryCode`
- `CheckMode`
- `CheckStatus`
- `ExpiryStatus`
- `NotificationType`

## 7. Main Functional Flows

## 7.1 Car Creation

When a new car is created:
1. save `Car`
2. auto-create two insurance records:
   - PZP
   - collision insurance
3. create vignette selection rows for chosen countries
4. create default schedule rows for applicable checks
5. initialize latest obligation states

### Default schedules created on car creation

- `PZP_CHECK`
- `COLLISION_INSURANCE_CHECK`
- `STK_CHECK`
- `EK_CHECK`
- `VIGNETTE_SK_CHECK` if SK enabled
- `VIGNETTE_CZ_CHECK` if CZ enabled
- `VIGNETTE_AT_CHECK` if AT enabled

## 7.2 Car Modification

User can later modify:
- car data
- both insurance records
- vignette selections
- cron expressions per check
- warning window per check
- enabled/disabled flags

## 7.3 Automatic Checking

A lightweight dispatcher runs every 30 seconds:
1. fetch due schedule rows
2. claim one schedule row by lock update
3. execute matching check handler
4. save run log
5. update latest obligation state
6. compute and persist next run
7. release lock

## 7.4 Notification Flow

When a successful or manual evaluation indicates nearing expiry:
- create a notification candidate
- deduplicate by obligation/day
- send email
- save notification log

Daily summary runs as a global scheduled task.

## 8. Scheduler Design

## 8.1 Why Not Quartz

Quartz is not necessary for this app initially.

Reason:
- dynamic scheduling can be implemented with DB-backed schedule rows
- much lighter than introducing a full scheduler framework
- easier control over run logging and per-car schedules

## 8.2 Scheduler Components

### ScheduleDispatcher
- fixed internal poll interval, e.g. every 30 seconds
- scans `check_schedule` for due items

### ScheduleClaimService
- atomically claims due schedule rows using `lockUntil`

### CheckExecutor
- routes to correct provider/service

### NextRunCalculator
- computes next run from cron expression and zone

### RunLoggingService
- writes `check_run_log`

## 8.3 Concurrency Model

Keep it very small:
- dispatcher thread: 1
- check worker threads: 1 or 2
- DB connection pool: max 4
- web thread pool reduced from defaults

## 9. Check Provider Architecture

## 9.1 Provider Interfaces

### General check interface

```java
public interface VehicleCheckProvider {
    CheckType supportedType();
    CheckOutcome execute(CheckCommand command);
}
```

### Insurance-specific provider interface

```java
public interface PolicyCheckProvider {
    PolicyType supportedPolicyType();
    CheckMode supportedMode();
    CheckOutcome execute(PolicyCheckCommand command);
}
```

## 9.2 Planned Providers

### Insurance
- `PzpOnlineCheckProvider`
- `PzpManualCheckProvider`
- `CollisionManualCheckProvider`

### Technical/vehicle obligations
- `StkCheckProvider`
- `EkCheckProvider`
- `SkVignetteProvider`
- `CzVignetteProvider`
- `AtVignetteProvider`

## 9.3 Important Practical Rule

Collision insurance should start as **manual expiry tracking**.

Reason:
- no assumption that a common public online checker exists across insurers
- architecture must still support insurer-specific adapters later if needed

## 9.4 External Integration Strategy

Use:
- Spring `RestClient` for outbound HTTP
- `Jsoup` for HTML parsing
- HTML fixtures in tests

Avoid headless browser automation initially.
Only use a browser automation fallback if form submissions cannot be implemented with simple HTTP + HTML parsing.

## 10. Business Rules

## 10.1 Status Evaluation

Recommended status logic:
- `VALID`: expiry date beyond warning window
- `EXPIRING`: expiry date within warning window
- `EXPIRED`: expiry date before today
- `UNKNOWN`: no known date yet
- `ERROR`: latest check failed

## 10.2 Data Preservation Rule

Do not replace a known valid expiry date with `null` because an online source failed.

On provider/network/parser failure:
- keep last successful expiry date
- mark latest run as `ERROR`
- optionally track consecutive failures

## 10.3 Reminder Policy

Default warning windows:
- PZP: 14 days
- collision insurance: 14 days
- STK: 30 days
- EK: 30 days
- vignette: 7 days

Behavior:
- send immediate notification when state becomes expiring or expired
- send at most one reminder per obligation per day
- include expiring items in daily summary

## 11. User Interface Plan

## 11.1 UI Language

- default language: Slovak
- alternative language: English
- toggle in page header
- backend code/messages/logging stay English

## 11.2 Main Pages

### Dashboard
Shows:
- all cars overview
- nearest expiries
- latest runs
- red/orange/green status cards

### Cars
- list cars
- add car
- edit car
- deactivate car

### Car Detail
Sections or tabs:
- basic info
- insurances
- vignettes
- schedules
- latest states
- run history

### Schedules
- view all schedules
- edit cron expressions
- enable/disable schedules
- manual run

### Run History
- filter by car
- filter by check type
- show findings, duration, outcome, timestamps

### Notifications
- sent email log
- failures
- last sent summary

### Settings
- recipients
- daily summary cron
- default locale
- app defaults

### Monitoring
- JavaMelody page linked from main menu

## 11.3 Thymeleaf Notes

Use:
- layout dialect or shared fragments
- message bundles for all labels
- simple forms with server-side validation
- Bootstrap or minimal CSS for lightweight UI

## 12. Monitoring, Logging, and Tracing

## 12.1 Monitoring

Enable:
- Spring Boot Actuator health/info
- JavaMelody for performance and SQL monitoring

## 12.2 Logging Strategy

Use two layers of logging:

### Technical file logging
For:
- startup
- scheduler actions
- provider calls
- errors
- tracing/correlation

### Business run logs in DB
For:
- each cron execution
- results and findings
- duration
- expiry dates found
- notification relation

## 12.3 Log Rotation

Configure Logback rolling policy:
- daily rotation
- keep 30 days
- separate error file optional

Suggested configuration goals:
- `app.log`
- `error.log`
- max history 30
- gzip older files optional

## 12.4 Tracing Fields

Include in logs when relevant:
- `traceId`
- `runId`
- `scheduleId`
- `carId`
- `insurancePolicyId`
- `licensePlate`
- `checkType`
- `durationMs`

## 13. Email Design

## 13.1 Outbound Email Port

```java
public interface EmailSender {
    SendResult send(EmailMessage message);
}
```

## 13.2 Initial Implementation

Implement one email adapter using your chosen email API.
Possible future fallback:
- SMTP adapter

## 13.3 Daily Summary Email Template – Slovak

### Subject

`Denný prehľad vozidiel – {date}`

### Body

```text
Dobrý deň,

posielam denný prehľad vozidiel k {generatedAt}.

Súhrn upozornení:
{warningSummary}

Vozidlá:
{carBlocks}

Posledné spustenia kontrol:
{runSummary}

S pozdravom,
Car Watcher
```

### Per-car block

```text
- {carName} ({licensePlate})
  PZP: {pzpExpiry} [{pzpStatus}]
  Havarijné poistenie: {collisionExpiry} [{collisionStatus}]
  STK: {stkExpiry} [{stkStatus}]
  EK: {ekExpiry} [{ekStatus}]
  Diaľničná známka SK: {skVignetteExpiry} [{skVignetteStatus}]
  Diaľničná známka CZ: {czVignetteExpiry} [{czVignetteStatus}]
  Diaľničná známka AT: {atVignetteExpiry} [{atVignetteStatus}]
```

## 13.4 Daily Summary Email Template – English

### Subject

`Daily vehicle status – {date}`

### Body

```text
Hello,

here is the daily vehicle status as of {generatedAt}.

Warnings summary:
{warningSummary}

Vehicles:
{carBlocks}

Last check runs:
{runSummary}

Best regards,
Car Watcher
```

## 14. Persistence Schema Draft

## 14.1 Main Tables

Create at least these tables in first migrations:
- `car`
- `insurance_policy`
- `car_vignette_selection`
- `obligation_state`
- `check_schedule`
- `check_run_log`
- `notification_log`
- `app_setting`

## 14.2 Suggested DDL Direction

Important indexes:
- `car(license_plate)` unique
- `car(vin)` unique
- `insurance_policy(car_id, policy_type)` unique
- `obligation_state(car_id, obligation_type)` unique
- `check_schedule(enabled, next_run_at)`
- `check_run_log(car_id, started_at desc)`
- `notification_log(sent_at desc)`

## 15. Suggested Package Structure

```text
com.example.carwatch
├── boot
├── domain
│   ├── car
│   ├── insurance
│   ├── obligation
│   ├── schedule
│   └── notification
├── application
│   ├── car
│   ├── insurance
│   ├── checks
│   ├── schedule
│   ├── notification
│   └── dashboard
├── infrastructure
│   ├── persistence
│   ├── flyway
│   ├── sqlite
│   ├── http
│   ├── providers
│   ├── email
│   ├── monitoring
│   └── config
└── web
    ├── controller
    ├── form
    ├── view
    ├── mapper
    └── i18n
```

## 16. Configuration Plan

## 16.1 Application Properties

Key properties to externalize:
- application locale defaults
- timezone (`Europe/Bratislava`)
- DB file path
- dispatcher poll interval
- thread pool sizes
- email API settings
- JavaMelody path
- daily summary cron

## 16.2 Default Runtime Tuning for Pi 3

Suggested initial JVM options:
- `-Xms128m`
- `-Xmx384m`
- `-XX:+UseSerialGC` or validate G1 works acceptably in testing

Keep runtime dependencies and thread counts small.

## 17. Error Handling Strategy

Types of recoverable errors:
- network timeout
- HTTP failure
- HTML parsing/layout drift
- email send failure
- schedule lock conflict

Handling rules:
- store run log with `ERROR`
- keep last good expiry value
- expose error details on run history page
- retry only a small number of times for outbound HTTP/email

## 18. Testing Strategy

## 18.1 Coverage Goal

- aggregated line coverage at least 90%
- enforced in Maven build

## 18.2 Test Types

### Unit tests
Test:
- expiry evaluation
- reminder policy
- cron next run logic
- deduplication
- dashboard aggregations

### Repository tests
Use real SQLite.
Test:
- migrations
- repository CRUD
- unique constraints
- query correctness

### Parser/provider tests
Use saved HTML fixtures.
Test:
- successful extraction
- missing data
- changed layout tolerance
- provider error handling

### Integration tests
Test end-to-end flows:
- car creation auto-creates insurance and schedules
- scheduler execution updates states
- reminder sending stores notification logs
- daily summary generation

### Web/MVC tests
Test:
- form validation
- page rendering models
- locale switching
- edit flows

## 18.3 Test Tooling

Suggested libraries:
- JUnit 5
- AssertJ
- Mockito
- Spring Boot Test
- MockMvc
- Testcontainers not necessary for SQLite
- MockWebServer or WireMock for HTTP integrations

## 18.4 JaCoCo Setup

At parent module:
- attach agent to all modules
- create aggregate report in parent/reporting module
- fail build below 90%

## 19. Delivery Phases

## Phase 1 – foundation

Deliver:
- parent Maven build
- module skeleton
- Boot startup app
- SQLite and Flyway setup
- basic layout page
- i18n setup
- logback rotation
- JavaMelody integration
- Actuator health

## Phase 2 – core car management

Deliver:
- `Car` CRUD
- auto-create PZP and collision policy on new car
- vignette selections
- edit pages
- DB migrations for base entities

## Phase 3 – scheduling core

Deliver:
- `check_schedule` model and repository
- dispatcher
- locking logic
- next-run calculation
- run logs
- manual run support

## Phase 4 – manual policy evaluation and reminders

Deliver:
- manual PZP evaluation
- manual collision policy evaluation
- reminder policy engine
- daily reminder deduplication

## Phase 5 – online providers

Implement in this order:
1. AT vignette
2. SK vignette
3. CZ vignette
4. STK
5. EK
6. PZP online checker

Reason:
- lower risk providers first
- PZP online integration may be the most fragile or policy-sensitive

## Phase 6 – notifications and summary emails

Deliver:
- email sender adapter
- immediate reminders
- daily summary cron
- notification log UI

## Phase 7 – dashboard and history polish

Deliver:
- dashboard status cards
- history filters
- better Slovak texts
- English translation coverage
- monitoring link polish

## Phase 8 – hardening and release

Deliver:
- performance tuning on Raspberry Pi
- DB backup script
- systemd service file
- production log verification
- final coverage gate

## 20. Deployment Plan

## 20.1 Packaging

Build one executable JAR.

## 20.2 Raspberry Pi Runtime Layout

Suggested filesystem layout:

```text
/opt/carwatch/
  app/
    carwatch.jar
  config/
    application-prod.yml
  data/
    carwatch.db
  logs/
    app.log
    error.log
  backups/
```

## 20.3 Service Setup

Run as systemd service.

Suggested responsibilities:
- auto-start on boot
- restart on failure
- use external config path
- write logs to dedicated directory

## 20.4 Backup Plan

Nightly backup:
- copy SQLite DB to dated backup file
- optionally compress backups
- keep a limited number of days

## 21. Security Position

No login/security is required per current scope because the app is for local network use only.

Still recommended:
- bind only to local/private network interface if possible
- disable unnecessary endpoints
- do not expose admin/debug endpoints beyond local network
- keep email API keys in external config, not source code

## 22. Open Risks and Mitigations

### Risk 1 – external HTML changes
Mitigation:
- isolate each provider
- use fixture-based parser tests
- log raw response snippets when safe

### Risk 2 – public check services may restrict automation
Mitigation:
- verify before rollout
- keep manual mode as fallback
- make provider optional per check type

### Risk 3 – SD card wear on Raspberry Pi
Mitigation:
- rotate logs
- keep logs concise
- back up DB
- consider good quality storage

### Risk 4 – SQLite locking under simultaneous writes
Mitigation:
- WAL mode
- tiny worker pool
- short transactions
- single app instance

## 23. Definition of Done

A release is done when:
- car CRUD works
- PZP and collision policy records are auto-created with car creation
- policies are editable
- vignette selections are editable
- schedules are editable in UI
- each cron run is stored in DB
- expiry dates persist correctly
- reminders and daily summaries send emails
- Slovak UI and English switch work
- JavaMelody is accessible from menu
- logs rotate for 30 days
- tests pass with aggregated coverage >= 90%
- app runs stably on Raspberry Pi 3 within memory budget

## 24. Prompt-Ready Tasks for GitHub Copilot CLI

*(Feed these tasks sequentially to the Copilot CLI)*

**Task 1: Project Skeleton & Maven Setup**
> "Generate a parent Maven `pom.xml` for `carwatch-parent` using Java 25 and Spring Boot 4.x. Include plugin management for JaCoCo. Create sub-modules: `carwatch-boot`, `carwatch-domain`, `carwatch-application`, `carwatch-infrastructure`, and `carwatch-web`. Create basic empty `pom.xml` files for each sub-module linking to the parent."

**Task 2: Database & Core Dependencies**
> "In `carwatch-infrastructure`, add dependencies for Spring Data JDBC, SQLite JDBC driver, and Flyway. In `carwatch-boot`, create the main `CarWatcherApplication` class. Add `application.yml` configuring the SQLite connection (`jdbc:sqlite:data/carwatch.db`) and Flyway."

**Task 3: Initial Database Migration**
> "Create a Flyway migration script `V1__init.sql` in `carwatch-infrastructure` to create tables for: `car`, `insurance_policy`, `check_schedule`, and `check_run_log`. Use SQLite syntax, ensure foreign keys are supported, and include unique constraints like `car(license_plate)`."

**Task 4: Domain Entities**
> "In `carwatch-domain`, create the core Java records/classes for `Car` and `InsurancePolicy`. Include the enums `PolicyType`, `CheckMode`, and `CheckStatus` as defined in the domain model."

**Task 5: Repositories (Spring Data JDBC)**
> "In `carwatch-infrastructure`, create Spring Data JDBC repository interfaces for `Car` and `InsurancePolicy`. Add custom `@Query` methods if necessary for finding cars by license plate or active status."

**Task 6: Car Creation Use Case**
> "In `carwatch-application`, implement a `CarManagementService`. Write a method to create a new `Car` which automatically creates two linked `InsurancePolicy` records (PZP and COLLISION) with default 'MANUAL' check modes. Write a JUnit 5 test for this logic."

**Task 7: Web Layer & Thymeleaf Layout**
> "In `carwatch-web`, add Thymeleaf dependencies. Create a base layout HTML file using Bootstrap or minimal CSS. Create a `CarController` with a `/cars` endpoint that lists all cars from the DB, rendering them in a `cars-list.html` view."

**Task 8: Scheduler Core**
> "In `carwatch-application`, implement a custom lightweight scheduler (`ScheduleDispatcher`) using Spring's `@Scheduled` to poll `check_schedule` every 30 seconds. Do not use Quartz. Ensure it claims rows atomically using a lock field."

**Task 9: Check Provider Interfaces**
> "In `carwatch-domain`, define the `VehicleCheckProvider` and `PolicyCheckProvider` interfaces. Then, in `carwatch-infrastructure`, implement a stub for `PzpManualCheckProvider`."

**Task 10: Email Notification Setup**
> "In `carwatch-infrastructure`, implement an `EmailSender` interface using Spring Mail. Create a service in `carwatch-application` that generates the 'Daily Summary Email' in Slovak using Thymeleaf template processing for the email body."

