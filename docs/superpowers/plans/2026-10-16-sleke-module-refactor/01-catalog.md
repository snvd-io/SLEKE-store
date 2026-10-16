# SLEKE Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve source catalog identity, make metadata optional and bounded, and move reusable catalog presentation into `:sleke`.

**Architecture:** Source DTOs map once into domain rows. Enrichment supplies display fields only. Paging and presentation consume these rows and host services through contracts.

**Tech Stack:** Kotlin, Flow, Paging 3, Compose, Hilt, Firebase, JUnit 4 and coroutines-test.

**Spec:** [Approved design](../../specs/2026-10-16-sleke-module-refactor-design.md), sections 3–5; OpenSpec `sleke-catalog` and `runtime-branding`.

## Global Constraints

Apply the [coordinator's global constraints and delivery order](../2026-10-16-sleke-module-refactor-plan.md). Preserve `:app → :sleke`, `com.sleke.store`, 76 / `4.8.4`, minimum SDK 26, database 13, source ordering, 300 ms debounce and at most four concurrent metadata calls. Domain contains no Android, Firebase, Paging, Compose or Aurora types. No persisted metadata cache.

## Review Focus

Context changes during a shared metadata call are pinned in C2. Source failure versus empty, source identities across collections, variable page sizes and enterprise back state are pinned in C1–C4.

---

## Task C1: Baseline, catalog models and test support

**Files:**
- Create: `docs/validation/sleke-module-refactor.md`.
- Create: `sleke/src/main/java/com/sleke/library/catalog/domain/CatalogApp.kt`, `CatalogResult.kt`, `CatalogMetadata.kt`, `CatalogRepository.kt` in that same directory.
- Create: `sleke/src/test/java/com/sleke/library/catalog/domain/CatalogAppTest.kt`.
- Modify: `sleke/build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.

**Interfaces:** Produces `CatalogScope` with `Regular`, `Sleke`, `Enterprise`; `CatalogIdentity(collection: String, documentId: String)`; `CatalogApp(identity, packageName, downloadUrl, type, country, title, description, publisher, versionLabel, iconUrl, rating, installs)`. Strings default to empty only for optional display fields; type is nullable and country is retained. Produces `CatalogResult.Success(apps: List<CatalogApp>)` and `Failure(cause: Throwable)`; successful empty is `Success(emptyList())`.

Produces `CatalogRepository.load(scope: CatalogScope, refresh: Boolean = false): CatalogResult` (suspending), `CatalogMetadataSource.context: StateFlow<MetadataContext>`, and suspending `load(packageName: String, context: MetadataContext): CatalogMetadata?`. `MetadataContext(key: String)` is an opaque host-generated account/device/locale generation, never a token. `CatalogMetadata` has nullable title/description/publisher/versionLabel/iconUrl/rating/installs. `CatalogApp.withMetadata(metadata: CatalogMetadata?): CatalogApp` changes only those display fields.

- [ ] **Step 1: Record the baseline.** Run existing recovery tests and vanilla app/test-APK builds; record commit, Java/SDK, exact commands, results and current lint findings. Use `./gradlew :app:testVanillaDebugUnitTest :app:assembleVanillaDebug :app:assembleVanillaDebugAndroidTest :app:ktlintCheck`; preserve a failing pre-existing lint result as baseline evidence. Do not mark future behavior passed.
- [ ] **Step 2: Write `CatalogAppTest.sourceFieldsSurviveMetadata`.** Construct a source row with identity `apks/doc-1`, package `a.b`, URL `https://example.test/a.apk`, type `enterprise` and country `AU`; enrich its title and icon. Assert `assertEquals(source.identity, enriched.identity)`, `assertEquals(source.downloadUrl, enriched.downloadUrl)`, `assertEquals(source.packageName, enriched.packageName)`, `assertEquals("enterprise", enriched.type)`, `assertEquals("AU", enriched.country)`. Add `nullMetadataKeepsSource` with `assertEquals(source, source.withMetadata(null))`.
- [ ] **Step 3: Add test dependencies and run red.** Add library `libs.junit`, `libs.google.truth` and `kotlinx-coroutines-test` aligned to `kotlinxCoroutinesCore` 1.10.2; add the same coroutines test dependency to app JVM and instrumentation tests. Run `./gradlew :sleke:testDebugUnitTest --tests '*CatalogAppTest'`; expect missing catalog symbols, not SDK/configuration errors.
- [ ] **Step 4: Implement the domain files and display-only merge.** Keep Firestore document identity separate from package identity; multiple source rows may name the same package. Implement the interfaces above without Firebase/Play imports or DI bindings yet.
- [ ] **Step 5: Run green and commit.** Repeat the focused command and common build gate. Commit `refactor: define catalog boundary and regression harness` with only the listed files.

## Task C2: Optional metadata and stable paging

**Files:**
- Create: `sleke/src/main/java/com/sleke/library/catalog/data/CatalogEnricher.kt`, `CatalogPagingSource.kt` in that directory.
- Create: `app/src/main/java/com/sleke/integration/catalog/PlayCatalogMetadataSource.kt`, `app/src/main/java/com/sleke/integration/di/SlekeIntegrationModule.kt`.
- Create: `sleke/src/test/java/com/sleke/library/catalog/data/CatalogEnricherTest.kt`, `CatalogPagingSourceTest.kt` in that test directory.
- Modify: `app/src/main/java/com/aurora/store/data/repository/GPlayRepository.kt` only if another verified caller still requires it after C4; otherwise remove it in C4.

**Interfaces:** Consumes C1. Produces `CatalogEnricher.enrich(apps: List<CatalogApp>, context: MetadataContext): List<CatalogApp>` (suspending), preserving order; and `CatalogPagingSource(apps: List<CatalogApp>, query: String, enricher: CatalogEnricher, context: MetadataContext): PagingSource<Int, CatalogApp>`. Keys are source offsets, not page numbers. The host integration module binds `CatalogMetadataSource` to the Play adapter in `SingletonComponent`. The Play adapter uses existing `AuthProvider` and `AppDetailsHelper` internally and emits a new context key whenever account, device-spoof or locale context changes.

- [ ] **Step 1: Write metadata tests.** Use a fake `CatalogMetadataSource` backed by controlled `CompletableDeferred` results. `missingAuthKeepsRows` and `ordinaryFailureKeepsUrl` assert unchanged row count and source URL; `cancelledRequestPropagates` asserts a `CancellationException`; `fourCallsMaximum` asserts `assertEquals(4, peakActive)` while eight distinct requests are blocked; `duplicateInFlightSharesCall` asserts `assertEquals(1, callsForPackage)`; `contextChangeDiscardsSharedCompletion` asserts that context A never emits into B and a B request executes independently. A cancelled caller must not cancel work still awaited by another caller in the same current context.
- [ ] **Step 2: Write paging tests.** Load a refresh of 15 rows followed by an append of 5 using the returned key. `differentLoadSizesKeepSourceOrder` asserts `assertEquals((0..19).map(Int::toString), rows.map { it.identity.documentId })`; add out-of-range, last-page and refresh-anchor cases. Search by case-insensitive title or package; blank query retains source order.
- [ ] **Step 3: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*CatalogEnricherTest' --tests '*CatalogPagingSourceTest'`; expect missing implementation or explicit assertions failing.
- [ ] **Step 4: Implement the data classes and host adapter.** Use a session-owned coroutine scope, a `Semaphore(4)` around actual calls and a context/package in-flight map. Remove completed entries, cancel the old context's owner, and rethrow cancellation before ordinary fallback. No completed-result disk cache. Paging calculates `end = minOf(offset + loadSize, filtered.size)` and uses `end` for append; refresh derives a valid offset from the anchor and existing page boundaries. Context invalidation replaces its pager.
- [ ] **Step 5: Run green and commit.** Repeat tests and common gate. Commit `fix: preserve catalog rows and stable paging`.

## Task C3: Retryable Firestore catalog and boundary mappings

**Files:**
- Create: `sleke/src/main/java/com/sleke/library/catalog/data/FirestoreCatalogRepository.kt`, `CatalogDtoMapper.kt`, `CatalogFetchStore.kt` in that directory.
- Create: `sleke/src/test/java/com/sleke/library/catalog/data/FirestoreCatalogRepositoryTest.kt`, `CatalogFetchStoreTest.kt`.
- Modify: `sleke/src/main/java/com/sleke/library/data/repository/FirestoreApkRepository.kt`, `ApkRepository.kt`, `sleke/src/main/java/com/sleke/library/di/RepositoryModule.kt`.

**Interfaces:** Consumes C1 and E1/E2's session state. Produces `CatalogFetchStore.load(key: String, refresh: Boolean, fetch: suspend () -> CatalogResult): CatalogResult`, `invalidate()`; the store is private to data and uses the current identity/generation as part of its key. `CatalogDtoMapper` has explicit mappings from each existing DTO plus its Firestore collection/document ID. `CatalogRepository.load(Enterprise)` resolves membership from the actual current identity; no arbitrary user ID parameter is public.

- [ ] **Step 1: Write mapping and fetch tests.** Map regular, SLEKE and enterprise DTOs without losing source fields. `samePackageDifferentCollectionsKeepsIdentity` asserts two distinct identities. `successfulEmptyIsNotFailure` asserts `Success(emptyList())`; `failedFetchCanRetry` scripts failure then success and asserts `assertEquals(2, fetchCalls)`; `concurrentConsumersShareFetch` asserts one fetch; `identityChangeInvalidatesPendingResult` asserts an old completion is rejected and not cached for the new identity. Enterprise failure must not become `Success(emptyList())`.
- [ ] **Step 2: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*FirestoreCatalogRepositoryTest' --tests '*CatalogFetchStoreTest'`.
- [ ] **Step 3: Implement the source boundary.** Keep `apks` ordered by `name` and existing SLEKE/enterprise collection paths. Read document IDs explicitly instead of losing them through `toObjects`. Put SDK access behind a data-only `CatalogDocumentsSource` interface in `FirestoreCatalogRepository.kt`, with its Firebase implementation there; fake this boundary in JVM tests. Rethrow cancellation; surface actual failures. Capture identity generation before IO and compare before publishing. Retain only successful session-scoped results and shared pending work; remove failed pending entries.
- [ ] **Step 4: Bind the new repository while legacy callers remain buildable.** Existing `ApkRepository` can delegate catalog methods to the new data implementation until C4/S1 migrate their callers. Preserve legacy signatures only for that transition; membership is migrated in E2. Do not add a second Firestore cache.
- [ ] **Step 5: Run green and commit.** Repeat focused tests and common gate. Commit `refactor: isolate retryable catalog data access`.

## Task C4: Move routes, state, resources and download consumers

**Files:**
- Move: `app/src/main/java/com/sleke/presentation/HomeViewModel.kt` → `sleke/src/main/java/com/sleke/library/catalog/presentation/HomeViewModel.kt`.
- Move/refactor the current `screens/SlekeHomeScreen.kt`, `SlekeAppsScreen.kt`, `SlekeAppsViewModel.kt`, `SlekeAppsUiState.kt`, `PagedAppsScreen.kt` into that library presentation directory as `HomeRoute.kt`, `SlekeAppsContent.kt`, `SlekeAppsViewModel.kt`, `CatalogUiState.kt`, `PagedCatalogContent.kt`.
- Create: `sleke/src/main/java/com/sleke/library/catalog/presentation/SlekeAppsRoute.kt`.
- Move/refactor: `app/src/main/java/com/sleke/presentation/screens/EnterpriseAppsPane.kt` and `enterprise/EnterpriseAppsViewModel.kt` → `sleke/src/main/java/com/sleke/library/enterprise/presentation/EnterpriseAppsContent.kt`, `EnterpriseAppsViewModel.kt`.
- Replace/delete: `app/src/main/java/com/sleke/presentation/AppDetailsPagingSource.kt`, `download/DownloadableApp.kt`; inspect and remove verified-unused `screens/SlekeNavigation.kt`, `screens/SlekeAppsFragment.kt`.
- Modify: `app/src/main/java/com/aurora/store/compose/ui/main/MainScreen.kt`, `sleke/build.gradle.kts`, `sleke/src/main/res/values/strings.xml`; move other directly referenced SLEKE assets into library resources.
- Create: `sleke/src/test/java/com/sleke/library/catalog/presentation/CatalogViewModelTest.kt`, `SlekeNavigationStateTest.kt`; `app/src/androidTest/java/com/sleke/integration/catalog/SlekeCatalogNavigationTest.kt`.

**Interfaces:** Consumes C1–C3, E2/E3 and D4. Produces `SlekeAppsRoute(onOpenPackage: (String) -> Unit, onEnterpriseSignOut: () -> Unit)` and `HomeRoute(onOpenPackage: (String) -> Unit)`; stateless `SlekeAppsContent(state: CatalogUiState, onSearch: (String) -> Unit, onOpenPackage: (String) -> Unit, onDownload: (CatalogApp) -> Unit, onRetry: () -> Unit)`. `CatalogUiState` explicitly represents loading, populated, empty and error states plus immutable row/download state. The host applies its outer theme; public callbacks carry no Aurora types.

- [ ] **Step 1: Write state and navigation tests.** Virtual-time search asserts zero fetches at 299 ms and one at 300 ms; stale searches cannot overwrite new results. Retry replaces an error with populated data. Download invokes D4 with `packageName`, never display name; terminal observation releases its collector. Route drill-down/back survives saved-state recreation and opening details passes the original package. Add Android UI cases for loading, empty, error/retry, regular entry, enterprise entry/back and session access loss while enterprise is visible.
- [ ] **Step 2: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*CatalogViewModelTest' --tests '*SlekeNavigationStateTest'`; the new routes/state must not exist yet.
- [ ] **Step 3: Move and bind feature presentation.** Enable library Compose build features and declare the existing Paging Compose, lifecycle/Hilt Compose and preview dependencies directly. Own drill-down in `SlekeAppsRoute` with saved state; use lifecycle-aware collection. ViewModels consume domain repositories/coordinator, with Android package facts behind D1's `InstalledPackageSource`. Add loading/populated/empty/error previews with inert callbacks. Manual Install invokes `CustomDownloadCoordinator.retryInstall`; observers only render. Remove duplicated Home transfer methods after caller search.
- [ ] **Step 4: Reduce MainScreen to feature hooks and complete cleanup.** Keep its upstream tabs, storage/notification and back algorithms; replace only custom tab content/state with route callbacks. Search all source/manifests before deleting unused fragment/navigation/GPlay/DTO compatibility paths. Move FirebaseUI theme in E3 and retain the already owned `sleke_login_logo`. Preserve string translations by moving their existing localized values together.
- [ ] **Step 5: Run green and commit.** Run focused tests, common gate and `./gradlew :app:assembleVanillaDebugAndroidTest`. On Android run `./gradlew :app:connectedVanillaDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sleke.integration.catalog.SlekeCatalogNavigationTest`; record unavailable runtime separately. Commit `refactor: move SLEKE feature presentation into library` after build/JVM proof, leaving Android acceptance unchecked until exercised.
