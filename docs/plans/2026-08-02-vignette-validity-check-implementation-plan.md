# Online Vignette Validity Check Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add free SK/CZ/AT vignette validity lookup with conservative Playwright automation, persisted T-14/T-7/T-1 scheduling, and a human confirmation fallback.

**Architecture:** Keep browser code behind an application port. Reuse one extracted schedule execution pipeline for poller and `Check now`. Store only sanitized typed metadata in existing JSON fields; preserve known-good obligation data on every non-success result. All external checks are serialized and rate-limited from persisted run history.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Spring Data JDBC, SQLite/Flyway, Thymeleaf MVC, Playwright Java, JUnit 5, Mockito, MockMvc.

---

## Execution rules

- Execute in a dedicated worktree.
- Keep `.serena/` and all unrelated user files untouched.
- Follow red-green-refactor for every task: add one focused failing test, run it and confirm the expected failure, add minimum implementation, rerun.
- Never run live official-site checks in the standard Maven lifecycle.
- Never add CAPTCHA solvers, stealth/fingerprint plugins, Google login, proxies, screenshots, traces, or raw provider HTML persistence.
- Commit after each task only when its focused tests pass.
- Run commands from repository root unless a step says otherwise.

## Task 1: Define typed vignette lookup contracts and safe run metadata

**Files:**

- Modify: `pom.xml`
- Modify: `carwatch-application/pom.xml`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteLookupStatus.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteLookupRequest.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignettePeriod.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteLookupResult.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteLookupGateway.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteRunSource.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteRunDetails.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteRunDetailsCodec.java`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteRunDetailsCodecTest.java`

**Step 1: Add a failing codec round-trip test**

Test that the codec:

- round-trips country, lookup status, source, `automatedAttempt`, `manualActionRequired`, and sanitized periods;
- returns an empty/unknown model for null, blank, or malformed JSON;
- rejects provider metadata keys outside a small allowlist;
- never serializes plate, cookies, tokens, HTML, or URLs.

Core contracts should be:

```java
public enum VignetteLookupStatus {
    FOUND, NOT_FOUND, HUMAN_REQUIRED, SITE_CHANGED, TEMPORARY_ERROR, RATE_LIMITED
}

public record VignetteLookupRequest(
        Long carId,
        String licensePlate,
        CountryCode country
) {}

public record VignetteLookupResult(
        VignetteLookupStatus status,
        LocalDate expiryDate,
        List<VignettePeriod> periods,
        String providerCode,
        String message,
        LocalDateTime retryAt
) {}

public interface VignetteLookupGateway {
    VignetteLookupResult lookup(VignetteLookupRequest request);
}
```

`VignetteRunDetails` is the only structure encoded into `findings_json` / `details_json`:

```java
public record VignetteRunDetails(
        int schemaVersion,
        CountryCode country,
        VignetteLookupStatus lookupStatus,
        VignetteRunSource source,
        boolean automatedAttempt,
        boolean manualActionRequired,
        List<VignettePeriod> periods,
        Map<String, String> providerMetadata
) {}
```

Use Jackson 3 (`tools.jackson.core:jackson-databind`) managed by the Spring Boot BOM. Configure a local `JsonMapper`; do not enable default typing.

**Step 2: Run the test and verify red**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteRunDetailsCodecTest test
```

Expected: test compilation fails because the vignette contracts do not exist.

**Step 3: Implement contracts and codec**

Add `jackson.version` only if the Boot BOM does not manage the selected artifact. Keep all classes in application; no Playwright imports.

**Step 4: Run focused tests**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteRunDetailsCodecTest test
```

Expected: `BUILD SUCCESS`.

**Step 5: Commit**

```bash
git add pom.xml carwatch-application/pom.xml carwatch-application/src/main/java/com/carwatch/application/vignette carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteRunDetailsCodecTest.java
git commit -m "feat: define vignette lookup contracts"
```

## Task 2: Implement explicit T-14/T-7/T-1 scheduling

