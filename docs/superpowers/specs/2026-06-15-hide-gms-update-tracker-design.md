# Hide Google Play Services from the update tracker — Design

Date: 2026-06-15
Status: Approved — executed
Scope: SLEKE Store update tracking

## Goal

Prevent **Google Play Services** (`com.google.android.gms`) from appearing anywhere in
the SLEKE Store update tracker: it must never be checked, listed in the Updates screen,
notified, or auto-updated.

Decisions:
- **Exclude entirely** (not merely hidden from the list).
- **Hardcoded / always-on** — no user setting.
- **Match by package name** — also covers microG, which masquerades as
  `com.google.android.gms`.

## Single choke point

Every update-population path funnels through `UpdateWorker.checkUpdates()`:

- Manual pull-to-refresh / settings "check now" → `UpdateHelper.checkUpdatesNow()` →
  enqueues `UpdateWorker`.
- Periodic background check → `UpdateWorker`.

The Updates UI (`UpdatesViewModel.updates` → `UpdateHelper.updates`) and notifications
only read from `UpdateDao`. So filtering inside `checkUpdates()` covers all surfaces.

## Change

In `UpdateWorker.checkUpdates()`, exclude GMS in the early package-collection stage,
alongside the existing blacklist filter:

```kotlin
val packages = PackageUtil.getAllValidPackages(context)
    .filterNot { it.packageName == Constants.PACKAGE_NAME_GMS } // hide Play Services / microG
    .filterNot { blacklistProvider.isBlacklisted(it.packageName) }
    .filter { ... }
```

`com.aurora.Constants` is already imported; `PACKAGE_NAME_GMS` is the existing constant
`"com.google.android.gms"`.

Filtering at this stage means GMS is dropped before any Play details query, so it is
never fetched, never inserted into `UpdateDao`, and therefore never shown, notified, or
auto-updated.

## No DB migration / stale-row handling

`UpdateDao.insertUpdates()` runs `deleteAll()` then `insertAll()` — a full table rebuild
on every check. Any GMS row currently present is removed automatically on the next
check. No migration or explicit cleanup is required.

## Out of scope

- Hiding GMS from search, app details, or the installed-apps list (only the update
  tracker is in scope).
- A user-facing toggle.
- Distinguishing genuine Google Play Services from microG (intentionally matched by
  package name).

## Testing

- `./gradlew :app:compileVanillaDebugKotlin` — must build.
- Manual (device with a logged-in account and GMS showing as updatable): run an update
  check; confirm Google Play Services no longer appears in the Updates list or update
  notifications, and is not auto-updated.
- No unit test added: the filter sits in a private suspend method inside a Hilt worker
  with heavy dependencies, offering no clean test seam for a one-line filter.
