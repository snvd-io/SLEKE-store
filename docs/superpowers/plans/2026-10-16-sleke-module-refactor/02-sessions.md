# SLEKE Enterprise Sessions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current Firebase identity authoritative for enterprise membership while preserving independent Play identities and predictable entry/sign-out behavior.

**Architecture:** One library session repository observes Firebase and reconciles existing storage. A host entry coordinator awaits Play preparation and owns navigation effects; scoped cleanup uses separate enterprise, cached-Play and Room-account operations.

**Tech Stack:** Kotlin, Flow, Hilt, Firebase Auth/Firestore, existing DataStore, FirebaseUI and Aurora AuthProvider/AccountRepository.

**Spec:** [Approved design](../../specs/2026-10-16-sleke-module-refactor-design.md), section 6; OpenSpec `enterprise-session` and the access-dependent download scenarios.

## Global Constraints

Apply the [coordinator](../2026-10-16-sleke-module-refactor-plan.md). Preserve `com.sleke.store`, 76 / `4.8.4`, minimum SDK 26, database 13 and preference file `sleke_prefs` with `signed_uid`, `signed_email`, `is_enterprise`. Email enterprise entry prepares anonymous Play; Google/microG/account-add entry does not fabricate Firebase identity. Full sign-out invalidates outstanding results before cleanup. No production backend-rule changes.

## Review Focus

E2/E3 test delayed membership/Play completion after full sign-out, provider cancellation with an existing session, membership outages versus denial, and navigation recreation. A caller-supplied UID is never an authorization input.

---

## Task E1: Session outcomes and host service contracts

**Files:**
- Create: `sleke/src/main/java/com/sleke/library/enterprise/domain/EnterpriseSessionState.kt`, `EnterpriseSessionRepository.kt`, `PlaySessionGateway.kt`, `CleanupReport.kt` in that directory.
- Create: `sleke/src/main/java/com/sleke/library/enterprise/domain/EnterpriseEntryPolicy.kt`.
- Create: `sleke/src/test/java/com/sleke/library/enterprise/domain/EnterpriseEntryPolicyTest.kt`.

**Interfaces:** Produces `EnterpriseIdentity(uid: String)`; `EnterpriseSessionState` variants `Restoring`, `SignedOut`, `Checking(identity)`, `Allowed(identity, enterpriseId: String, enterpriseName: String)`, `NoMembership(identity)`, `Unavailable(identity)`. Public state carries no Firebase user, email, document or credential. `EnterpriseSessionRepository` exposes `state: StateFlow<EnterpriseSessionState>`, suspending `refresh(): EnterpriseSessionState`, and `signOut(): CleanupReport`.

Produces `PlaySessionGateway.prepareAnonymous(): PlayPreparation`, `resetCachedAuth(): CleanupReport`, `removeAllAccounts(): CleanupReport` (all suspending). `PlayPreparation` is `Ready` or `Failed(cause: Throwable)`. `CleanupReport(failures: List<CleanupFailure>)` has `isComplete` and `operator fun plus(other: CleanupReport): CleanupReport`; `CleanupFailure(stage: CleanupStage, cause: Throwable)` uses stages `ENTERPRISE`, `PLAY_CACHE`, `PLAY_ACCOUNTS`.

Produces `EnterpriseEntryPolicy.destination(session: EnterpriseSessionState, playReady: Boolean): EntryDestination`, with `Wait`, `RegularCatalog`, `EnterpriseCatalog`. Signed-out/restoring/checking is `Wait`; authenticated denied/unavailable is regular only when Play is ready; allowed is enterprise only when Play is ready.

- [ ] **Step 1: Write policy tests.** For every session state and both Play-ready values, assert the table above. `outageAllowsOnlyPublicEntry` asserts `assertEquals(RegularCatalog, destination(Unavailable(identity), true))`; the false case is `Wait`. `deniedIsDistinctFromUnavailable` asserts unequal states with the same identity. Combine a failed enterprise cleanup with successful Play cleanup and assert the failure is retained once.
- [ ] **Step 2: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*EnterpriseEntryPolicyTest'`; expect missing domain symbols.
- [ ] **Step 3: Implement the immutable domain contracts/policy.** Keep identity/membership distinct from Play readiness and stored enterprise hints. Repository implementations and Hilt bindings follow E2/E3; do not add fake production sessions.
- [ ] **Step 4: Run green and commit.** Repeat focused tests and common gate. Commit `refactor: define enterprise session and entry contracts`.

## Task E2: One current-identity Firebase session repository