**Files:**

- Modify: `carwatch-domain/src/main/java/com/carwatch/domain/schedule/CheckOutcome.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteSchedulePlanner.java`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteSchedulePlannerTest.java`
- Modify test: `carwatch-domain/src/test/java/com/carwatch/domain/schedule/CheckOutcomeTest.java` if it exists; otherwise create it.

**Step 1: Write failing boundary tests**

With a fixed `Clock` and `Europe/Bratislava`, cover:

- unknown expiry -> empty next run;
- before T-14 06:00 -> T-14 06:00;
- exactly at a milestone -> next strictly later milestone;
- offline after T-14 but before T-7 -> T-7;
- after T-1 -> empty;
- replacement expiry -> new date's first future milestone;
- DST boundary still produces local 06:00;
- explicit `retryAt` (AT daily limit) wins over a milestone.

Extend `CheckOutcome` without breaking existing call sites:

```java
public record CheckOutcome(
        RunStatus status,
        String message,
        LocalDate expiryDateFound,
        String findingsJson,
        LocalDateTime suggestedNextRunAt
) {
    public CheckOutcome(RunStatus status, String message, LocalDate expiryDateFound, String findingsJson) {
        this(status, message, expiryDateFound, findingsJson, null);
    }
}
```

Planner API:

```java
public Optional<LocalDateTime> nextRun(
        LocalDate expiryDate,
        LocalDateTime strictlyAfter,
        LocalDateTime suggestedNextRunAt
)
```

**Step 2: Verify red**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteSchedulePlannerTest test
```

Expected: missing planner / five-argument `CheckOutcome` failure.

**Step 3: Implement minimum planner**

Generate milestones in this order: `expiry.minusDays(14)`, `minusDays(7)`, `minusDays(1)`, each at 06:00. Return first timestamp strictly after the execution time. `retryAt` is used only when it is later than the execution time.

**Step 4: Verify green**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteSchedulePlannerTest test
```

**Step 5: Commit**

```bash
git add carwatch-domain/src/main/java/com/carwatch/domain/schedule/CheckOutcome.java carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteSchedulePlanner.java carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteSchedulePlannerTest.java carwatch-domain/src/test/java/com/carwatch/domain/schedule/CheckOutcomeTest.java
git commit -m "feat: plan vignette milestone checks"
```

## Task 3: Enforce SK and synchronize country flags on create/edit

**Files:**

- Modify: `carwatch-application/src/main/java/com/carwatch/application/car/UpdateCarCommand.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/car/CarManagementService.java`
- Modify: `carwatch-application/src/test/java/com/carwatch/application/car/CarManagementServiceTest.java`
- Modify: `carwatch-web/src/main/java/com/carwatch/web/controller/CarController.java`
- Modify: `carwatch-web/src/main/resources/templates/cars/form.html`
- Modify: `carwatch-web/src/test/java/com/carwatch/web/controller/CarControllerWebMvcTest.java`

**Step 1: Add failing application tests**

Test:

- empty/null country input still creates enabled SK selection, SK obligation, and SK schedule;
- SK/CZ/AT schedules have blank cron and null `nextRunAt` until expiry is known;
- base checks and HU retain their current schedule behavior;
- edit always keeps SK enabled;
- enabling CZ/AT upserts selection, obligation, and schedule;
- disabling CZ/AT sets selection and schedule disabled but does not delete obligation/history;
- repeated updates do not create duplicate schedules or obligations;
- optimistic version is still copied to the car before save.

Change command shape:

```java
public record UpdateCarCommand(
        Long id,
        String name,
        String licensePlate,
        LocalDate registrationDate,
        String vin,
        int version,
        Set<CountryCode> vignetteCountries
) {}
```

**Step 2: Verify red**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CarManagementServiceTest test
```

Expected: assertions show SK missing and update selections unsynchronized.

**Step 3: Implement lifecycle rules**

Add one normalizer:

