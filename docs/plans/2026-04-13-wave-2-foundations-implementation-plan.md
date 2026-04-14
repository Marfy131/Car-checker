# Wave 2 Foundations Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce time-related test brittleness and eliminate duplicated `CheckType` mapping logic without widening into controller/service architecture changes.

**Architecture:** Execute two serial tasks. First, centralize `CheckType` mapping in one reusable domain utility and replace duplicated mapping logic. Second, inject a shared `Clock` into the schedule-layer services that currently hard-code `now()`, plus their focused tests. The tasks are serial because both touch `ObligationUpdateService`.

**Tech Stack:** Spring Boot, Spring Data JDBC, Java Time `Clock`, JUnit 5, Mockito

---

### Task 1: Centralize `CheckType` mapping

**Files:**
- Create: `carwatch-domain/src/main/java/com/carwatch/domain/schedule/CheckTypeMapping.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ObligationUpdateService.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ObligationStateVehicleCheckProvider.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/car/CarManagementService.java`

**Required outcome:**
- Replace duplicated `CheckType -> ObligationType` logic with a single reusable mapping utility.
- Expose at least obligation lookup and vignette-country lookup from that utility.
- Reuse it from `ObligationUpdateService`, `ObligationStateVehicleCheckProvider`, and `CarManagementService`.
- Keep behavior unchanged.

### Task 2: Inject `Clock` into schedule services

**Files:**
- Create: one shared Spring `Clock` bean in a configuration class under an existing scanned package
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleDispatcher.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleClaimService.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ObligationUpdateService.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/schedule/ScheduleManagementService.java`
- Modify: `carwatch-application/src/test/java/com/carwatch/application/schedule/ScheduleDispatcherTest.java`
- Modify: `carwatch-application/src/test/java/com/carwatch/application/schedule/ObligationUpdateServiceTest.java`

**Required outcome:**
- Remove hard-coded `LocalDateTime.now()` / `LocalDate.now()` from the listed schedule services.
- Inject and use a shared `Clock`.
- Keep runtime behavior unchanged under the default system clock.
- Update focused tests so time-sensitive assertions can use fixed clock values where needed.
