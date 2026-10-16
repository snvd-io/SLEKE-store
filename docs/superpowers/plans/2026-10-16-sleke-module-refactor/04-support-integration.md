# SLEKE Support and Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract product policies/runtime support, enforce dependency boundaries and prove the complete refactor preserves compatibility.

**Architecture:** Pure policy decisions live in library feature packages. Host adapters translate package facts, restart storage and application configuration. Gradle checks enforce the final boundary; Android and upgrade tests establish runtime behavior.

**Tech Stack:** Kotlin/JUnit, existing Aurora workers/Room/Preferences, Timber/Crashlytics, Gradle/ktlint, Python stdlib architecture check and Android instrumentation.

**Spec:** [Approved design](../../specs/2026-10-16-sleke-module-refactor-design.md), sections 8–10; OpenSpec `catalog-update-eligibility`, `app-details-recovery`, `runtime-branding` and integrated scenarios.

## Global Constraints

Apply the [coordinator](../2026-10-16-sleke-module-refactor-plan.md). Preserve `com.sleke.store`, 76 / `4.8.4`, minimum SDK 26, database 13, existing preference keys/signing and both module boundaries. Keep one-second retry, 60-second guard and update-only GMS exclusion. No new style suppressions, framework, module, release publication or backend deployment.

## Review Focus

S1 pins the full eligibility truth table and failure behavior. S2 pins clock/restart boundaries and duplicate runtime initialization. S3/S4 check flavor compilation, imported host types, existing-data upgrade, real installer outcomes and the remaining upstream merge surface.

---

## Task S1: Consumer-specific catalog eligibility and retry

**Files:**
- Create: `sleke/src/main/java/com/sleke/library/eligibility/domain/CatalogEligibilityPolicy.kt`.
- Create: `sleke/src/test/java/com/sleke/library/eligibility/domain/CatalogEligibilityPolicyTest.kt`.
- Modify: `app/src/main/java/com/aurora/store/viewmodel/all/InstalledViewModel.kt`, `app/src/main/java/com/aurora/store/data/work/UpdateWorker.kt`.
- Create: `app/src/androidTest/java/com/sleke/integration/eligibility/CatalogEligibilityIntegrationTest.kt`.
- Modify, only where GMS can leak through previously stored rows: `app/src/main/java/com/aurora/store/viewmodel/all/UpdatesViewModel.kt` and `app/src/main/java/com/aurora/store/compose/ui/updates/UpdatesScreen.kt`.
- Modify retry/error integration: `app/src/main/java/com/aurora/store/compose/ui/installed/InstalledScreen.kt`.

**Interfaces:** Consumes C1/C3 `CatalogResult`/repository. Produces `PackageFacts(packageName: String, blacklisted: Boolean, enabled: Boolean, fdroid: Boolean, installerPackage: String?)`, `UpdateFilterOptions(catalogOnly: Boolean, extended: Boolean, excludeFdroid: Boolean, excludedInstallers: Set<String>)`, and `EligibilityResult.Allowed(packages: Set<String>)` or `Unavailable`.

`CatalogEligibilityPolicy.installed(facts: List<PackageFacts>, catalog: CatalogResult): EligibilityResult` always intersects successful catalog and blacklist rules. `updates(facts, options, catalog: CatalogResult?): EligibilityResult` excludes GMS first in every mode, then existing blacklist/enabled rules. Catalog-only true intersects successful catalog and bypasses the current F-Droid/installer-source exclusions; false needs no catalog fetch and applies those existing exclusions. A null installer source retains current inclusion behavior. A failed required catalog is `Unavailable`, never unrestricted or a successful empty set.