```java
private EnumSet<CountryCode> normalizeVignetteCountries(Set<CountryCode> requested) {
    EnumSet<CountryCode> result = requested == null || requested.isEmpty()
            ? EnumSet.noneOf(CountryCode.class)
            : EnumSet.copyOf(requested);
    result.add(CountryCode.SK);
    return result;
}
```

Use repository `findByCarId` collections to upsert/enable/disable. Do not delete state. Add `findEnabledVignetteCountries(carId)` for edit-form population.

**Step 4: Add failing MVC tests, then update controller/template**

Test SK is preselected and disabled visually, a hidden `vignetteSk=true` value is submitted, and CZ/AT values round-trip through edit. Keep HU unchanged.

```bash
mvn -q -pl carwatch-web -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CarControllerWebMvcTest test
```

Expected before web changes: edit form flags are false and update command lacks countries.

Implement the fixed SK control as a disabled checkbox plus hidden field, so browser submission cannot remove SK.

**Step 5: Run both suites**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CarManagementServiceTest test
mvn -q -pl carwatch-web -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CarControllerWebMvcTest test
```

**Step 6: Commit**

```bash
git add carwatch-application/src/main/java/com/carwatch/application/car carwatch-application/src/test/java/com/carwatch/application/car/CarManagementServiceTest.java carwatch-web/src/main/java/com/carwatch/web/controller/CarController.java carwatch-web/src/main/resources/templates/cars/form.html carwatch-web/src/test/java/com/carwatch/web/controller/CarControllerWebMvcTest.java
git commit -m "feat: synchronize vehicle vignette countries"
```

## Task 4: Backfill existing cars and add persisted lookup-history queries

**Files:**

- Modify: `carwatch-infrastructure/src/main/resources/db/migration/V1__init.sql`
- Create: `carwatch-infrastructure/src/main/resources/db/migration/V3__online_vignette_checks.sql`
- Modify: `carwatch-domain/src/main/java/com/carwatch/domain/schedule/CheckRunLogRepository.java`
- Modify: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/persistence/JdbcCheckRunLogRepository.java`
- Modify: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/persistence/CheckRunLogRepositoryAdapter.java`
- Test: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/persistence/VignetteMigrationIntegrationTest.java`
- Test: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/persistence/CheckRunLogRepositoryAdapterTest.java`

**Step 1: Write failing migration tests**

Create a pre-V3 SQLite fixture with:

- one car with no selections;
- one car with disabled SK;
- one selected CZ car;
- known SK expiry and no online schedule.

After Flyway migration assert:

- each car has enabled SK selection exactly once;
- each car has one SK obligation and one SK schedule;
- existing CZ/AT selection state is retained;
- SK/CZ/AT schedule cron is blank;
- known expiry gets the correct first future milestone, unknown expiry stays null;
- unique partial index prevents duplicate `(car_id, check_type)` schedules;
- index `(check_type, started_at DESC)` exists.

**Step 2: Verify red**

```bash
mvn -q -pl carwatch-infrastructure -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteMigrationIntegrationTest test
```

**Step 3: Add V3 and keep V1 synchronized**

V3 must use `INSERT ... SELECT ... WHERE NOT EXISTS`; never delete audit rows. Keep `cron_expression=''` because the existing column is `NOT NULL`. Add the same indexes to V1 for clean databases. If duplicate schedules already exist, fail the migration clearly rather than silently deleting logs.

**Step 4: Add failing repository-query tests**

Add port methods:

```java
List<CheckRunLog> findByCarIdAndCheckTypeSince(
        Long carId, CheckType checkType, LocalDateTime since);

List<CheckRunLog> findByCheckTypeBetween(
        CheckType checkType, LocalDateTime fromInclusive, LocalDateTime toExclusive);
