# Code Quality and Architecture Improvements

This plan outlines refactoring strategies to address technical debt, optimize performance during scheduled tasks, and improve code readability in the core of the `Car-checker` application.

## Proposed Changes

---

### Notification & Workflow Services (Performance & Clean Code)

We are currently fetching ALL application settings (`findAll()`) from the database and iterating over them in loops just to resolve single keys (like recipients or locale). We can utilize the existing `findByKey` method in the repository for direct indexed lookup. 

Additionally, the `ReminderNotificationService` contains a "self proxy injection" anti-pattern which provides zero benefit because both caller and callee methods share standard `REQUIRED` transaction propagation. 

#### [MODIFY] [ScheduledWorkflowProvider.java](file:///C:/Users/dekan/IdeaProjects/Car-checker/carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduledWorkflowProvider.java)
- Add SLF4J `Logger` to log warning messages when skipping execution due to missing email recipients or layout configurations.
- Optimize setting resolution to use `appSettingRepository.findByKey(...)` instead of reading all settings via `findAll()`.
- Fetch `app.email.recipients` and `app.default-locale` securely and quickly.
- Remove `resolveLocale()` helper and inline the lookup.

#### [MODIFY] [ReminderNotificationService.java](file:///C:/Users/dekan/IdeaProjects/Car-checker/carwatch-application/src/main/java/com/carwatch/application/notification/ReminderNotificationService.java)
- Remove `setSelf` proxy injection and the unused `self` field. Replace `self.sendImmediateReminderForState` with standard `this.sendImmediateReminderForState`.
- To prevent SonarQube `S6809` warnings (calling a `@Transactional` method from the same class), remove the unnecessary `@Transactional` annotation from `sendImmediateReminderForState` and reduce its visibility to package-private. The method correctly inherits the active transaction from `sendDailyReminders`.
- Delete `resolveRecipientsFromSettings` since `ScheduledWorkflowProvider` will now do it directly.
- Refactor the 60+ line `buildSummaryModel` into smaller, readable private helper methods (`aggregateCarStates`, `buildCarSummaryLines`).

---

### Core Scheduler (Observability & Logging)

The main scheduler loop silently swallows exception blocks with empty catch statements. This is dangerous because it hides vital infrastructure issues (like database disconnections during polling). 

#### [MODIFY] [ScheduleDispatcher.java](file:///C:/Users/dekan/IdeaProjects/Car-checker/carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleDispatcher.java)
- Add SLF4J `Logger`.
- Add proper `logger.error("Failed to process schedule ...", ex)` inside the ignored `catch` blocks. 

#### [MODIFY] [ScheduleClaimService.java](file:///C:/Users/dekan/IdeaProjects/Car-checker/carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleClaimService.java)
- Add SLF4J `Logger`.
- Log the `OptimisticLockingFailureException` occurrences at the `DEBUG` or `TRACE` level, so the runtime state remains visible. 

## Verification Plan

### Automated Tests
1. **Mailpit Testcontainers Integration Test**:
   - Add `testcontainers-bom` to the project root `pom.xml`.
   - Add `testcontainers` and `junit-jupiter` dependencies to `carwatch-boot` (or `carwatch-infrastructure`).
   - Create a new integration test (e.g., `ScheduledWorkflowProviderIT.java` or `EmailSenderIT.java`).
   - Configure a `GenericContainer` for `axllent/mailpit` exposing ports `1025` (SMTP) and `8025` (HTTP/API).
   - Use `@DynamicPropertySource` to point `spring.mail.host` and `spring.mail.port` to the Mailpit container.
   - Assert that the email actually arrives in Mailpit by optionally querying the Mailpit REST API (`/api/v1/messages`), or just verifying the SMTP accepts it without throwing exceptions.
2. Run `mvn clean verify` to ensure unit tests continue to pass and `ReminderNotificationServiceTest` works with the removed lazy proxy injection.