- [ ] **Step 1: Write a parameterized truth table.** Cover catalog membership, preference true/false, blacklist, enabled/extended, F-Droid, matching/nonmatching/null installer and GMS. Assert true-catalog branch includes an otherwise F-Droid/installer-excluded member; false branch excludes it. `installedIgnoresUpdatePreference` keeps catalog intersection in both modes. `gmsNeverReachesUpdateOutput` asserts GMS absent from candidates, persisted update UI output, notifications and download enqueue; manual microG discovery remains callable. Successful empty asserts `Allowed(emptySet())`; failed required catalog asserts `Unavailable`.
- [ ] **Step 2: Write host retry tests.** Fake C3 repository failure then success: Installed emits an explicit error and refresh obtains fresh rows; catalog-filtered UpdateWorker enqueues zero packages on failure and performs a fresh fetch on the next run. Concurrent Installed/Updates for one identity share source work; changing identity invalidates it. False update mode asserts zero catalog fetches.
- [ ] **Step 3: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*CatalogEligibilityPolicyTest'`.
- [ ] **Step 4: Implement policy and small host hooks.** Replace the permanently failed lazy deferred in Installed with a refreshable job/state; retain its existing sorting/metadata logic and expose retry to its existing screen. Map Android package/source facts in the host. UpdateWorker keeps upstream auth, self-update, certificate, ignored-update, compatibility and notification orchestration. Gate GMS before lookup and at stale stored-row presentation/enqueue boundaries; preserve manual microG routes and stored `PREFERENCE_FILTER_AURORA_ONLY` default `false`.
- [ ] **Step 5: Run green and commit.** Repeat the pure policy suite/common gate, compile the host Android tests and run `CatalogEligibilityIntegrationTest` on Android before marking its retry/worker acceptance passed. Commit `refactor: extract catalog eligibility without changing filter modes`.

## Task S2: Recovery, runtime diagnostics and owned resources

**Files:**
- Move: `app/src/main/java/com/aurora/store/data/model/RecoveryDecision.kt` → `sleke/src/main/java/com/sleke/library/recovery/domain/AppDetailsRecoveryPolicy.kt` with original notices.
- Move: `app/src/test/java/com/aurora/store/data/model/RecoveryDecisionTest.kt` → `sleke/src/test/java/com/sleke/library/recovery/domain/AppDetailsRecoveryPolicyTest.kt`.
- Create: `sleke/src/main/java/com/sleke/library/recovery/domain/RecoveryGuardStore.kt`; `app/src/main/java/com/sleke/integration/recovery/PreferenceRecoveryGuardStore.kt`.
- Create: `sleke/src/main/java/com/sleke/library/runtime/SlekeRuntime.kt`, `RuntimeConfig.kt`, `RuntimeInitializer.kt`; `sleke/src/main/java/com/sleke/library/runtime/data/CrashlyticsTimberTree.kt`, `DiagnosticsSink.kt`.
- Create: `sleke/src/test/java/com/sleke/library/runtime/SlekeRuntimeTest.kt`, `DiagnosticPolicyTest.kt`; `app/src/androidTest/java/com/sleke/integration/recovery/AppDetailsRecoveryIntegrationTest.kt`.
- Modify: `app/src/main/java/com/sleke/integration/di/SlekeIntegrationModule.kt` to bind `RecoveryGuardStore` to the host preference adapter.
- Modify: `app/src/main/java/com/aurora/store/viewmodel/details/AppDetailsViewModel.kt`, `app/src/main/java/com/aurora/store/AuroraApp.kt`, `app/src/main/java/com/aurora/store/util/CrashlyticsTree.kt`.
- Inspect/remove verified-unused: `sleke/src/main/java/com/sleke/library/ui/GoogleLogo.kt` and other legacy support only if all callers are migrated. Keep active assets and notices.

**Interfaces:** `AppDetailsRecoveryPolicy.decide(retryAttempted: Boolean, lastRestartMs: Long, nowMs: Long): Recovery` returns existing `RETRY`, `RESTART`, `SHOW_MESSAGE`. `RecoveryGuardStore.read(): Long`, `record(timestampMs: Long)`, `clear()` preserve `PREFERENCE_ERROR_RESTART_TS`; host adapter performs actual preference access. Keep actual delay/restart/navigation in AppDetailsViewModel. Use the existing `RESTART_GUARD_WINDOW_MS = 60_000L`, `RETRY_DELAY_MS = 1_000L`.

`RuntimeConfig(debug: Boolean)` is supplied by app BuildConfig. `SlekeRuntime.initialize(context: Context, config: RuntimeConfig)` owns Firebase/product diagnostics initialization once per process. `DiagnosticsSink.breadcrumb(priority: DiagnosticPriority, message: String)` and `record(throwable: Throwable)` are a data-only test seam; `DiagnosticPriority` is plain `WARNING`/`ERROR`. `RuntimeInitializer.initialize(config: RuntimeConfig)` is the plain Kotlin orchestration seam, consuming a `LoggingRegistry` interface (`registerOwned(debug: Boolean)`, `isOwnedRegistered(): Boolean`) declared in that file. JVM tests exercise it with a fake registry; only the Android entry point creates Firebase/platform adapters. The Timber tree maps actual Android log integers, never Cedar enum ordinals. Keep domain decisions separate from the Android runtime entry point.