```

Order newest first. Tests must prove country/type isolation and inclusive/exclusive boundaries.

**Step 5: Implement JDBC queries and verify**

```bash
mvn -q -pl carwatch-infrastructure -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteMigrationIntegrationTest,CheckRunLogRepositoryAdapterTest test
```

**Step 6: Commit**

```bash
git add carwatch-infrastructure/src/main/resources/db/migration carwatch-domain/src/main/java/com/carwatch/domain/schedule/CheckRunLogRepository.java carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/persistence carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/persistence
git commit -m "feat: persist vignette backfill and lookup history"
```

## Task 5: Extract shared schedule execution and preserve known-good state

**Files:**

- Create: `carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleExecutionService.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleDispatcher.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ObligationUpdateService.java`
- Modify: `carwatch-application/src/test/java/com/carwatch/application/schedule/ScheduleDispatcherTest.java`
- Modify: `carwatch-application/src/test/java/com/carwatch/application/schedule/ObligationUpdateServiceTest.java`
- Create: `carwatch-application/src/test/java/com/carwatch/application/schedule/ScheduleExecutionServiceTest.java`

**Step 1: Add failing obligation preservation tests**

For `WARNING`, `ERROR`, `NO_DATA`, and pending-human findings with no expiry, assert all of these remain unchanged:

- existing future `expiryDate`;
- calculated `ExpiryStatus`;
- `lastSuccessfulCheckAt`;
- `sourceSystem`.

Only `lastCheckedAt`, `updatedAt`, and safe `detailsJson` may change. `FOUND` updates expiry/status/source/success time. Make `updateFromOutcome` return the saved `ObligationState` so next-run planning uses exactly persisted state.

**Step 2: Verify red and implement preservation**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ObligationUpdateServiceTest test
```

**Step 3: Add failing execution-service tests**

Move from dispatcher into `ScheduleExecutionService.execute(CheckSchedule)`:

- car/command loading;
- provider invocation and exception-to-ERROR mapping;
- run logging;
- obligation update;
- vignette next-run planning from persisted expiry;
- cron next-run calculation for non-vignette schedules;
- schedule save.

Assert a missed vignette milestone runs once and advances, and failure has no retry loop. `ScheduleDispatcher` must retain only due polling, batching, claim/release, and safe error isolation.

**Step 4: Verify red, implement extraction, then green**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ScheduleExecutionServiceTest,ScheduleDispatcherTest test
```

Expected after implementation: dispatcher tests verify claim/release and delegation; execution tests verify state/log/schedule.

**Step 5: Commit**

```bash
git add carwatch-application/src/main/java/com/carwatch/application/schedule carwatch-application/src/test/java/com/carwatch/application/schedule
git commit -m "refactor: share scheduled check execution"
```

## Task 6: Add online providers, cooldowns, and Austrian daily limit

**Files:**

- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/OnlineVignetteVehicleCheckProvider.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteLookupRateLimitService.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteCheckService.java`
- Modify: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/config/ProviderConfiguration.java`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/OnlineVignetteVehicleCheckProviderTest.java`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteLookupRateLimitServiceTest.java`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteCheckServiceTest.java`
- Modify test: provider wiring/configuration tests under `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/config/`.

**Step 1: Write failing provider mapping tests**

For a fake gateway, verify:

| Lookup result | Run status | Expiry write | Pending | Automated attempt |
|---|---:|---:|---:|---:|
| `FOUND` | `SUCCESS` | yes | no | yes |
| `NOT_FOUND` | `WARNING` | no | yes | yes |
| `HUMAN_REQUIRED` | `WARNING` | no | yes | yes |
| `SITE_CHANGED` | `ERROR` | no | yes | yes |
| `TEMPORARY_ERROR` | `ERROR` | no | yes | yes |
| `RATE_LIMITED` | `WARNING` | no | yes | no |

Create one provider bean per SK/CZ/AT. Leave HU on `ObligationStateVehicleCheckProvider`. The provider derives country from `CheckTypeMapping`; no URL or country comes from user input.

**Step 2: Add rate-limit tests**

Using fixed Bratislava clock and persisted logs:

