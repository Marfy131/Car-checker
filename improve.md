# Car Watcher – Improvement Plan

A comprehensive review of the current codebase with prioritised improvements.
Each section is independent and ordered roughly by impact.

---

## 1. Domain Model – Boilerplate & Encapsulation

### Problem
All domain entities (`Car`, `CheckSchedule`, `ObligationState`, `InsurancePolicy`, `NotificationLog`, `CheckRunLog`) are pure anemic JavaBeans: `public` setters for every field, no invariant protection, no factory methods.
This means any layer can set any field to any value at any time — the domain layer provides zero safety guarantees.

### Proposed Improvements

| What | Where |
|------|-------|
| **Restrict setters.** Remove public setters from fields that should only be set at creation (`createdAt`, `id`) or via controlled transitions (`status`, `expiryDate`). Expose only package-private setters or domain methods. | All entities in `carwatch-domain` |
| **Add domain methods.** e.g. `ObligationState.markExpired(LocalDate)`, `CheckSchedule.claim(String owner, LocalDateTime lockUntil)`, `Car.deactivate()`. This moves state-transition logic into the entity and makes invalid states unrepresentable. | `carwatch-domain` entities |
| **Use builders or static factory methods** for complex construction, replacing scattered `new + setX()` chains in `CarManagementService.createCar()`. | `CarManagementService`, all seeded entities |
| **Override `equals`/`hashCode`/`toString`** on all entities — currently missing entirely. This causes subtle bugs with Collections and logging. | All domain entities |

---

## 2. `Car.version` — Missing `@Version`

### Problem
`Car` has a manual `version` field but is **not** annotated with `@Version`.
In contrast, `CheckSchedule` uses `@Version` correctly.

In `CarManagementService.updateCar()`, the code manually checks `existing.getVersion() != command.version()` and manually increments it.
This duplicates what Spring Data JDBC provides for free with `@Version`, and is subtly broken: because Spring Data JDBC sees no `@Version`, it performs an **upsert (INSERT ON CONFLICT)** on save, meaning the optimistic-locking check can be silently bypassed in edge cases.

### Fix
Add `@Version` to `Car.version` and remove the manual compare-and-increment in `updateCar()` and `deactivateCar()`. Let Spring Data JDBC throw `OptimisticLockingFailureException` natively.

---

## 3. Clock Injection – `LocalDateTime.now()` Scattered Everywhere

### Problem
13+ calls to `LocalDateTime.now()` / `LocalDate.now()` are hard-coded across services:
- `ScheduleDispatcher`, `ScheduleClaimService`, `ObligationUpdateService`, `ScheduleManagementService`, `SettingsController`, `CarVignetteSelectionRepositoryAdapter`

`ReminderNotificationService` is the *only* class that correctly injects a `Clock` — the rest are untestable for time-sensitive behaviour.

### Fix
Inject `Clock` into all services that use `now()`. Declare a `Clock.systemDefaultZone()` bean in a shared config and inject it. Tests can then provide `Clock.fixed(...)`.

Priority targets:
- `ScheduleDispatcher` — tests cannot control "now" for due-schedule checks
- `ScheduleClaimService` — lock-timeout logic is impossible to test deterministically
- `ObligationUpdateService` — expiry-status resolution uses `LocalDate.now()` directly

---

## 4. `SettingsController` Bypasses Architecture Boundary

### Problem
`SettingsController` directly injects `AppSettingRepository` (domain interface) and performs read + write logic in the controller, including timestamp management.
This violates the established pattern where **web → application → domain**, and puts business logic in the web layer.

### Fix
Create a `SettingsService` in `carwatch-application`, similar to how `CarManagementService` wraps `CarRepository`. The controller should only handle form binding and delegate to the service.

---

## 5. `HistoryController` & `NotificationController` Bypass Architecture

### Problem
Both controllers directly inject domain repository interfaces (`CheckRunLogRepository`, `NotificationLogRepository`) and call them from the web layer.
This breaks the dependency direction: `web → domain` without going through `application`.

### Fix
Introduce thin read-only services in `carwatch-application`:
- `CheckRunLogQueryService` (or `HistoryService`)
- `NotificationQueryService`

