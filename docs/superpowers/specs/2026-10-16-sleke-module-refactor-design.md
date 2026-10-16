# SLEKE module architecture

Refactoring specification · Draft for review · 16 October 2026

Move SLEKE product behavior into `:sleke`, expose small contracts for host services, and keep Aurora changes easy to review. Preserve the current catalog, enterprise entry, branding and recovery behavior while correcting the concrete reliability defects identified below.

The repository snapshot is `c58f58eb0`, the merge of [PR 5](https://github.com/snvd-io/SLEKE-store/pull/5). This phase produces the specification and web reader. Application implementation begins after review.

## 1. Outcome and scope

The finished refactor has two existing Gradle modules with the dependency `:app → :sleke`. SLEKE owns its feature UI, state, domain rules, Firebase data access and download workflow. The application owns navigation, Play integration, Aurora installers and Android packaging.

The scope includes the five follow-ups from PR 5, the app-details recovery extraction and the reliability corrections necessary to make those boundaries dependable. It includes focused regression tests, architecture checks, dependency cleanup and the existing SLEKE style debt. The three installation/access decisions are confirmed: continue requested installs, use the preferred installer consistently for updates/manual retries, and cancel unsubmitted enterprise work when access ends. The complete architecture remains a draft for review.

Application ID `com.sleke.store`, versionCode 76, versionName `4.8.4`, minimum SDK 26, database version 13, preference keys, signing configuration and supported product flavors remain compatible. Keep the current ViewModel, Flow, Compose, Hilt and WorkManager approach. Keep backend deployments, release publication and unrelated upstream feature redesign outside this change.

## 2. Current coupling and defects

There are 13 SLEKE presentation files and 1,433 lines still in the application module. Six files directly import Aurora implementation classes or app resources. The last upstream integration needed ten conflict resolutions; two were launcher-header conflicts, and the remaining files crossed product and upstream ownership.

| Area | Current evidence | Required outcome |
| --- | --- | --- |
| Feature ownership | `com.sleke.presentation` lives in `:app`; screens refer to application resources and theme | Feature presentation lives in `:sleke`; the host supplies navigation and an outer theme |
| Catalog metadata | Enrichment replaces source fields and can empty a download URL; normal failure drops the entry | Source identity and download fields survive enrichment; unavailable metadata keeps the entry |
| Paging | Page position is calculated from the current request size | Initial and append sizes cannot repeat or skip entries |
| Enterprise session | Firebase identity, a stored Boolean and per-screen checks act as different authorities | One current-identity session with explicit membership outcomes |
| Download identity | Unique work can retain an existing job while returning the unused new UUID | Every caller receives the actual retained operation handle |
| Download ownership | Installation runs inside UI observation; collectors do not finish at terminal states | Observation is side-effect-free; one operation owner controls transfer and installation |
| Artifact validation | The worker does not compare the requested package with the downloaded APK | Invalid responses and mismatched packages fail before installation |
| Catalog policy | Installed and Updates use different catalog scopes; a failed lazy fetch stays failed | Preserve the scopes, share successful work and allow explicit retry without broadening eligibility |

The unused Home download method also supplies the app display name instead of its package name. This is a latent defect, not a proven live UI failure. The legacy DownloadManager receiver has no discovered registration or matching enqueue path; remove it only after reference and manifest verification.

## 3. Ownership and dependency direction

### Feature organization

Use feature-first packages under `com.sleke.library`: `catalog`, `enterprise`, `downloads`, `eligibility`, `recovery` and `runtime`. Within a feature, use `domain`, `data` and `presentation` where those responsibilities exist. Do not manufacture empty layers or create more Gradle modules for this change.

- **Domain** owns plain Kotlin models, repository contracts and decisions. It imports no Android, Compose, Firebase, WorkManager or Aurora types.
- **Data** implements domain contracts, maps DTOs, observes platform services and owns IO/cancellation.
- **Presentation** exposes immutable state and actions. Stateless content composables accept that state and callbacks; route composables bind lifecycle-aware view models.
- **Host adapters** live in `:app` outside upstream implementation packages where possible. They translate Play models, installer artifacts and navigation requests at the boundary.

### Keep the upstream integrations small

Keep `MainScreen`, `SplashScreen`, `UpdateWorker`, `InstalledViewModel` and `AppDetailsViewModel` as upstream-owned orchestration surfaces. Call feature routes, policies or recovery contracts from bounded integration points. Do not duplicate upstream notification, tracker, storage or installer algorithms in a new SLEKE shell.

`SlekeAppsRoute` owns its enterprise drill-down state, reducing custom navigation state inside `MainScreen`. The host retains the SLEKE tab definition and package-details callback. A library-owned email-entry component owns FirebaseUI resources and result handling; the host combines its outcome with Play preparation and deep-link navigation.

## 4. Contracts and model mapping

The following names describe the proposed public boundary. Exact constructor details can be settled during the implementation plan without changing these responsibilities.

| Contract | Owned by | Responsibility |
| --- | --- | --- |
| `CatalogRepository` | `:sleke` domain | Fetch source catalog entries and return explicit success, empty or failure results |
| `CatalogMetadataSource` | Contract in `:sleke`; adapter in `:app` | Supply optional Play display metadata without exposing Play authentication or models |
| `EnterpriseSessionRepository` | `:sleke` | Observe current enterprise identity, reconcile storage, refresh membership and sign out |
| `PlaySessionGateway` | Contract in `:sleke`; adapter in `:app` | Prepare or reset the required Play session through existing upstream behavior |
| `CustomDownloadCoordinator` | `:sleke` | Start, observe and cancel a custom-download operation by its real handle |
| `ApkInstallGateway` | Contract in `:sleke`; adapter in `:app` | Stage a validated artifact and submit it to the existing preferred installer |
| `CatalogEligibilityPolicy` | `:sleke` | Apply explicit installed-list and update-mode rules to package facts |
| `AppDetailsRecoveryPolicy` | `:sleke` | Return retry, restart or message decisions using the existing guard values |
| `SlekeRuntime` | `:sleke` | Initialize product diagnostics and support code from host-provided configuration |

Expose immutable domain models such as `CatalogApp`, `CatalogMetadata`, `EnterpriseSessionState`, `CustomDownloadRequest`, `DownloadHandle`, `DownloadState` and `ValidatedApk`. Keep Firestore DTOs, Firebase users, `AuthData`, raw WorkManager keys and Aurora `Download`/`ExternalApk` models out of feature APIs. Keep Android file paths/URIs in data and adapter implementations; represent artifact ownership by an opaque reference at the domain boundary.

Map Firestore DTOs into domain models at the repository boundary and map domain models into presentation state at the view-model boundary. Successful enrichment fills optional display fields; it never overwrites the authoritative source package, download URL, type, country or catalog identity.

## 5. Catalog and presentation

Move all currently hosted SLEKE presentation into the library after its metadata and installation contracts are in place. Replace app-resource imports with library-owned resources. Keep the application theme around library content so the visual result remains consistent.

Use source offsets or stable item keys for paging; do not multiply page numbers by a changing `loadSize`. Keep the existing 300 ms search debounce and source ordering. Preserve source entries when enrichment is unavailable. Bound metadata concurrency to four requests, deduplicate in-flight requests within the same session and cancel obsolete searches or session work. Do not introduce a persisted metadata cache or share metadata between account/device/locale contexts.

Loading, populated, empty and error states remain distinct. A failed source fetch provides a fresh retry rather than an immortal failed deferred. Preview stateless content with representative states. Remove view-model methods that are verified unused instead of moving duplicate implementations into the library.

## 6. Enterprise sessions and sign-out

Enterprise identity and Play identity are separate. Firebase email entry currently resolves enterprise membership and then builds an anonymous Play session; Google Play entry does not establish a Firebase identity. Preserve that distinction and retain the upstream Google, microG and account-add paths.

The enterprise session has explicit restoring, signed-out, checking-membership, allowed, no-membership and temporarily-unavailable states. Membership is resolved from the actual current Firebase user inside the data implementation. A caller cannot grant access by supplying an arbitrary UID. Stored `isEnterprise` is a presentation hint that must be reconciled with the current user before enterprise routing.

| Operation | Enterprise identity and state | Play session and account records |
| --- | --- | --- |
| Enterprise-only sign-out | Clear Firebase session, membership and enterprise presentation/cache | Preserve the existing Play identity |
| Play token/device-spoof reset | Preserve enterprise identity | Rebuild the affected Play session |
| Switch/remove one Play account | Preserve enterprise identity | Apply the upstream account operation and default-account behavior |
| Full application sign-out | Attempt complete enterprise cleanup | Attempt cached-auth and account-record cleanup even if another cleanup fails |

Full sign-out invalidates in-flight sign-in, membership and bootstrap results before cleanup, so delayed completions cannot restore a signed-out user. Membership failure remains retryable rather than being persisted as successful denial. An authenticated nonmember receives the regular catalog. A temporarily unavailable membership service also permits regular-catalog entry once Play is ready, with enterprise content blocked and an explicit membership retry. This preserves public functionality without granting unverified enterprise access.

Own one Firebase listener with deterministic cleanup. Keep provider cancellation distinct from failure, and reset busy state without clearing an existing session. Coordinate email outcome and anonymous Play readiness before one navigation effect; preserve the original deep-link package/developer intent across preparation and lifecycle recreation.

Backend authorization remains the server's responsibility. This refactor must not claim that client membership checks establish cross-enterprise isolation; verify the existing backend rules separately when that evidence is available, without changing production rules as part of this work.

## 7. Custom downloads and installation

### One operation owner

All SLEKE download entry points use one coordinator. `start` returns the actual active operation when unique work retains an earlier request. Use a namespaced custom-download key and prevent an old operation from overwriting the state of a replacement. Screen collection renders state and performs no transfer, staging or installation side effects.

The transfer owns HTTP success checking, temporary-file cleanup, cancellation propagation and archive validation. Validate the requested package against the parsed package before publishing a usable artifact. A cancelled or failed transfer cannot publish success or reach an installer. Do not add a new certificate trust policy under the label of refactoring; retain the existing source trust model while enforcing package identity.

### Host installation adapter

The host adapter converts only validated artifacts into Aurora's existing installation model. Stage files on an IO dispatcher and isolate ownership of the exact artifact set so a stale split APK cannot be included accidentally. Do not clear a shared download directory without proving that no other operation owns its files. Retain an artifact while the platform or user still needs it, including pending-confirmation sessions.

Use the current preferred-installer selection and its existing Session fallback when the selected installer is unavailable. Preserve one declared system-prompt fallback for synchronous staging/submission failure. Asynchronous failure, user rejection and permission denial remain terminal/retryable states; they never silently start a second installer.

### Installation decisions

All three installation/access decisions below were confirmed on 16 October 2026. Changing a decision changes its acceptance scenarios and the later execution plan.

| Decision | Selected behavior | Status | Alternative considered |
| --- | --- | --- | --- |
| Requested install after navigation/restart | Continue the original user-requested operation through the preferred installer; retain Android confirmation where required | Confirmed | Stop at a downloaded state and wait for manual installation |
| Older installed versions and manual retries | Offer the requested newer APK and use the preferred installer consistently; do not reinstall an equal/newer installed version automatically | Confirmed | Preserve the current skip-installed behavior and direct manual system prompt |
| Enterprise access ends | Cancel unfinished enterprise work and block unsubmitted installation after sign-out, identity change or confirmed revocation | Confirmed | Allow a previously started enterprise request to finish |

Each enterprise operation carries its owning identity/enterprise scope. Confirmed access loss invalidates unsubmitted work under the selected policy. If the installer already accepted a request, attempt cancellation only for an owned pending session when the platform supports it; report the outcome and do not claim that an installed application was undone. Do not uninstall an existing application as a side effect of signing out.

A temporary membership-service outage is not confirmed revocation. It blocks new enterprise requests and new install handoffs while keeping a validated private artifact pending an explicit access refresh. A confirmed identity change/sign-out purges unsubmitted artifacts belonging to the old identity. Already accepted installer requests follow the platform's lifecycle and are reconciled rather than replayed.

Continuing the original user request must not mean initiating unrelated installs. Worker retries and process recovery must reconcile an already submitted installer request before resubmission; an exactly-once guarantee across a process crash and an external installer is not assumed.

## 8. Eligibility, recovery and runtime

### Preserve eligibility modes

| Consumer or setting | Catalog membership | Other existing exclusions |
| --- | --- | --- |
| SLEKE installed-app list | Required | Preserve blacklist behavior |
| Updates with catalog filter enabled | Required | Preserve the current branch's bypass of F-Droid/installer-source exclusions |
| Updates with catalog filter disabled, the current default | Not required | Preserve the existing non-catalog source filters |
| GMS automated updates | Always excluded | Apply before all update-mode branches |
| Manual microG discovery/onboarding | No new global exclusion | Preserve its existing entry points |

Preserve the stored preference key and default. Catalog fetch failure does not become a successful empty result or an unrestricted package set. Share successful in-flight work within one identity, invalidate it when identity changes and allow fresh retries after failure. Do not add last-known catalog caching until its freshness and authorization semantics have been specified.

### Preserve recovery behavior

Move the pure recovery decision and tests into `:sleke`, with a small storage contract that preserves the existing timestamp key. Keep the first retry after one second, the 60-second restart guard, the exact boundary behavior and successful-load guard reset. Authentication failures retain the upstream reauthentication route. Keep process restart and package navigation in the host adapter.

### Isolate runtime behavior

Move product diagnostics and owned assets into `:sleke`. Pass application debug/configuration explicitly; do not derive application behavior from the library's build type. Initialization is idempotent and does not remove other logging sinks. Retain original throwables, map severity from the actual logging API, and do not log tokens, email or full account documents. Remove an unregistered duplicate logging adapter only after reference verification.

## 9. Delivery sequence and rollback

Each stage is an independently buildable change with its own behavior tests and review. Implementation planning follows approval of this specification.

1. **Characterize the baseline.** Pin catalog mapping/paging, auth scopes, retained work IDs, cancellation, package validation and policy truth tables before moving code.
2. **Introduce contracts and domain models.** Add boundary mappings and Hilt adapter bindings while current entry points continue to work.
3. **Move catalog presentation.** Move screens/view models/resources, remove verified unused legacy routes and keep host navigation hooks small.
4. **Separate enterprise sessions.** Move Firebase observation/membership/storage and introduce the host entry coordinator with explicit sign-out scopes.
5. **Unify download ownership.** Correct retained handles and validation, then implement the selected continuation/update rules through one coordinator and host installer adapter.
6. **Extract policies and support.** Move eligibility, recovery, runtime logging and branding while preserving upstream algorithms in the host.
7. **Tighten dependencies and checks.** Narrow `api` exports, declare host dependencies explicitly, enable style checks for both modules and enforce domain/module boundaries.
8. **Validate the integrated result.** Run JVM, Android integration, build, upgrade and behavioral checks; rehearse a non-destructive upstream merge to inspect remaining integration points.

Keep preference names and the database schema unchanged throughout. Do not remove legacy code until its replacement has passed the relevant scenario and references have been checked. Roll back a failed stage by reverting its own commit/PR; stop downstream stages until the previous boundary works. Never replace live application data to recover a refactor.

## 10. Acceptance and evidence

The capability files under `openspec/changes/isolate-sleke-features/specs` are the normative behavior contracts. The human design and those scenarios together define the review surface.

| Evidence | Required result |
| --- | --- |
| Architecture | No `:sleke → :app` dependency, Aurora implementation import or platform/network type in feature domain contracts |
| Catalog tests | Metadata failure preserves source rows/URLs; enrichment preserves identity fields; varying page sizes do not duplicate or skip |
| Session tests | Current-user membership, denial versus failure, listener cleanup, sign-out races and scoped logout semantics pass |
| Download tests | Retained unique-work identity, package mismatch, HTTP/cancellation cleanup, terminal observation and installer handoff scenarios pass |
| Policy/recovery tests | Both update modes, empty versus failed catalog, update-only GMS exclusion and all existing restart-guard boundaries pass |
| Build and style | Both modules compile; vanilla debug and instrumentation APKs build; relevant other flavor/release compilation is checked; style checks pass without new suppressions |
| Runtime acceptance | Real Android tests cover email cancellation, catalog/enterprise navigation, screen/process recreation, preferred-installer fallback, rejection and pending confirmation |
| Compatibility | Existing preferences and database version 13 survive an upgrade; app ID/version and signing configuration remain compatible |
| Upstream maintenance | Remaining product hooks are listed and a merge rehearsal reports text conflicts separately from runtime acceptance |

The prior merge baseline had six passing recovery JVM tests, successful vanilla-debug app/test APK builds and 128 existing lint findings. Those are baseline observations, not acceptance for the future refactor. Missing local emulator images must not be used to mark runtime cases as passed; complete them on an available device/emulator before claiming runtime acceptance.

## 11. Review decisions

Approve the ownership model, scoped sign-out table, preserved eligibility modes and explicit reliability corrections together. The three installation/access decisions in section 7 are confirmed. Review the complete architecture and acceptance scenarios before finalizing the execution plan.

Implementation details left to the builder include private class names, constructor layout, pure helper decomposition and the exact test harness. They must not alter the public responsibilities or observable scenarios above.

## 12. Source map

- [Merged repository snapshot](https://github.com/snvd-io/SLEKE-store/tree/c58f58eb0f8925a03fc579604c7e802a58866e71)
- `app/src/main/java/com/sleke/presentation`: catalog, enterprise and custom-download presentation.
- `app/src/main/java/com/aurora/store/data/repository/GPlayRepository.kt`: current metadata mapping and fallback.
- `app/src/main/java/com/aurora/store/viewmodel/auth/AuthViewModel.kt` and `compose/ui/splash/SplashScreen.kt`: mixed enterprise/Play entry.
- `sleke/src/main/java/com/sleke/library/data/repository/FirestoreApkRepository.kt`: catalog, enterprise membership and exception handling.
- `sleke/src/main/java/com/sleke/library/worker/ApkDownloadWorker.kt` and app-layer `AppDownloadManager.kt`: transfer, work identity and installation handoff.
- `app/src/main/java/com/aurora/store/data/work/UpdateWorker.kt` and `viewmodel/all/InstalledViewModel.kt`: policy scopes.
- `app/src/main/java/com/aurora/store/data/model/RecoveryDecision.kt`: current recovery rule values.
- `app/src/main/java/com/aurora/store/util/CrashlyticsTree.kt`: current logging adapters.