- user `Check now` blocked until 24 hours after latest `automatedAttempt=true` for same car/type;
- other cars/countries do not block it;
- manual confirmations do not count;
- AT allows three automated attempts across all cars in the local calendar day;
- fourth AT scheduled result suggests next local day 06:00;
- fourth user request returns human fallback immediately;
- restart simulation with a new service instance still sees the limit.

**Step 3: Verify red**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=OnlineVignetteVehicleCheckProviderTest,VignetteLookupRateLimitServiceTest,VignetteCheckServiceTest test
```

**Step 4: Implement services**

`VignetteCheckService.checkNow(carId, country)` must:

1. accept only SK/CZ/AT;
2. verify enabled selection;
3. find the unique schedule;
4. apply 24-hour cooldown;
5. claim it;
6. invoke `ScheduleExecutionService` synchronously;
7. release in `finally`;
8. return a view-safe result containing only status, next run, and whether manual action is required.

Scheduled checks do not use the per-car manual cooldown, but the gateway/provider always applies AT's global daily limit.

**Step 5: Replace provider wiring and reject duplicates**

```bash
mvn -q -pl carwatch-infrastructure -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='*ProviderConfigurationTest,*CheckExecutorTest' test
```

Expected: exactly one provider for each check type; SK/CZ/AT are online providers, HU stays unchanged.

**Step 6: Commit**

```bash
git add carwatch-application/src/main/java/com/carwatch/application/vignette carwatch-application/src/test/java/com/carwatch/application/vignette carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/config/ProviderConfiguration.java carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/config
git commit -m "feat: execute rate-limited vignette lookups"
```

## Task 7: Implement conservative Playwright gateway and isolated country parsers

**Files:**

- Modify: `pom.xml`
- Modify: `carwatch-infrastructure/pom.xml`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/VignetteBrowserProperties.java`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/OfficialVignetteSite.java`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/PlaywrightVignetteLookupGateway.java`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/page/OfficialVignettePage.java`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/page/SkVignettePage.java`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/page/CzVignettePage.java`
- Create: `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette/page/AtVignettePage.java`
- Create fixtures: `carwatch-infrastructure/src/test/resources/vignette/{sk,cz,at}/`
- Test: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette/page/SkVignettePageTest.java`
- Test: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette/page/CzVignettePageTest.java`
- Test: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette/page/AtVignettePageTest.java`
- Test: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette/PlaywrightVignetteLookupGatewayTest.java`

**Step 1: Pin dependency and create sanitized fixtures**

Add a parent property such as `playwright.version` and `com.microsoft.playwright:playwright` only to infrastructure. Pin the version verified during implementation; do not use a version range.

Fixtures must contain only minimal synthetic/sanitized HTML for:

- valid result and one/multiple periods;
- no-result page;
- Google reCAPTCHA, Turnstile, and MTCaptcha markers;
- changed/missing result container.

No real plate, cookie, token, page dump, or personal data may be committed.

**Step 2: Write failing parser tests**

Each country parser must return a typed result and parse dates with an explicit formatter/locale. AT selects digital vignette only and ignores section toll. Multiple valid periods choose the latest end date while retaining sanitized periods.

**Step 3: Implement parsers and verify**

```bash
mvn -q -pl carwatch-infrastructure -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='*VignettePageTest' test
```

**Step 4: Write failing gateway lifecycle/security tests**

Abstract browser/context creation behind a package-private factory so tests can prove:

- disabled/missing executable -> `HUMAN_REQUIRED`;
- global semaphore permits one active lookup;
- acquisition/timeout/exception always closes page, context, and Playwright;
- persistent profile path is fixed under configured data root;
- navigation rejects any origin outside exact HTTPS allowlist;
- log messages contain car ID and country but never the plate;
- no screenshot/video/trace calls exist in normal flow.

**Step 5: Implement conservative browser adapter**

Use `launchPersistentContext` with:

- headless Chromium;
- optional fixed `executablePath` for Raspberry Pi OS Chromium;
- `sk-SK` locale;
- `Europe/Bratislava` timezone;
- configured 45-second timeout;
- one new page and one submission attempt;
- normal JavaScript/resources, home IP, no account.

If challenge is present and no provider result is already available, return `HUMAN_REQUIRED` immediately. Do not attempt audio challenge or token manipulation. Convert selector drift to `SITE_CHANGED`, network/timeouts to `TEMPORARY_ERROR`, missing browser to `HUMAN_REQUIRED`.

**Step 6: Verify all infrastructure tests**

```bash
mvn -q -pl carwatch-infrastructure -am test
```

**Step 7: Commit**

```bash
git add pom.xml carwatch-infrastructure/pom.xml carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/vignette carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette carwatch-infrastructure/src/test/resources/vignette
git commit -m "feat: add official vignette browser adapter"
```

## Task 8: Record human official results and pending notifications

**Files:**

- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/ManualVignetteConfirmationCommand.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/ManualVignetteConfirmationService.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteManualActionNotificationService.java`
- Modify: `carwatch-domain/src/main/java/com/carwatch/domain/notification/NotificationLogRepository.java`
- Modify: notification JDBC repository/adapter files in `carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/persistence/`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/ManualVignetteConfirmationServiceTest.java`
- Test: `carwatch-application/src/test/java/com/carwatch/application/vignette/VignetteManualActionNotificationServiceTest.java`

**Step 1: Write failing manual-confirmation tests**

Command:

```java
public record ManualVignetteConfirmationCommand(
        Long carId,
        CountryCode country,
        LocalDate expiryDate,
        boolean noValidVignette
) {}
```

Reject both/neither result choices, unsupported/disabled country, and a nonsensical expiry input. For a date result:

- write a `SUCCESS` run with source `MANUAL_OFFICIAL`;
- update expiry/status and both checked timestamps;
- clear pending details;
- set next milestone.

For confirmed no-valid result:

- write a successful audited manual result;
- clear expiry;
- set explicit `EXPIRED` state with source `MANUAL_OFFICIAL`;
- clear pending;
- set `nextRunAt=null`.

Never set `manualOverride=true`; this is a recorded official lookup, not an override.

**Step 2: Implement and verify**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ManualVignetteConfirmationServiceTest test
```