---

## 6. Missing Global Error Handling

### Problem
No `@ControllerAdvice` / `@ExceptionHandler` exists anywhere in the project.
Unhandled exceptions will render the default Spring Boot Whitelabel error page — unacceptable for a production-even-if-local app.

### Proposed
Create a `GlobalExceptionHandler` in `carwatch-web`:
- Map `ResponseStatusException` → appropriate Thymeleaf error template
- Map `OptimisticLockingFailureException` → friendly "concurrent edit" page
- Map generic `Exception` → 500 error page
- Create `error/404.html`, `error/500.html` in templates

---

## 7. Duplicate `CheckType → ObligationType` Mapping

### Problem
The mapping from `CheckType` to `ObligationType` is duplicated in **three** places:
1. `ObligationUpdateService.TYPE_MAPPING` (static `EnumMap`)
2. `ObligationStateVehicleCheckProvider.MAPPING` (static `Map.of()`)
3. `CarManagementService.resolveCheckTypes()` / `resolveObligationTypes()` (implicit)

Also `CarManagementService.resolveScheduleWarningDays()` manually maps CheckTypes to ints.

### Fix
Create a domain utility, e.g. `CheckTypeMapping`, exposing:
```java
public static Optional<ObligationType> toObligationType(CheckType ct) {...}
public static Optional<CheckType> toCheckType(ObligationType ot) {...}
public static Optional<CountryCode> toCountryCode(CheckType ct) {...}
```
Replace all three duplications. This also makes adding a new obligation type a single-point change.

---

## 8. `DailySummaryModel.CarSummaryLine` — 19-Field Record

### Problem
`CarSummaryLine` has **19 fields**. It is unwieldy, hard to extend, and error-prone to construct (wrong argument order is a silent bug).

### Proposed Refactor
Restructure into a nested model:
```java
record CarSummaryLine(
    String name, String licensePlate,
    ObligationSummary pzp, ObligationSummary collision,
    ObligationSummary stk, ObligationSummary ek,
    List<VignetteSummary> vignettes
)
record ObligationSummary(String expiry, String status)
record VignetteSummary(String country, boolean enabled, String expiry, String status)
```
This also simplifies the Thymeleaf template (loop over generic obligations instead of 18+ `th:text` expressions).

---

## 9. `ReminderNotificationService` — N+1 Queries in `sendDailyReminders`

### Problem
`sendDailyReminders()` iterates all obligation states and for each expiring/expired one calls:
1. `notificationLogRepository.existsByCarIdAndObligationTypeAndTypeAndDate(...)` — 1 query per state
2. `carRepository.findById(...)` — 1 query per state
3. `notificationLogRepository.save(...)` — 1 insert per state

For N expiring obligations, this is **3N + 1** queries.

### Fix
- Pre-load all car names by ID into a `Map<Long, Car>` once (already done in `sendDailySummary`)
- Pre-load today's notification logs for all cars in a single query and check dedupe in memory
- Batch-insert notification logs

---

## 10. Missing `@Transactional` on `updateCar` and `deactivateCar`

### Problem
`CarManagementService.updateCar()` and `deactivateCar()` perform:
1. `findById()` — read
2. Mutate fields
3. `save()` — write

Without `@Transactional`, these are separate transactions. A concurrent write between step 1 and step 3 causes a lost-update even with the manual version check, because the entity was read outside the transaction boundary.

### Fix
Add `@Transactional` to both methods (or to the entire service class).

---

## 11. Test Coverage Gaps

### Current State
| Module | Unit Tests | Integration Tests |
|--------|-----------|-------------------|
| `carwatch-application` | ✅ 7 test classes | — |
| `carwatch-domain` | ✅ 1 (EnumCoverageTest) | — |
| `carwatch-web` | ✅ 3 WebMvc + smoke | — |
| `carwatch-infrastructure` | ✅ 1 (SpringMailEmailSenderTest) | — |
| `carwatch-boot` | ✅ 1 smoke + 1 IT (ScheduledWorkflowProviderIT) | Mailpit IT |

The plan targets 90% line coverage. The current coverage threshold is **10% line / 5% branch** — effectively disabled.

