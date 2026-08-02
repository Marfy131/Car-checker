# Online Vignette Validity Check Design

**Date:** 2026-08-02
**Status:** Approved

## Context

Car Watcher already creates vignette selections, obligation states, and schedules for Slovakia, Czechia, and Austria. The registered `VIGNETTE_*_CHECK` providers currently reuse manually stored obligation dates instead of querying official systems.

All vehicles managed by this deployment are registered in Slovakia. Every vehicle must track a Slovak vignette. Czech and Austrian tracking remain optional per vehicle. Hungarian vignette behavior is outside this change.

The application runs on a Raspberry Pi 4 with 8 GB RAM and a 64-bit operating system. Occasional Playwright/Chromium execution is acceptable, but browser processes must not remain active unnecessarily.

## External System Findings

All three countries provide public validity forms, but no stable, documented, free integration API was found:

- Slovakia: `https://eznamka.sk/selfcare/modification/select/select-vignettes/?operation=Check`; Google reCAPTCHA; embedding blocked with `X-Frame-Options: SAMEORIGIN`.
- Czechia: `https://edalnice.gov.cz/en`; Cloudflare Turnstile; embedding blocked with `X-Frame-Options: SAMEORIGIN`.
- Austria: `https://evidenz.asfinag.at/en/`; MTCaptcha; embedding blocked with `X-Frame-Options: DENY`; ASFINAG allows at most three queries per device per day and exposes only digital products.

Direct iframes are therefore not viable. Re-proxying pages to remove browser security headers is intentionally excluded because it would be fragile, interfere with cookies and CAPTCHA validation, and weaken external-site protections.

Free or free-tier CAPTCHA solvers exist, but they intentionally bypass anti-automation systems, do not reliably cover all three providers, and add privacy and service-blocking risk. Car Watcher will not use CAPTCHA solvers, stealth plugins, fingerprint spoofing, rotating proxies, or automated Google login.

## Goals

- Attempt free online vignette lookup through each official site.
- Use a clear human-in-the-loop fallback whenever automated lookup cannot complete.
- Never erase a last known good expiry date because an external check failed.
- Run external checks only when explicitly requested or at 14, 7, and 1 day before known expiry.
- Keep browser resource use suitable for a Raspberry Pi 4.
- Preserve the existing module dependency direction and DB-backed audit trail.

## Non-Goals

- Bypassing CAPTCHA or provider rate limits.
- Purchasing, renewing, or modifying vignettes.
- Checking Austrian adhesive vignettes or Austrian section toll products.
- Adding Hungarian online lookup.
- Running live provider checks in CI.

## User Experience

Each car displays a vignette card for every enabled country. The card shows:

- country;
- current status and expiry date;
- last successful check;
- source of the current value;
- next scheduled online check;
- pending manual-action warning, if any;
- a `Check now` action.

Slovak tracking is always enabled and cannot be unchecked. Czech and Austrian tracking are controlled by vehicle form checkboxes. Disabling an optional country disables its schedule but retains its obligation state and history.

`Check now` first runs the same online-check workflow used by the scheduler. When Playwright cannot complete because of CAPTCHA, site changes, browser availability, or the Austrian daily limit, Car Watcher displays:

- the official validity-check link in a new browser tab;
- the vehicle registration plate with a copy button;
- fixed registration country `SK`;
- an expiry-date input;
- a `No valid vignette found` option.

The official sites cannot be embedded directly. A user manually completes the official form, returns to Car Watcher, and records the result. This result is audited as `MANUAL_OFFICIAL`.

## Architecture

### Domain

Existing `CheckType`, `ObligationType`, `CheckSchedule`, `ObligationState`, `CheckOutcome`, and `VehicleCheckProvider` types remain the core scheduler contract. No external browser dependency enters the domain module.

Persistent run status continues to use the existing `SUCCESS`, `WARNING`, `ERROR`, and `NO_DATA` values. Detailed lookup state is typed inside the application layer and stored in findings/details JSON where required. This avoids adding a persistence enum solely for a UI action state.

### Application

Add these application components:

