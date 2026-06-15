# Friendly app-details load error with auto-recovery — Design

Date: 2026-06-15
Status: Approved (pending spec review)
Scope: App details screen load-failure handling only

## Goal

Replace the misleading "App not available for your device" message shown when the app
details page fails to load, and add automatic recovery before falling back to a
user-facing message.

New message (English source):
> "Oops! We had trouble loading this page. Try closing the app and reopening it."

Recovery ladder (decided with the user):
1. **Retry in place** (silent re-fetch, no disruption).
2. **Background quit + restart** (ProcessPhoenix) if the retry fails.
3. **Manual message** (the friendly text above) if it still fails after the restart.

Other decisions:
- After a restart, the user lands on the **home screen** (ProcessPhoenix default). The
  failed page is abandoned; the user can re-navigate.
- Scope is the **app details page only** (the single place this message is used today).

## Current state

- `toast_app_unavailable` = "App not available for your device" is used in exactly one
  place: `AppDetailsScreen.ScreenContentError`, rendered by the shared `Error`
  composable when `viewModel.state` is `AppState.Error`.
- `AppState.Error(message)` is set in `AppDetailsViewModel.fetchAppDetails()` when
  `appDetailsHelper.getAppByPackageName(packageName)` throws — i.e. a **load failure**,
  not real device incompatibility.
- The app already bundles **ProcessPhoenix**
  (`com.jakewharton.processphoenix.ProcessPhoenix.triggerRebirth(context)`), used in
  `OnboardingViewModel` and `ForceRestartDialog`.
- `AppDetailsViewModel` has `@ApplicationContext context`, `viewModelScope`, and access
  to the `Preferences` util.
- The `Error` composable supports `message`, optional `actionMessage`, and `onAction`.
- `toast_app_unavailable` has ~45 translations; these are left untouched.

## Components & changes

### a. New string resource
Add to `app/src/main/res/values/strings.xml`:
```xml
<string name="app_details_load_error">Oops! We had trouble loading this page. Try closing the app and reopening it.</string>
```
English only; other locales fall back to English until translated via Weblate. Do not
modify `toast_app_unavailable` or its translations.

### b. `ScreenContentError` (AppDetailsScreen.kt)
Render the new friendly string instead of the raw exception/`toast_app_unavailable`:
```kotlin
message = stringResource(R.string.app_details_load_error)
```
Raw exception text is no longer surfaced to the user. The `AppState.Error` screen is now
only reached as the final fallback in the ladder.

### c. Pure recovery decision (new, unit-testable)
A small pure function (no Android deps) so the branching is testable without killing the
process. Suggested location: a new file under
`app/src/main/java/com/aurora/store/data/model/` (e.g. `RecoveryDecision.kt`).

```kotlin
enum class Recovery { RETRY, RESTART, SHOW_MESSAGE }

fun decideRecovery(
    retryAttempted: Boolean,
    lastRestartMs: Long,
    nowMs: Long,
    windowMs: Long
): Recovery = when {
    !retryAttempted -> Recovery.RETRY
    lastRestartMs != 0L && (nowMs - lastRestartMs) < windowMs -> Recovery.SHOW_MESSAGE
    else -> Recovery.RESTART
}
```

Branch semantics:
- First failure (`retryAttempted == false`) → `RETRY`.
- Retry failed, no recent restart (no guard, or guard older than window) → `RESTART`.
- Retry failed, restart already happened within the window → `SHOW_MESSAGE`.

### d. Persisted restart guard (`Preferences`)
The guard timestamp must survive the process restart, so it lives in SharedPreferences.
- Add `getLong` / `putLong` helpers to `com.aurora.store.util.Preferences` (it currently
  exposes only int/boolean/string).
- Add key `PREFERENCE_ERROR_RESTART_TS` (Long, epoch millis; `0L` = unset).
- Add a window constant `RESTART_GUARD_WINDOW_MS = 60_000L` (co-located with the
  recovery logic).

### e. `AppDetailsViewModel` orchestration
- Add an in-memory `private var retryAttempted = false` (per ViewModel instance).
- `fetchAppDetails(packageName)` (public entry): reset `retryAttempted = false`, then run
  the existing load.
- On the catch path, replace the direct `_state.value = AppState.Error(...)` with a
  `handleLoadFailure(packageName)` that:
  1. computes `decideRecovery(retryAttempted, Preferences.getLong(...PREFERENCE_ERROR_RESTART_TS, 0L), System.currentTimeMillis(), RESTART_GUARD_WINDOW_MS)`.
  2. `RETRY` → set `retryAttempted = true`, keep `AppState.Loading`, `delay(1000)`, call
     the load again.
  3. `RESTART` → `Preferences.putLong(context, PREFERENCE_ERROR_RESTART_TS, System.currentTimeMillis())`, then `ProcessPhoenix.triggerRebirth(context)`.
  4. `SHOW_MESSAGE` → `_state.value = AppState.Error(null)` (message text comes from the
     resource in the composable).
- On a **successful** fetch: clear the guard with
  `Preferences.putLong(context, PREFERENCE_ERROR_RESTART_TS, 0L)` and leave
  `retryAttempted` as-is (it resets on the next `fetchAppDetails` call).

`System.currentTimeMillis()` is fine in app code (the Date/random restriction only
applies to workflow scripts).

## Data flow

```
fetchAppDetails(pkg)            // retryAttempted = false
  └─ load → success → defaultAppState; clear guard
          → throw → handleLoadFailure(pkg)
                      decideRecovery(...)
                        RETRY        → retryAttempted=true; Loading; delay(1s); load again
                        RESTART      → save guard ts; ProcessPhoenix.triggerRebirth → home
                        SHOW_MESSAGE → AppState.Error(null) → friendly message screen
```

After a `RESTART`, the process relaunches at home. If the user hits another app-details
failure within the guard window, the ladder ends at `SHOW_MESSAGE` instead of restarting
again. Outside the window (or after any success), the guard is stale/cleared and a fresh
incident gets the full ladder again.

## Loop safety

- Single in-place retry (in-memory flag) absorbs transient blips.
- The persisted guard window caps restarts at one per ~60s incident, preventing an
  infinite restart loop for a persistently-failing page.
- Success clears the guard; staleness re-arms it.

## Known tradeoff

For a page that persistently fails (e.g. a genuinely region-locked app), the worst case
is: silent retry → one whole-app restart → land on home → user re-navigates → retry →
friendly message. At most one full restart per guard window. This is the accepted cost
of restart-first recovery.

## Out of scope

- Other load-error surfaces (home, search, etc.).
- Returning to the failed page after restart (we land on home).
- Re-translating the message into the ~45 existing locales (English fallback for now).
- Changing `toast_app_unavailable` or its translations.

## Testing

- `./gradlew :app:compileVanillaDebugKotlin` — must build.
- Unit tests for `decideRecovery` covering: first failure → RETRY; post-retry, no/stale
  guard → RESTART; post-retry within window → SHOW_MESSAGE.
- Manual: trigger a fetch failure (airplane mode / forced exception) and confirm the
  sequence silent-retry → background restart to home → friendly message on the repeat;
  confirm a normal successful load clears the guard.