- [ ] **Step 1: Preserve and extend recovery tests.** Port all six existing recovery cases unchanged. Add elapsed 59,999 ms → `SHOW_MESSAGE`, exactly 60,000 ms → `RESTART`, no prior timestamp → `RESTART` after retry, and first failure → `RETRY` even with a recent timestamp. Virtual time asserts retry begins at 1,000 ms, successful load clears the original key and auth failure invokes upstream reauthentication without restart. Preserve current future-timestamp behavior as characterized; do not change the guard's clock semantics silently.
- [ ] **Step 2: Write diagnostics/init tests.** `initializationTwicePlantsOneOwnedTree` asserts one owned registration and retains a pre-existing unrelated Timber tree. Debug config plants no release-reporting tree. Warning records one breadcrumb and zero exceptions; error with throwable asserts `assertSame(original, recorded.single())`; integer WARN/ERROR maps to W/E correctly. Use a sanitized fixed context message with no identity interpolation. Assert none of the scripted credential/email/account document sentinel values appear in captured diagnostics.
- [ ] **Step 3: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*AppDetailsRecoveryPolicyTest' --tests '*SlekeRuntimeTest' --tests '*DiagnosticPolicyTest'`.
- [ ] **Step 4: Extract policies/adapters and runtime.** Keep the pure recovery decision equivalent and its host hook small; preserve guard storage and clear-on-success behavior. Move active Timber reporting into the library, preserve original throwables and implement warnings as breadcrumbs/errors once. Remove the unregistered Cedar adapter only after registration/caller search; never call `uprootAll`. Runtime uses host debug config, not the library build type. Product assets/strings use owned resource names; preserve active Google logo/branding and all licensing.
- [ ] **Step 5: Run green and commit.** Repeat focused JVM tests/common gate, compile and run host recovery Android tests, build release compilation in S3/S4 and inspect the remaining upstream hook diff. Commit `refactor: isolate SLEKE recovery and runtime support`.

## Task S3: Explicit dependencies, style and architecture enforcement

**Files:**
- Modify: `app/build.gradle.kts`, `sleke/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`.
- Create: `scripts/check_sleke_architecture.py`; `scripts/tests/test_sleke_architecture.py`.
- Modify: remaining SLEKE source formatting and only the upstream files carrying the recorded style debt; use a separate mechanical commit for upstream-only formatting.
- Remove verified-unused legacy types/repositories/UI/worker enqueue paths after C4/D4/S1 migration; retain the old worker class needed for upgrade compatibility.

**Interfaces:** `python3 scripts/check_sleke_architecture.py --root <repository>` exits 0 only when module/source boundaries pass, with file/line diagnostics on violation. `:sleke:checkArchitecture` invokes it and is wired into library `check`; root verification includes both modules' checks. Architecture rules cover all library Kotlin source sets, fully-qualified references as well as imports, forbidden dependency coordinates and domain package references. No broad allowlist for implementation packages.

- [ ] **Step 1: Write and run red architecture fixtures.** In temporary source trees, a library import or fully-qualified reference to `com.aurora` fails; a domain import/reference to Android, Compose, Firebase, WorkManager, Paging, Ktor or other platform/network models fails; library Gradle dependency on `:app` fails. A plain domain model and app-owned adapter pass. Comment/license text must not create a false failure. Nested feature directories must be scanned. Run `python3 -m unittest discover -s scripts/tests -p 'test_sleke_architecture.py'` red before adding the checker.
- [ ] **Step 2: Implement and wire the checker.** Use stdlib and explicit source/dependency scans with comment/string handling appropriate to Kotlin/Gradle. Report structural violations, not a claim to prove arbitrary runtime behavior. Pair enforcement with review of public method types. Fix actual boundary violations instead of adding suppressions or an app dependency to the library.
- [ ] **Step 3: Narrow exports and remove dead paths.** Change Firebase, Timber/Cedar, DataStore and other implementation-only `api` dependencies to `implementation`; declare dependencies the app still directly imports in its own module. Retain `api` only for types intentionally present in public signatures, such as a public Paging/Flow contract where needed. Remove duplicate hard-coded Paging/Room declarations when the version-catalog alias supplies the same resolved dependency; do not upgrade unrelated libraries. Verify release consumers do not rely on library BuildConfig for app behavior. Keep schema 13 and signing untouched.
- [ ] **Step 4: Clear style debt in bounded commits.** Apply ktlint to both modules using the existing plugin/version. Run `./gradlew :app:ktlintCheck :sleke:ktlintCheck`; resolve findings without rule disablement or new baselines/suppressions. Preserve copyright/license content. Separate purely mechanical upstream changes from feature moves for review and future merge diagnosis.
- [ ] **Step 5: Run green static/build gate and commit.** `./gradlew :sleke:checkArchitecture :sleke:testDebugUnitTest :app:testVanillaDebugUnitTest :app:ktlintCheck :sleke:ktlintCheck :app:assembleVanillaDebug :app:assembleVanillaDebugAndroidTest :app:compileHuaweiDebugKotlin :app:compilePreloadDebugKotlin :app:compileVanillaReleaseKotlin`. All must succeed; confirm task names with `./gradlew :app:tasks --all` if the build plugins expose a different compilation task. Commit `build: enforce SLEKE boundaries and explicit dependencies` after the separate style commit.

