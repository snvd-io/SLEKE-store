# SLEKE module architecture proposal

## Why

SLEKE product behavior is spread across the reusable library and frequently changing Aurora screens, authentication, installers, workers and view models. The 4.8.4 integration required ten conflict resolutions; feature ownership and stable integration contracts will make future synchronizations easier to review and maintain.

## What Changes

- Consolidate SLEKE catalog, enterprise presentation, sessions, custom-download coordination, update eligibility, recovery and runtime support inside `:sleke` with feature-based data, domain and presentation layers.
- Retain thin adapters in `:app` for Play metadata/authentication, Aurora installers, navigation and Android packaging. Keep the compile-time dependency `:app → :sleke`.
- Correct catalog enrichment fallback and pagination, retained download-work identity, package validation, cancellation and observer ownership, with regression scenarios.
- Define independent enterprise and Play session scopes, including explicit full sign-out and stale-operation handling.
- Preserve the current installed-list/update filtering differences and prevent catalog-fetch failures from broadening update eligibility.
- Reduce dependency exports, remove verified unused paths, enforce module boundaries and resolve existing style debt in affected SLEKE code.
- Keep behavior changes around installation continuation and updates explicitly reviewable before implementation.

## Capabilities

### New Capabilities

These capabilities document existing product behavior and the specified reliability corrections in a repository that previously had no OpenSpec capability inventory.

- `sleke-catalog`: Catalog fidelity, optional Play enrichment, stable paging and feature navigation.
- `enterprise-session`: Independent enterprise identity, current-user membership, lifecycle-safe reconciliation and sign-out scopes.
- `custom-apk-downloads`: Validated, observable download operations and controlled preferred-installer handoff.
- `catalog-update-eligibility`: Installed-list and update-mode behavior, catalog failure handling and scoped GMS exclusion.
- `app-details-recovery`: Retry, restart and restart-loop guard behavior preserved during extraction.
- `runtime-branding`: Stable product identity, runtime initialization, diagnostics and reusable presentation.

### Modified Capabilities

None. There are no existing OpenSpec capability files.

## Impact

The work affects the two existing Gradle modules, the SLEKE presentation files currently in `:app`, their host adapters, Firebase session/repository code and custom download workers. Application ID `com.sleke.store`, version `76 / 4.8.4`, database version 13, stored preference keys, Android signing, upstream installer implementations and backend deployment configuration remain compatible. No new application framework or Gradle module is required.

The specification and web reader are the deliverables of this phase. Product implementation follows review of the completed design.