- `VignetteLookupGateway`: external lookup port.
- `VignetteLookupResult`: typed result with `FOUND`, `NOT_FOUND`, `HUMAN_REQUIRED`, `SITE_CHANGED`, and `TEMPORARY_ERROR` outcomes.
- `OnlineVignetteVehicleCheckProvider`: replaces the manual-first vignette provider registrations for SK, CZ, and AT.
- `VignetteCheckService`: common execution flow used by the scheduler and manual `Check now`.
- `VignetteSchedulePlanner`: calculates explicit milestone timestamps.
- `ManualVignetteConfirmationService`: validates and records a human-confirmed official result.
- `VignetteLookupRateLimitService`: enforces per-vehicle cooldown and the global Austrian daily limit from persisted run history.

The existing execution pipeline should be extracted so scheduled and user-triggered checks share command creation, provider execution, run logging, obligation update, and next-run planning.

### Infrastructure

Implement `VignetteLookupGateway` with Playwright Java and country-specific page objects:

- `SkVignettePage`;
- `CzVignettePage`;
- `AtVignettePage`.

The adapter uses a dedicated persistent Chromium profile so legitimate cookies and cache survive between rare runs. It uses normal Chromium headless mode, JavaScript, images, fonts, a Slovak locale, and the `Europe/Bratislava` timezone. It does not alter automation fingerprints.

Only one browser lookup may run at a time. A global semaphore protects the persistent profile and limits Raspberry Pi resource use. Each attempt starts Chromium, performs one lookup within a configured timeout, and closes all Playwright resources.

Provider URLs are fixed in configuration/code and restricted to an allowlist. User input never controls navigation targets.

### Web

Add MVC routes and Thymeleaf fragments for:

- country vignette cards on the vehicle view/list;
- `Check now`;
- pending manual verification;
- manual result confirmation;
- Czech/Austrian selection editing.

All user-facing text is added to both Slovak and English message bundles. Backend identifiers and logs remain English.

## Lookup Flow

1. Scheduler or user invokes `VignetteCheckService` for a car and country.
2. Service validates that the country is enabled and that automated rate limits allow a request.
3. Gateway launches the official country page and enters the Slovak registration country and vehicle plate.
4. If the provider produces a result without human interaction, the page object parses it.
5. A valid digital product returns `FOUND` with its expiry date and sanitized provider metadata.
6. CAPTCHA or required interaction returns `HUMAN_REQUIRED` without attempts to solve it.
7. Missing expected page elements returns `SITE_CHANGED`.
8. Network, timeout, or browser failures return `TEMPORARY_ERROR`.
9. Service logs the run, safely updates the obligation, and plans the next milestone.

For Austria, the page object selects only the digital vignette product and ignores digital section toll. When a provider shows multiple relevant vignette periods, the adapter stores the latest relevant end date and retains sanitized periods in findings JSON.

## Human Confirmation Flow

1. A non-automatic outcome creates a persistent pending-action marker in obligation details and a warning run log.
2. Dashboard displays an action badge and official external link.
3. User performs the official check in a normal browser and returns with either an expiry date or `No valid vignette found`.
4. `ManualVignetteConfirmationService` validates the input and enabled country.
5. It writes a successful audited check with source `MANUAL_OFFICIAL`, updates the obligation, clears the pending marker, and recalculates `next_run_at`.

Email notification uses the existing `CHECK_FAILURE` type and is deduplicated for the pending state. Daily summary/dashboard continue to expose expiry warnings independently.

## Scheduling

Vignette schedules use explicit `next_run_at` values instead of daily external-check cron execution.

For known expiry date `D`, `VignetteSchedulePlanner` selects the earliest future milestone at 06:00 Europe/Bratislava:

- `D - 14 days`;
- `D - 7 days`;
- `D - 1 day`.

After a milestone attempt, the planner selects the next strictly later milestone. If Car Watcher was offline, a past `next_run_at` remains due and executes once after startup. After the final milestone, no further automatic lookup is scheduled until a new expiry date is recorded or the user invokes `Check now`.

Unknown expiry produces `next_run_at = null`; discovery occurs only through `Check now`.

A `HUMAN_REQUIRED`, `SITE_CHANGED`, or temporary failure never creates a retry loop. The known expiry and status remain intact, the pending action remains visible, and the next normal milestone is scheduled.