## Task S4: Integrated runtime, upgrade and upstream maintenance proof

**Files:**
- Update: `docs/validation/sleke-module-refactor.md`, `openspec/changes/isolate-sleke-features/tasks.md`.
- Create: `docs/architecture/sleke-integration-points.md`.
- Create: `app/src/androidTest/java/com/sleke/integration/SlekeUpgradeCompatibilityTest.kt`.
- Modify production code only for concrete failures found here, with a regression in its owning suite and a separate fix commit.

**Interfaces:** Consumes all preceding tasks and the 38-requirement coverage table. Produces an evidence record with host/JVM, Android UI/worker, real installer, upgrade, flavor/release compilation and merge-rehearsal results kept distinct. No testing or deployment claim is inferred from an artifact existing.

- [ ] **Step 1: Run the complete host/static suite.** Run S3's gate, architecture fixtures and `openspec validate isolate-sleke-features --strict --no-interactive`. Inspect XML manifests/resources and compile variant receivers. Record exact commit and command outputs; avoid repeatedly rerunning unaffected suites after they pass.
- [ ] **Step 2: Run Android behavior tests.** Use an available API-26 device/emulator and a current supported API device/emulator where behavior differs. Run the `com.sleke.integration` instrumentation package using `./gradlew :app:connectedVanillaDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.sleke.integration`. Cover email success/cancel/outage, deep links, enterprise drill-down/back, preferred installer selection/fallback, user rejection, pending confirmation, notification cancellation, navigation/recreation and process death. Simulate process death without treating force-stop suppression of background work as a product bug. Test available privileged installers separately; explicitly list unavailable capabilities. Use a designated enterprise test account for actual Firebase email/membership acceptance; injected provider results prove UI handling only. If real test-account/device access is unavailable, retain those acceptance rows as pending rather than contacting production users or changing server rules.
- [ ] **Step 3: Prove existing-data compatibility.** Install a signed baseline artifact from merged PR 5 on a disposable profile, seed nonsecret settings, accounts/bindings and pending/validated custom download state, then install the refactored APK using the same application ID and signing key without clearing data. Because version remains 76, use a permitted same-version replacement rather than inventing a release bump. The upgrade test asserts DB user_version 13, preserved existing keys/data and safe legacy WorkManager-row handling. Do not run the upgrade test over the user's live app data.
- [ ] **Step 4: Review final integration hooks.** Independently review changed behavior against the approved spec and every requirement owner. Record package/file/API hooks in MainScreen, SplashScreen/AuthViewModel, UpdateWorker, InstalledViewModel, AppDetailsViewModel, AuroraApp and installer file/receipt hooks. Verify no full upstream shell/algorithm was copied and no library API exposes host implementation types.
- [ ] **Step 5: Rehearse upstream synchronization reversibly.** Fetch the configured upstream, record exact refs, then use a disposable checkout/branch for a merge rehearsal. Record textual conflicts and required semantic hook review separately. If upstream has not advanced beyond `3a15ff676af9badb3cdf0bbb54b451383918cd6b`, report that limitation; a no-op rehearsal does not prove reduced future conflicts. Abort/remove only the owned rehearsal checkout. Do not alter `develop` or rewrite branch history.
- [ ] **Step 6: Update evidence/checklists and prepare the implementation PR.** Mark only executed passing acceptance items complete; leave device/installer/upgrade gaps explicit. Link the approved spec, full plan, integration hook inventory and evidence record. PR title/body describe concrete behavior and validation, preserve the user's attribution preference, and contain no unrelated changes. Do not merge automatically. Commit `docs: record SLEKE refactor validation and integration hooks` after actual evidence exists.
