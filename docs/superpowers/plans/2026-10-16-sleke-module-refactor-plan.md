# SLEKE Module Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move SLEKE behavior into the existing library, correct the approved reliability defects, and leave small, documented integration points for future upstream merges.

**Architecture:** Keep `:app → :sleke`. Feature domain contracts are plain Kotlin; Firebase, WorkManager and file implementations live in library data packages. The app provides Play, installer, navigation and packaging adapters. Preserve upstream orchestration and installer algorithms.

**Tech Stack:** Kotlin, Compose, ViewModel/Flow, Hilt/KSP, Firebase, Paging 3, WorkManager, Ktor, existing Aurora installers; JUnit 4, coroutines-test and Android instrumentation.

**Spec:** [Approved architecture](../specs/2026-10-16-sleke-module-refactor-design.md), approved 16 October 2026; normative scenarios in `openspec/changes/isolate-sleke-features/specs/`.

Implementation plan · Ready for review · 16 October 2026

## Global Constraints

- Retain application ID `com.sleke.store`, versionCode 76, versionName `4.8.4`, minimum SDK 26 and database version 13.
- Preserve existing preference keys, signing configuration, product flavors, source ordering, branding, copyright and license notices.
- Keep exactly two Gradle modules. No `:sleke → :app` dependency, Aurora implementation imports in library code, or Android/network/Compose types in feature domain contracts.
- Keep ViewModel, Flow, Compose, Hilt and WorkManager. Add test libraries aligned to existing versions; introduce no new application framework or persisted metadata cache.
- Preserve the 300 ms search debounce, at most four concurrent metadata requests, one-second recovery retry and 60-second restart guard, including the exact boundary.
- Continue requested custom installs after navigation/process recreation; update older installed APKs and use the preferred installer for manual retries. Never automatically reinstall an equal/newer installed version.
- On enterprise sign-out, identity change or confirmed revocation, cancel unfinished work and purge unsubmitted private artifacts. Already accepted installation is reconciled and cancelled only where supported; never uninstall as a sign-out side effect.
- A membership outage permits the regular catalog once Play is ready, blocks new enterprise requests/handoffs and retains owner-scoped validated files pending explicit refresh.
- Keep Installed catalog intersection, Updates' default-false catalog preference and its existing branch differences. Exclude GMS throughout the automated update path while preserving manual microG entry.
- No backend deployment, release publication, database reset, certificate-policy expansion, attribution additions or unrelated product redesign.

## Review Focus

Each risk below has a named regression in its owning task, in addition to the 53 OpenSpec scenarios.

1. Process death between installer acceptance and receipt persistence must lead to reconciliation, never an automatic second submission: D3/D4 `unknownHandoffDoesNotReplay`.
2. A public and enterprise request for the same package must not expose one identity's retained files or state to another: D1/D4 `retainedWorkNeverCrossesOwner`.
3. A Play download already owns the package/version directory; custom staging must leave its split files untouched: D3 `foreignSplitSetIsNeitherInstalledNorDeleted`.
4. A slow membership/Play completion after full sign-out must not persist an account, membership or navigation effect: E2/E3 `lateCompletionAfterFullSignOutIsDiscarded`.
5. An obsolete search has a shared metadata request when the account/device/locale context changes; its completion must not enter the new context: C2 `contextChangeDiscardsSharedCompletion`.

---

## Delivery order and review units

Four linked plans keep each subsystem readable. Task IDs are stable across the plan, OpenSpec checklist and validation record. Each task ends with a green focused suite and a small commit. Setup belongs to its first consuming task; no separate scaffold-only PR.

| Stage | Tasks, in order | Reviewable deliverable |
| --- | --- | --- |
| 1. Baseline and catalog contracts | C1 | Recorded baseline, pure catalog types and real test dependencies |
| 2. Session and download contracts | E1 → D1 | Explicit session, operation and installer boundaries with pure decision tests |
| 3. Metadata and paging | C2 | Correct metadata fallback, stable offsets and bounded session work |
| 4. Enterprise sessions and catalog access | E2 → C3 → E3 | One session authority, retryable catalog access, awaited entry and scoped sign-out |
| 5. Owned download pipeline | D2 → D3 → D4 | Validated background transfer, controlled installer handoff and lifecycle recovery |
| 6. Presentation and support extraction | C4 → S1 → S2 | Library-owned UI, eligibility, recovery, diagnostics and resources |
| 7. Dependency and style checks | S3 | Narrow exports, explicit app dependencies and enforced architecture/style |
| 8. Integrated acceptance | S4 | Builds, runtime/upgrade evidence, independent review and upstream rehearsal |

Presentation cutover follows the new session/download implementations so moved screens need no temporary adapter around the unsafe old observer-driven installation. This refines the design's delivery sequence without changing its ownership or behavior.

- [Catalog and presentation plan](2026-10-16-sleke-module-refactor/01-catalog.md) — C1–C4.
- [Enterprise sessions plan](2026-10-16-sleke-module-refactor/02-sessions.md) — E1–E3.
- [Custom downloads plan](2026-10-16-sleke-module-refactor/03-downloads.md) — D1–D4.
- [Support and integration plan](2026-10-16-sleke-module-refactor/04-support-integration.md) — S1–S4.

## File ownership

