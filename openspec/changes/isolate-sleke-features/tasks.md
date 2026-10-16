# SLEKE module refactoring stages

The architecture and all three installation/access decisions were approved on 16 October 2026. The full task-by-task plan is `docs/superpowers/plans/2026-10-16-sleke-module-refactor-plan.md`. Its C/E/D/S task IDs define the implementation and evidence gates below. Plan review/execution-method selection precedes product changes.

## 1. Review the design

- [x] 1.1 Review the ownership model and capability scenarios.
- [x] 1.2 Confirm requested-install continuation after navigation and process recreation.
- [x] 1.3 Confirm preferred-installer handling for older installed versions and manual retries.
- [x] 1.4 Confirm enterprise access-loss behavior.
- [x] 1.5 Write and self-review the staged execution plan and exact test interfaces.
- [ ] 1.6 Review the completed plan and select execution method.

## 2. Characterize behavior and introduce contracts

- [ ] 2.1 Pin catalog source-field mapping, enrichment fallback and variable-size paging.
- [ ] 2.2 Pin enterprise/Play identity scopes, membership outcomes, cancellation and sign-out races.
- [ ] 2.3 Pin retained work identity, HTTP/archive/package validation and installer submission outcomes.
- [ ] 2.4 Pin installed/update eligibility branches and existing recovery boundaries.
- [ ] 2.5 Add domain models, mapping boundaries and application adapter bindings without reverse module dependencies.

## 3. Move presentation and enterprise sessions

- [ ] 3.1 Move SLEKE screens, state and view models into feature packages, with owned resources and stateless previews.
- [ ] 3.2 Keep host navigation and upstream update/storage/notification algorithms in the application.
- [ ] 3.3 Move Firebase session observation, membership and storage behind one enterprise-session contract.
- [ ] 3.4 Implement explicit logout scopes and awaited Play preparation, including stale-result invalidation.

## 4. Unify custom downloads

- [ ] 4.1 Use one coordinator with actual retained operation handles and side-effect-free observation.
- [ ] 4.2 Correct cancellation, partial-file cleanup, HTTP validation and requested/actual package verification.
- [ ] 4.3 Implement isolated IO staging and one preferred-installer adapter without automatic asynchronous-failure fallback.
- [ ] 4.4 Implement the reviewed continuation/update/access-loss defaults, including process-recovery and pending-confirmation cases.
- [ ] 4.5 Remove duplicated or verified dead Home/DownloadManager paths after caller and manifest checks.

## 5. Extract support and tighten dependencies

- [ ] 5.1 Extract catalog eligibility, preserving its consumer-specific truth table and retry semantics.
- [ ] 5.2 Extract guarded recovery, runtime initialization, diagnostics and remaining owned assets.
- [ ] 5.3 Narrow dependency exports, declare host dependencies explicitly and keep package/preference/database compatibility.
- [ ] 5.4 Enforce architecture checks and clear existing style violations without suppressing rules.

## 6. Validate the integrated refactor

- [ ] 6.1 Run JVM behavior tests and both module checks; build vanilla app and instrumentation APKs.
- [ ] 6.2 Check supported flavor/release compilation and install existing-data upgrades on Android.
- [ ] 6.3 Exercise email cancellation, navigation, lifecycle recovery, preferred installers, rejection and confirmation.
- [ ] 6.4 Independently review the complete branch and rehearse a reversible upstream merge.
- [ ] 6.5 Record exact evidence and remaining limits before preparing the implementation PR.