### Gaps to Fill
- **`CarManagementService`:** test vignette/schedule seeding edge cases (empty set, all countries, duplicate create)
- **`ScheduleClaimService`:** test lock expiry, concurrent claim, release semantics (needs Clock injection first)
- **`ObligationUpdateService`:** test all status transitions including edge case where `expiryDate == today`
- **Persistence adapters:** no tests at all — need SQLite-based `@DataJdbcTest` tests for all repository adapters
- **Controller tests:** `ScheduleController`, `SettingsController`, `HistoryController`, `NotificationController` have zero test coverage
- **`DashboardController`:** untested (though trivial)

### Recommended
Gradually raise JaCoCo thresholds as tests are added:
```xml
<jacoco.coverage.line.minimum>0.50</jacoco.coverage.line.minimum>
<jacoco.coverage.branch.minimum>0.30</jacoco.coverage.branch.minimum>
```

---

## 12. Security — Minimal Hardening

### Problem
The plan says "no authentication needed" but provides zero protection. Several easy wins exist:

| Risk | Fix |
|------|-----|
| CSRF on mutation endpoints | Already handled by Spring's default CSRF if Spring Security is on classpath; verify it's active |
| No input sanitization | VIN and license plate pass through `@Pattern` validation — good. But `notes`, `insurerName`, `policyNumber` accept arbitrary text with no length limit and could be stored unsanitized. |
| SQLite file accessible to any process | Set restrictive file permissions in `run-prod.sh` |
| No rate limiting on form submissions | Acceptable for local-network, but note the risk |

---

## 13. `ScheduleDispatcher` — Sequential Execution

### Problem
Due schedules are processed **sequentially** in `pollAndExecute()`. If one check provider blocks (network timeout), all subsequent due checks are delayed.

### Proposed
Use a bounded `ExecutorService` (2 threads max, per the plan). Submit each `processSchedule` as a task. This also protects against one slow provider blocking other timely checks.

Ensure thread-safety in:
- `ScheduleClaimService` (already safe — per-row locking)
- `RunLoggingService` (stateless)
- `ObligationUpdateService` (stateless)

---

## 14. Configuration Improvements

### Externalize Magic Numbers
Several magic numbers are hardcoded:
- `ScheduleClaimService`: lock TTL = 5 minutes (`now.plusMinutes(5)`)
- `ScheduleDispatcher`: poll interval = 30 seconds (`fixedDelay = 30000`)
- `HistoryController`: page size = 100
- `NotificationController`: page size = 100
- `ReminderNotificationService`: recent runs = 10

Move all to `application.yml` under `carwatch.*` keys with sensible defaults.

### Missing `application-test.yml`
The integration test uses `@ActiveProfiles("test")` but there is no `application-test.yml`. The test relies on the default profile's config, which points to `data/carwatch.db` — a **real file on disk**. Each test run pollutes production data or fails depending on filesystem state.

**Fix:** Create `application-test.yml` with an in-memory (or per-test-temp-file) SQLite database:
```yaml
spring:
  datasource:
    url: jdbc:sqlite::memory:
carwatch:
  scheduler:
    enabled: false
```

---

## 15. Observability Improvements

### Problem
Only `ScheduleDispatcher` and `ScheduleClaimService` have logging. Other critical paths are silent:
- `CarManagementService.createCar()` — creates 6+ DB records but logs nothing
- `ObligationUpdateService.updateFromOutcome()` — changes obligation status silently
- `CheckExecutor.execute()` — no log of what provider was invoked
- `ScheduleManagementService.runNow()` — manual run trigger is unlogged

### Fix
Add structured `logger.info()` calls at service method entry/exit with key business context (carId, checkType, outcome status). For error paths, add `logger.error()` with exception context.

### Metrics
Consider adding a few `Counter` / `Timer` metrics via Micrometer (already on classpath via Spring Boot):
- `carwatch.checks.total` (counter, tagged by checkType and status)
- `carwatch.checks.duration` (timer, tagged by checkType)
- `carwatch.notifications.sent` (counter, tagged by type and status)

---

## 16. Database & Schema