| Root | Responsibility |
| --- | --- |
| `sleke/src/main/java/com/sleke/library/catalog/` | Source models/repository, mapping, metadata enrichment, paging, catalog presentation |
| `sleke/src/main/java/com/sleke/library/enterprise/` | Firebase identity/membership, session storage, email entry and enterprise presentation |
| `sleke/src/main/java/com/sleke/library/downloads/` | Request/operation state, durable operation journal, transfer/validation and coordinator |
| `sleke/src/main/java/com/sleke/library/eligibility/` | Pure consumer-specific catalog policy |
| `sleke/src/main/java/com/sleke/library/recovery/` | Pure recovery decision and storage contract |
| `sleke/src/main/java/com/sleke/library/runtime/` | Host-configured initialization and diagnostics |
| `app/src/main/java/com/sleke/integration/` | Play metadata/session, entry coordination, installer/artifact adapters and recovery storage |
| `sleke/src/main/res/` | Feature-owned strings, FirebaseUI theme/logo and presentation assets |
| `scripts/check_sleke_architecture.py` | Repository boundary checks, invoked by Gradle `check` |
| `docs/validation/sleke-module-refactor.md` | Commands, results, device/variant identity and unresolved acceptance evidence |

Create files named in the linked tasks as their task is implemented. Move source with its license header. Delete the old path only after callers have migrated; do not keep forwarding wrappers after the final cutover.

## Requirement coverage

Every normative requirement has an owner. The linked task's tests cover its scenarios; S4 supplies Android and whole-branch proof.

| Requirement | Task |
| --- | --- |
| Catalog source fields remain authoritative | C1, C3 |
| Enrichment failure retains the entry | C2 |
| Pagination has stable item boundaries | C2 |
| Feature presentation preserves navigation | C4 |
| Metadata work is bounded and session scoped | C2 |
| Empty catalog and failed catalog are distinct | C3 |
| Membership belongs to the current identity | E2 |
| Access outcomes remain distinct | E1, E2, E3 |
| Email entry awaits required session preparation | E3 |
| Play identity remains independent | E3 |
| Sign-out scopes are explicit | E3 |
| Cancelled entry preserves the previous session | E3 |
| Observation and diagnostics respect lifecycle and privacy | E2, S2 |
| Start returns the actual operation | D1, D4 |
| Observation is free of installation effects | D4, C4 |
| Only a validated artifact reaches installation | D2 |
| Cancellation remains cancellation | D2 |
| Staging has explicit artifact ownership | D3 |
| Preferred-installer fallback is controlled | D3 |
| Requested installation survives screen lifecycle | D4 |
| Installed versions and manual retries use one path | D3, D4 |
| Enterprise operations follow confirmed access loss | D4 |
| Temporary access outage blocks new private handoffs | D4 |
| Installed-app visibility preserves catalog scope | S1 |
| Update modes retain their existing meaning | S1 |
| Catalog failure cannot broaden eligibility | S1 |
| GMS exclusion is limited to the update pipeline | S1 |
| Catalog reuse is identity scoped | C3, S1 |
| First ordinary failure retries in place | S2 |
| Repeated failure uses the restart guard | S2 |
| Successful load clears the guard | S2 |
| Authentication failure follows upstream reauthentication | S2 |
| Product identity remains compatible | S3, S4 |
| Runtime initialization is idempotent | S2 |
| Diagnostics retain meaningful severity | S2 |
| SLEKE resources have stable ownership | C4, S2 |
| Presentation can be reused without host internals | C4 |
| Structural changes retain an auditable contract | S3 |

## Evidence and integration rules

- Run each focused JVM suite red for its named missing behavior, then green. A configuration/network failure is not a valid red test. Never commit a knowingly failing intermediate stage.
- After each production task run `./gradlew :sleke:testDebugUnitTest :app:testVanillaDebugUnitTest :app:assembleVanillaDebug`; the task lists additional instrumentation/build gates. Use Java 21 and the repository's existing SDK setup.
- Keep host tests under `app/src/test/java/com/sleke/integration/`; library tests mirror feature packages. Android-only WorkManager, PackageManager, AtomicFile and installer tests belong under `androidTest`, not JVM tests with stubbed Android calls.
- Record a fresh lint baseline in C1. Require no new findings while stages are in flight; S3 clears existing style debt and both module checks must pass without added suppressions. Keep mechanical upstream-only formatting in a separate commit.
- Mark an OpenSpec implementation item complete only when its mapped task's checks pass. Do not mark runtime, upgrade or privileged-installer paths complete from APK compilation or mocks.
- Use a branch created from this approved planning commit in an isolated checkout at execution time. Keep the approved spec/plan history. Do not reset unrelated work or merge into `develop` automatically.
- Roll back a failed stage by reverting its own commits; preserve user data, existing keys and schema. Reverse dependent commits first if a later stage has already landed.

## Planning handoff

The architecture and three product decisions are approved. This plan is ready for review; execution method has not been selected. Recommended: task-by-task implementation and review, because the session, durable-operation and installer boundaries share race-sensitive contracts. Native implementation is also supported with the same gates and a whole-branch review.

The self-review checks all 38 requirements, shared signatures, file ownership, five additional race/collision tests and staged rollback. No application behavior has been implemented by this planning change.