## Vehicle Selection Rules

On vehicle creation:

- always create and enable SK vignette selection, obligation, and schedule;
- create CZ/AT selections, obligations, and schedules only when selected;
- retain HU behavior outside this feature.

On vehicle edit:

- SK remains enabled;
- enabling CZ/AT creates or re-enables their selection, obligation, and schedule;
- disabling CZ/AT disables their selection and schedule without deleting obligation state or audit history.

Existing vehicles are backfilled with enabled SK selection, obligation, and schedule. Database constraints and fresh-install assumptions in `V1__init.sql` remain synchronized with any new Flyway migration required for existing databases.

## Rate Limits

- Automated `Check now` has a persisted 24-hour cooldown per car and country.
- The manual official-site link is always available.
- Austrian automated queries are limited to three per Europe/Bratislava calendar day across the entire application.
- When the Austrian limit is exhausted, scheduled work moves to the next day and user-triggered work immediately offers human fallback.
- Browser attempts are serialized for all countries.

Rate-limit decisions use persisted check-run history so application restarts cannot reset them.

## Error and State Rules

- `FOUND`: store expiry, successful timestamp, source, details, and calculated status.
- `NOT_FOUND`: never erase a known future expiry; mark warning and require confirmation. With no future known expiry, retain an explicit unknown/expired result according to the manual confirmation.
- `HUMAN_REQUIRED`: preserve the last good date and status; set pending action.
- `SITE_CHANGED`: preserve data, log an error, set pending action, and stop automated parsing for that attempt.
- `TEMPORARY_ERROR`: preserve data and expose fallback; do not retry aggressively.
- Browser-disabled/unavailable: act as human-required rather than failing the whole application.

Any non-successful lookup must preserve `expiry_date` and `last_successful_check_at`.

## Security and Privacy

- Do not use CAPTCHA solvers, Google login, browser stealth packages, proxies, or challenge-token services.
- Store the browser profile below the Car Watcher data directory with filesystem mode `0700`.
- Do not store screenshots, page HTML, video, or Playwright traces by default.
- Log car ID and country, not the registration plate.
- Sanitize findings JSON; never persist cookies, CAPTCHA tokens, antiforgery tokens, or full provider responses.
- Apply CSRF protection and server-side validation to manual confirmation routes.
- Permit navigation only to the three configured official HTTPS origins.

## Configuration and Operations

Add settings similar to:

```yaml
carwatch:
  vignette:
    browser:
      enabled: true
      timeout: PT45S
      profile-directory: ${CARWATCH_HOME:/opt/carwatch}/data/browser-profile
    automated-check-cooldown: PT24H
    austria-daily-limit: 3
```

Production setup installs only Chromium and required Playwright OS dependencies. Installation belongs in deployment documentation/scripts, not application startup. A missing browser degrades to manual verification.

## Testing

- Unit-test each page parser with sanitized captured HTML fixtures; never call official sites in normal tests.
- Use a fake `VignetteLookupGateway` to test every application result and state transition.
- Test milestone boundaries with a fixed `Clock`, including missed dates, replacement expiry, and completion after `D - 1`.
- Test that all failure results preserve the last successful expiry.
- Test the per-vehicle cooldown and persisted Austrian three-per-day limit.
- Test manual confirmation validation, audit logging, pending-action clearing, and schedule recalculation.
- Add MVC tests for country cards, flags, `Check now`, fallback, and both locales.
- Add SQLite integration tests for migration/backfill and schedule uniqueness.
- Add provider-wiring tests to reject duplicate provider registrations.
- Keep an opt-in live smoke check disabled by default and excluded from CI; it may run only with a user-supplied plate and explicit configuration.
- Run `mvn clean verify` before completion.

## Operational Risks

- Official DOM or challenge behavior can change without notice. Country page objects isolate repairs.
- Playwright may be identified as automation, making human fallback common, especially for Slovak and Austrian forms.
- The official result remains authoritative; Car Watcher stores a convenience copy and audit trail, not legal proof.
- Persistent profiles improve continuity but must never contain unrelated accounts or credentials.