**Step 3: Add pending-email tests**

On transition from non-pending to pending, send `CHECK_FAILURE` to recipients from `app.email.recipients`. Dedupe with a stable pending key, not just process memory. While state remains pending, later failures do not resend. After manual confirmation clears pending, a future transition may notify again. Empty recipients/email disabled remains non-fatal and is logged through existing notification conventions.

Add a repository method accepting the persisted string dedupe key; do not overload the existing `LocalDate` reminder method.

**Step 4: Integrate notification after obligation save and verify**

```bash
mvn -q -pl carwatch-application -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteManualActionNotificationServiceTest,ScheduleExecutionServiceTest test
```

**Step 5: Commit**

```bash
git add carwatch-application/src/main/java/com/carwatch/application/vignette carwatch-application/src/test/java/com/carwatch/application/vignette carwatch-domain/src/main/java/com/carwatch/domain/notification/NotificationLogRepository.java carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/persistence
git commit -m "feat: record manual vignette verification"
```

## Task 9: Add vignette cards, Check now, and human fallback MVC

**Files:**

- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteCardView.java`
- Create: `carwatch-application/src/main/java/com/carwatch/application/vignette/VignetteQueryService.java`
- Create: `carwatch-web/src/main/java/com/carwatch/web/controller/VignetteController.java`
- Create: `carwatch-web/src/main/java/com/carwatch/web/form/VignetteConfirmationForm.java`
- Create: `carwatch-web/src/main/resources/templates/cars/vignettes.html`
- Modify: `carwatch-web/src/main/resources/templates/cars/list.html`
- Modify: `carwatch-web/src/main/resources/messages_sk.properties`
- Modify: `carwatch-web/src/main/resources/messages_en.properties`
- Test: `carwatch-web/src/test/java/com/carwatch/web/controller/VignetteControllerWebMvcTest.java`
- Modify test: `carwatch-web/src/test/java/com/carwatch/web/controller/CarControllerWebMvcTest.java`

**Step 1: Write failing query-service tests**

Build cards only for enabled selections. Each card exposes country, status, expiry, last successful check, source, next run, pending flag, and exact official URL. Plate is view data only, never a log field.

**Step 2: Write failing MVC tests**

Routes:

```text
GET  /cars/{carId}/vignettes
POST /cars/{carId}/vignettes/{country}/check
POST /cars/{carId}/vignettes/{country}/confirm
```

Cover:

- cards for SK plus selected CZ/AT;
- disabled country -> 404/validation error;
- successful check redirects and shows updated date;
- CAPTCHA/cooldown/browser unavailable shows manual panel;
- panel has official link with `target="_blank"` and safe `rel="noopener noreferrer"`;
- plate copy button and fixed registration country `SK`;
- date vs no-valid validation;
- CSRF required for both POST routes;
- Slovak and English message resolution;
- no iframe is rendered.

**Step 3: Verify red**

```bash
mvn -q -pl carwatch-web -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteControllerWebMvcTest test
```

**Step 4: Implement controller/forms/template**

Use redirect-after-post and flash attributes. Do not make the official site link conditional on automation cooldown; manual link is always available. Add a `Vignettes` action beside Edit on the car list.

**Step 5: Verify web module**

```bash
mvn -q -pl carwatch-web -am test
```

**Step 6: Commit**

```bash
git add carwatch-application/src/main/java/com/carwatch/application/vignette carwatch-web/src/main/java/com/carwatch/web carwatch-web/src/main/resources carwatch-web/src/test/java/com/carwatch/web
git commit -m "feat: add vignette verification UI"
```

## Task 10: Add configuration and Raspberry Pi operations support

**Files:**

- Modify: `carwatch-boot/src/main/resources/application.yml`
- Modify: `carwatch-boot/src/main/resources/application-prod.yml`
- Create: `ops/install-vignette-browser.sh`
- Modify: `ops/run-prod.sh`
- Modify: `README.md`
- Test: `carwatch-boot/src/test/java/com/carwatch/boot/VignetteBrowserConfigurationTest.java`

**Step 1: Add failing configuration tests**

Test defaults and environment overrides for:

```yaml
carwatch:
  vignette:
    browser:
      enabled: true
      timeout: PT45S
      profile-directory: ${CARWATCH_HOME:/opt/carwatch}/data/browser-profile
      executable-path: ${CARWATCH_VIGNETTE_BROWSER_EXECUTABLE_PATH:}
    automated-check-cooldown: PT24H
    austria-daily-limit: 3