### Missing `created_at` triggers
`car`, `obligation_state`, `insurance_policy` all have `DEFAULT CURRENT_TIMESTAMP` for `created_at`, but the application never sets `createdAt` on new entities.
Spring Data JDBC *may* pass `null` for these fields, causing SQLite to use the default — but this depends on the dialect behaviur. Safer to set it explicitly in application code or a Spring Data JDBC `BeforeSaveCallback`.

### No migration versioning path
All tables are in `V1__init.sql`. Future schema changes will require a `V2__*.sql` migration.
Document this convention clearly and consider adding a `V2__add_indexes.sql` placeholder early to establish the pattern for collaborators.

### `notification_log.dedupe_date` is `TEXT`
This should ideally be an index-backed lookup column. The current `idx_notification_log_dedupe` index covers it, but storing dates as `TEXT` means the deduplication query depends on consistent date formatting. Consider using `strftime('%Y-%m-%d', ...)` in the constraint or at least documenting the expected format.

---

## 17. Docker & Deployment

### Dockerfile Improvements
- **Volume for data:** The `data/` and `logs/` directories are created inside the image but have no `VOLUME` declaration. Data will be lost when the container is recreated.
  ```dockerfile
  VOLUME ["/opt/carwatch/data", "/opt/carwatch/logs"]
  ```
- **Health check missing:**
  ```dockerfile
  HEALTHCHECK --interval=30s --timeout=5s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
  ```
- **Non-root user:** Currently runs as root inside the container. Add:
  ```dockerfile
  RUN addgroup -S carwatch && adduser -S carwatch -G carwatch
  USER carwatch
  ```

---

## 18. Code Quality — Miscellaneous

| Issue | Location | Suggestion |
|-------|----------|------------|
| `CheckExecutor` merge strategy `(left, right) -> right` silently overwrites duplicate providers | `CheckExecutor` constructor | Throw `IllegalStateException` on duplicate `CheckType` registration |
| `@Autowired` on constructor is unnecessary (single constructor) | `ReminderNotificationService` | Remove `@Autowired` |
| `sendImmediateReminderForState` is package-private but called via `this.` inside `sendDailyReminders` — works, but confusing for readers | `ReminderNotificationService` | Make it `private` if only called internally |
| `providerMessageId` is overloaded: stores message ID on success, error message on failure | `NotificationLog` | Add a separate `errorDetail` field or use `message` field |
| `detailsJson` and `findingsJson` are stored as raw `String` — no validation, no size limit | Domain entities | Consider a max-length check or document expected format |
| `humanObligation()` uses hardcoded Slovak strings | `ReminderNotificationService` | Move to message bundles for consistency with i18n strategy |

---

## 19. Summary — Priority Matrix

| Priority | Item | Risk | Effort |
|----------|------|------|--------|
| 🔴 Critical | #2 `Car` missing `@Version` | Data corruption | Low |
| 🔴 Critical | #10 Missing `@Transactional` on update/deactivate | Lost updates | Low |
| 🔴 Critical | #14 Missing `application-test.yml` | Test pollution | Low |
| 🟠 High | #3 Clock injection everywhere | Untestable time logic | Medium |
| 🟠 High | #7 Duplicate type mappings | Maintenance landmine | Medium |
| 🟠 High | #6 Global error handler | UX/production readiness | Medium |
| 🟡 Medium | #4, #5 Controller architecture violations | Debt accumulation | Medium |
| 🟡 Medium | #9 N+1 queries in reminders | Performance at scale | Medium |
| 🟡 Medium | #11 Test coverage gaps | Quality confidence | High |
| 🟡 Medium | #15 Logging & metrics gaps | Operational blindness | Medium |
| 🟢 Low | #1 Domain model encapsulation | Long-term quality | High |
| 🟢 Low | #8 19-field record refactor | Readability | Medium |
| 🟢 Low | #13 Parallel schedule execution | Latency under load | Medium |
| 🟢 Low | #16 Schema & migration hygiene | Future-proofing | Low |
| 🟢 Low | #17 Docker hardening | Ops quality | Low |
| 🟢 Low | #18 Misc code quality | Polish | Low |
