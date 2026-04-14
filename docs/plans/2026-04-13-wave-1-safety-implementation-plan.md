# Wave 1 Safety Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Land the highest-risk fixes from `improve.md` without creating merge conflicts or changing broader behavior.

**Architecture:** Execute two isolated tasks. First, move `Car` optimistic locking to native Spring Data JDBC semantics and make update/deactivate transactional. Second, add a dedicated `test` profile so integration tests stop touching the default SQLite database.

**Tech Stack:** Spring Boot, Spring Data JDBC, SQLite, JUnit 5, Mockito

---

### Task 1: Native optimistic locking for `Car`

**Files:**
- Modify: `carwatch-domain/src/main/java/com/carwatch/domain/car/Car.java`
- Modify: `carwatch-application/src/main/java/com/carwatch/application/car/CarManagementService.java`
- Modify: `carwatch-application/src/test/java/com/carwatch/application/car/CarManagementServiceTest.java`
- Check impact only: `carwatch-web/src/main/java/com/carwatch/web/controller/CarController.java`

**Required outcome:**
- Add Spring Data JDBC `@Version` to `Car.version`.
- Remove manual version compare/increment logic from `updateCar()` and `deactivateCar()`.
- Add `@Transactional` to `updateCar()` and `deactivateCar()`.
- Keep controller behavior compatible with `OptimisticLockingFailureException`.

### Task 2: Dedicated test profile

**Files:**
- Create: `carwatch-boot/src/main/resources/application-test.yml`
- Check impact only: `carwatch-boot/src/test/java/com/carwatch/boot/ScheduledWorkflowProviderIT.java`
- Check impact only: `carwatch-boot/src/main/resources/application.yml`

**Required outcome:**
- Add a `test` profile config that does not point at `data/carwatch.db`.
- Disable scheduler execution in tests via config.
- Preserve compatibility with existing `@ActiveProfiles("test")` usage.