```

Validate positive timeout, cooldown, and limit. An empty executable path must be accepted and trigger discovery/fallback, not startup failure.

**Step 2: Implement config and verify**

```bash
mvn -q -pl carwatch-boot -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VignetteBrowserConfigurationTest test
```

**Step 3: Add Pi install script and documentation**

`ops/install-vignette-browser.sh` must:

- support 64-bit Raspberry Pi OS/Debian only;
- install distro `chromium` and required fonts through `apt-get` after explicit operator execution;
- locate the absolute binary path;
- create `/opt/carwatch/data/browser-profile` owned by `carwatch:carwatch` with mode `0700`;
- print the environment line for `/etc/carwatch/carwatch.env`;
- never run from application startup.

`run-prod.sh` should only pass configuration through. README must state Pi 4 8 GB is adequate for serialized occasional checks, automation may often fall back to a person, and Docker's current Alpine image does not support this Playwright path unless replaced with a supported browser image. Do not silently claim Docker browser support.

**Step 4: Shell-check scripts**

```bash
bash -n ops/install-vignette-browser.sh ops/run-prod.sh
```

Expected: no output, exit 0.

**Step 5: Commit**

```bash
git add carwatch-boot/src/main/resources/application.yml carwatch-boot/src/main/resources/application-prod.yml carwatch-boot/src/test/java/com/carwatch/boot/VignetteBrowserConfigurationTest.java ops/install-vignette-browser.sh ops/run-prod.sh README.md
git commit -m "ops: configure vignette browser on raspberry pi"
```

## Task 11: Add opt-in live smoke harness and full regression verification

**Files:**

- Create: `carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette/LiveVignetteLookupSmokeTest.java`
- Modify: `README.md`
- Modify tests as needed, but no production behavior expansion.

**Step 1: Add opt-in smoke test**

Guard with both:

```java
@Tag("live-vignette")
@EnabledIfEnvironmentVariable(named = "CARWATCH_LIVE_VIGNETTE_TEST", matches = "true")
```

Require plate and country via environment; accept `FOUND`, `NOT_FOUND`, `HUMAN_REQUIRED`, or `SITE_CHANGED` as a reachable-site result. Never assert CAPTCHA bypass. Do not include it in Surefire's normal test selection.

**Step 2: Verify it is skipped normally**

```bash
mvn -q -pl carwatch-infrastructure -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=LiveVignetteLookupSmokeTest test
```

Expected: skipped, no network call.

**Step 3: Run focused module verification**

```bash
mvn -q -pl carwatch-domain test
mvn -q -pl carwatch-application -am test
mvn -q -pl carwatch-infrastructure -am test
mvn -q -pl carwatch-web -am test
```

Expected: all `BUILD SUCCESS`, no live traffic.

**Step 4: Run full verification**

```bash
mvn clean verify
```

Expected: reactor `BUILD SUCCESS`; JaCoCo aggregate report generated; live vignette smoke skipped.

**Step 5: Inspect privacy and wiring mechanically**

```bash
rg -n "captcha.*solver|stealth|Google.*login|newPage\(.*http|screenshot|tracing|licensePlate.*logger" carwatch-* ops README.md
rg -n "VIGNETTE_(SK|CZ|AT)_CHECK" carwatch-infrastructure/src/main/java/com/carwatch/infrastructure/config/ProviderConfiguration.java
git status --short
```

Expected: no forbidden implementation; exactly one SK/CZ/AT provider each; only intended files changed and `.serena/` still untouched.

**Step 6: Optional manual smoke on Raspberry Pi**

Only after the user supplies a test plate and explicitly opts in:

```bash
CARWATCH_LIVE_VIGNETTE_TEST=true \
CARWATCH_LIVE_VIGNETTE_PLATE='<user-supplied>' \
CARWATCH_LIVE_VIGNETTE_COUNTRY=SK \
mvn -pl carwatch-infrastructure -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=LiveVignetteLookupSmokeTest test
```

Record only typed outcome and timing. Never paste the plate, cookies, tokens, or raw HTML into logs/issues.

**Step 7: Commit final tests/docs**

```bash
git add carwatch-infrastructure/src/test/java/com/carwatch/infrastructure/vignette/LiveVignetteLookupSmokeTest.java README.md
git commit -m "test: cover vignette verification end to end"
```

## Completion checklist

- SK exists and is enabled for every car; CZ/AT follow flags; HU behavior remains unchanged.
- Unknown expiry runs only through `Check now`.
- Known expiry schedules only T-14, T-7, T-1 at 06:00 Bratislava.
- Any failed/blocked lookup preserves last good expiry, status, source, and successful timestamp.
- CAPTCHA/rate limit/browser failure always provides official-link human fallback.
- AT automated attempts never exceed three per local day; manual checks remain available.
- One browser lookup at a time; persistent profile mode `0700`; no credentials or challenge bypass.
- Standard tests use sanitized fixtures only and make zero provider network calls.
- `mvn clean verify` passes.