**Files:**
- Create: `sleke/src/main/java/com/sleke/library/enterprise/data/FirebaseSessionSource.kt`, `FirebaseEnterpriseSessionRepository.kt`, `EnterpriseSessionStorage.kt`.
- Create: `sleke/src/test/java/com/sleke/library/enterprise/data/EnterpriseSessionRepositoryTest.kt`.
- Create: `app/src/androidTest/java/com/sleke/integration/auth/EnterpriseSessionStorageTest.kt`.
- Modify: `sleke/src/main/java/com/sleke/library/data/datastore/SlekePreferencesDataStore.kt`, `SlekePreferencesDataStoreImpl.kt` and `sleke/src/main/java/com/sleke/library/di/RepositoryModule.kt`.
- Modify: legacy enterprise methods in `sleke/src/main/java/com/sleke/library/data/repository/FirestoreApkRepository.kt` to delegate until callers migrate.

**Interfaces:** Consumes E1. `FirebaseSessionSource` is a data-only seam: `identities: Flow<EnterpriseIdentity?>`, suspending `membership(): MembershipResult` and `signOut()`. `membership()` reads `FirebaseAuth.currentUser` internally; `MembershipResult` is a data-layer result carrying allowed enterprise details, no-membership or failure. `EnterpriseSessionStorage` adapts existing preference fields and retains email only inside data storage. The repository owns a cancellable scope, one upstream auth listener and a monotonic identity generation.

- [ ] **Step 1: Write current-user and race tests.** Fake `FirebaseSessionSource` with a controllable identity flow and membership deferred. `storedEnterpriseHintCannotGrantAccess` seeds a different stored UID and asserts no `Allowed` state before current-user confirmation. `identityChangesDuringMembership` completes A after B and asserts A is never published/persisted. `outageIsRetryable` scripts failure then allowance; assert `Unavailable` then `Allowed` and two source calls. `confirmedDenialClearsAccess` asserts `NoMembership`, not an empty success or transient outage. `lateCompletionAfterFullSignOutIsDiscarded` cancels the old generation and completes its deferred; assert state stays signed out and storage remains cleared.
- [ ] **Step 2: Write lifecycle/storage tests.** A fake source counts attach/detach: repeated collectors assert `assertEquals(1, attaches)` and disposing the owner asserts `assertEquals(1, detaches)`. Android storage test seeds the existing preference names, reconciles matching/mismatched users and asserts no key renaming or email leakage through domain state.
- [ ] **Step 3: Run red.** `./gradlew :sleke:testDebugUnitTest --tests '*EnterpriseSessionRepositoryTest'`; source fakes test the repository without Android/Firebase static calls.
- [ ] **Step 4: Implement the session owner.** Use `callbackFlow`/`awaitClose` for the Firebase listener, shared once in the repository's owned scope. Resolve current user at membership IO, capture generation, recheck before storage and state publication. Propagate cancellation; distinguish real denial from exceptions. `signOut` invalidates generation first and attempts Firebase and preference cleanup independently. Keep enough cleanup failure state for explicit retry. Remove full account-document and email/UID logging from migrated code.
- [ ] **Step 5: Bind/delegate, run green and commit.** Legacy membership methods must use current-session results and cannot grant an arbitrary supplied UID access; migrate actual callers in E3/C4 before deleting the obsolete signatures in S3. Run focused suite/common gate and build the app test APK. Commit `refactor: centralize enterprise identity and membership`.

## Task E3: Awaited email entry and explicit sign-out scopes

**Files:**
- Create: `sleke/src/main/java/com/sleke/library/enterprise/presentation/EnterpriseEmailEntry.kt`.
- Create: `app/src/main/java/com/sleke/integration/auth/PlaySessionAdapter.kt`, `SlekeEntryCoordinator.kt`, `SessionMutationGate.kt`, `SignOutCoordinator.kt`.
- Create: `app/src/test/java/com/sleke/integration/auth/SlekeEntryCoordinatorTest.kt`, `SignOutCoordinatorTest.kt`.
- Create: `app/src/androidTest/java/com/sleke/integration/auth/SlekeEmailEntryTest.kt`, `ScopedSignOutTest.kt`.
- Modify: `app/src/main/java/com/aurora/store/viewmodel/auth/AuthViewModel.kt`, `app/src/main/java/com/aurora/store/compose/ui/splash/SplashScreen.kt`, `app/src/main/java/com/aurora/store/usecase/LogoutUseCase.kt`.
- Modify scoped reset call sites: `app/src/main/java/com/aurora/store/viewmodel/spoof/SpoofViewModel.kt`, `app/src/main/java/com/aurora/store/data/work/AuthWorker.kt`; verify account switch/removal in `app/src/main/java/com/aurora/store/viewmodel/accounts/AccountsViewModel.kt`.
- Move: FirebaseUI theme/style resources from `app/src/main/res/values/themes.xml` to library-owned, prefixed resources in `sleke/src/main/res/values/styles.xml`; keep `sleke_login_logo.xml`.
- Modify: `app/src/main/java/com/sleke/integration/di/SlekeIntegrationModule.kt` to bind `PlaySessionGateway` to `PlaySessionAdapter`; library repository bindings remain in `RepositoryModule.kt`.

**Interfaces:** Consumes E1/E2. `EnterpriseEmailEntry(onResult: (EmailEntryResult) -> Unit)` owns its FirebaseUI launcher/resources; `EmailEntryResult` is `Completed`, `Cancelled` or `Failed(cause)` with no caller UID/email authorization fields. `PlaySessionAdapter` implements E1 through existing `AuthProvider` and `AccountRepository`, on IO.

Produces host-only `EntryIntent(packageName: String?, developerId: String?)`, `EntryNavigation(id: Long, destination: EntryDestination, intent: EntryIntent)` and `SlekeEntryCoordinator.completeEmailEntry(intent: EntryIntent)` (suspending), `navigation: StateFlow<EntryNavigation?>`, `consumeNavigation(id: Long)`. Use a saved-state owner for the original intent and consumed effect ID. `SessionMutationGate` exposes `capture(): Long`, `invalidate(): Long`, and suspending `commitIfCurrent(epoch: Long, block: suspend () -> Unit): Boolean`; persistence/effect publication and full sign-out invalidation serialize through the same gate.

Produces `SignOutScope.ENTERPRISE`, `PLAY_RESET`, `FULL`; `SignOutCoordinator.signOut(scope: SignOutScope): CleanupReport` (suspending). `LogoutUseCase.invoke(scope: SignOutScope)` requires an explicit scope at every caller; no ambiguous default. Play reset preserves enterprise identity, enterprise-only preserves Play and Room, full attempts all three cleanup stages. Play account removal/switch keeps upstream default-promotion and binding cleanup.

- [ ] **Step 1: Write entry tests.** `entryWaitsForPlay` holds Play preparation and asserts `assertNull(navigation.value)` until ready. `outageEntersRegularCatalog` asserts regular destination with a membership retry. `providerCancellationKeepsPreviousSession` asserts zero sign-out calls and busy reset. `recreationConsumesNavigationOnce` saves/restores intent/effect IDs, asserting one consumption and the original package/developer target. `googleEntryDoesNotCreateEnterpriseIdentity` asserts zero Firebase sign-in calls.
- [ ] **Step 2: Write cleanup/race tests.** `lateCompletionAfterFullSignOutIsDiscarded` holds the result immediately before commit, signs out and then releases it; assert no account upsert, membership persistence or navigation. For each failing cleanup stage assert all other stages run and the report identifies the failed stage. Test repeated full sign-out, enterprise-only, Play reset, account switch/removal and device-spoof reset. Room Android test seeds multiple accounts/bindings and asserts full cleanup leaves both empty, while removing one account preserves/promotes the correct other account.
- [ ] **Step 3: Run red.** `./gradlew :app:testVanillaDebugUnitTest --tests '*SlekeEntryCoordinatorTest' --tests '*SignOutCoordinatorTest'`.
- [ ] **Step 4: Implement host coordination and the library email component.** Await preparation/persistence; remove nested fire-and-forget `launch` from the suspending verification chain. Route all affected final account writes and navigation through the gate, including delayed Google/anonymous verification. Full sign-out invalidates host and enterprise generations before cleanup. Reuse `AccountRepository.removeAccount` for Room/binding cleanup and continue other cleanup stages after ordinary failures; never swallow coroutine cancellation. Temporary membership failure retains authenticated public access and a visible refresh action.
- [ ] **Step 5: Replace mixed Firebase branches in AuthViewModel/SplashScreen.** Preserve upstream Google, microG, account-add, auth-failure and deep-link behavior; use the feature email component and coordinator at small integration points. Assign `PLAY_RESET` explicitly to auth-token/device-spoof reset callers, and `FULL` only to explicit whole-app sign-out. Remove the redundant Firebase listener and commented Google-to-Firebase credential path. No personal identity logging remains in these paths.
- [ ] **Step 6: Run green and commit.** Run focused suites/common gate and test-APK build; Android acceptance uses `SlekeEmailEntryTest`, `ScopedSignOutTest` and `EnterpriseSessionStorageTest`. Commit `refactor: coordinate enterprise entry and scoped sign-out`. Device-dependent cancellation, lifecycle and deep-link cases remain unchecked until executed.
