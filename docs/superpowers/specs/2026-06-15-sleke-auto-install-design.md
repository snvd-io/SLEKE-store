# Auto-install on SLEKE Store download process — Design

Date: 2026-06-15
Status: Approved (pending spec review)
Scope: SLEKE custom download flow only

## Goal

After an app finishes downloading through the **SLEKE custom store flow**, install it
**silently** (zero user tap) wherever the user's configured installer allows, falling
back gracefully to the system install prompt otherwise.

"SLEKE custom flow" = the `com.sleke` screens (SLEKE Apps / Enterprise apps lists),
whose downloads go through `AppDownloadManager → ApkDownloadWorker`. The main Aurora
Store flow (`DownloadWorker`) is **out of scope** — it already supports silent install
via the user's installer preference.

## Reality of "fully silent"

True zero-tap install requires a **privileged installer**: Root, Shizuku, the Aurora
privileged service, or device-owner. With the plain **Session** installer, Android
still shows a confirmation for *first-time* installs (silent updates are allowed on
Android 12+ only when SLEKE is the package's update-owner). Therefore:

- "Fully silent" = silent wherever the user's configured installer permits it.
- The OS install prompt is the graceful fallback for the plain Session/Native installer.

Routing SLEKE downloads through Aurora's existing `AppInstaller` delivers exactly this
behavior with no changes to the installers themselves.

## Current state (what exists today)

The SLEKE flow triggers install in **three** places, all via `Context.installApp()`,
which only fires an `ACTION_VIEW` intent (the system "Install?" prompt requiring a tap):

1. `ApkDownloadWorker.doWork()` (sleke module) — on download success.
2. `AppDownloadManager.observeWork()` (app module) — on `WorkInfo.State.SUCCEEDED`
   (redundant with #1).
3. `SlekeAppsScreen` / `EnterpriseAppsPane` — the manual "Install" button on the
   `SimpleAppUiState.Downloaded` state.

Relevant existing infrastructure:

- `AppInstaller` (app module) exposes `getPreferredInstaller()` returning an
  `IInstaller` (Session/Root/Shizuku/Service/AM/microG) chosen by the user's preference.
- `IInstaller.install(download: Download)` stages and commits the install. Installers
  read APK files via `InstallerBase.getFiles(packageName, versionCode)`, which lists
  `.apk` files from `PathUtil.getAppDownloadDir(context, packageName, versionCode)`
  (= `File(cacheDir, "downloads")/<pkg>/<versionCode>/`).
- `Download.fromExternalApk(externalApk: ExternalApk)` builds a `Download` for a
  non-Play APK (single file, no shared libs) — the right vehicle for SLEKE apps.
- `InstallerStatusReceiver` already handles the install-result broadcast and is
  `SlekeConstants.EXTRA_IS_CUSTOM_STORE`-aware.

Module dependency is one-way: `app → :sleke`. So `ApkDownloadWorker` (in `:sleke`)
**cannot** reference `AppInstaller`/`PathUtil` (in `app`), but `AppDownloadManager`
(in `app`, package `com.sleke.presentation.download`) **can**.

## Chosen approach

**Reuse `AppInstaller` via the app-layer.** Centralize the install trigger in
`AppDownloadManager`:

1. `ApkDownloadWorker` only downloads + verifies, and reports the APK's metadata.
2. `AppDownloadManager` stages the downloaded APK into the path `AppInstaller`
   expects, builds a `Download`, and calls `getPreferredInstaller().install()`.

This reuses the full, tested installer matrix unchanged and respects the user's
installer preference. Accepted cost: one internal file copy (the worker writes to
external files dir; `PathUtil` uses internal cache dir — different filesystems, so a
copy rather than a rename is required).

Rejected alternative — adding a raw-`File` install API to every installer
(Session/Root/Shizuku/Service/AM/microG): avoids the copy but multiplies surface area
and duplicates staging logic. Not worth it.

## Changes by component

### `ApkDownloadWorker` (sleke module)
- Remove the `installApp(uri)` call from the `doWork()` success path.
- After download + package-name extraction, also extract `versionCode` and `targetSdk`
  from the APK archive. Generalize the existing `Context.extractPackageName(path)`
  helper in `ContextExt.kt` into an APK-info extractor (e.g. returns package name,
  versionCode, targetSdk) using `PackageManager.getPackageArchiveInfo`.
- Add to the worker's `Result.success` output data: the absolute APK file path
  (`KEY_APK_PATH`), `versionCode`, and `targetSdk`, alongside existing `KEY_PACKAGE`
  and `KEY_APK_URI`.

### `AppDownloadManager` (app module, `com.sleke.presentation.download`)
- Inject `AppInstaller`.
- In `observeWork()` `SUCCEEDED` branch, replace `context.installApp(uri)` with:
  1. If already installed → `SimpleAppUiState.Installed` (unchanged).
  2. Otherwise: copy the downloaded APK into
     `PathUtil.getAppDownloadDir(context, pkg, versionCode)` (create dirs as needed).
  3. Build a `Download` for it (via `Download.fromExternalApk` / a minimal builder
     with `targetSdk` set from the extracted value).
  4. `runCatching { appInstaller.getPreferredInstaller().install(download) }`.
  5. On failure → log + fall back to `context.installApp(uri)` so the download is
     never left un-installable.
- Emit `SimpleAppUiState.Downloaded(uri, pkg)` after kicking off install (the actual
  installed state is reflected once the OS/`InstallerStatusReceiver` completes, same
  as today).

### UI (`SlekeAppsScreen`, `EnterpriseAppsPane`)
- No structural change. The manual "Install" button on the `Downloaded` state stays as
  a fallback. With silent install configured, most flows go straight to `Installed`.

## Data flow (after change)

```
startDownload(url, pkg)
  → ApkDownloadWorker: download + verify + extract {pkg, versionCode, targetSdk}
  → SUCCEEDED { KEY_PACKAGE, KEY_APK_URI, KEY_APK_PATH, versionCode, targetSdk }
  → AppDownloadManager:
        if installed → Installed
        else: copy APK → PathUtil.getAppDownloadDir(pkg, versionCode)
              build Download
              getPreferredInstaller().install(download)   // silent where allowed
              (on throw → installApp(uri) prompt fallback)
  → InstallerStatusReceiver handles result broadcast (EXTRA_IS_CUSTOM_STORE aware)
```

## Error handling

- Download/verify failure → existing `SimpleAppUiState.Error` path (unchanged).
- Install failure (exception from `install()`) → log via Timber + fall back to the
  `installApp()` system prompt.
- Missing/invalid extracted metadata (null package name / versionCode) → treat as a
  failure of that step and fall back to `installApp()`.

## Removed redundancy

The duplicate install trigger inside `ApkDownloadWorker` is removed. Install is
triggered exactly once, from the app layer.

## Out of scope

- Main Aurora `DownloadWorker` flow (already silent-capable).
- Adding a new installer type or a SLEKE-specific silent-install preference toggle.
- Changing any existing installer implementation.

## Testing

- Manual: download a SLEKE app on a device with (a) plain Session installer — expect
  OS prompt fallback on fresh install; (b) Shizuku/Root configured — expect zero-tap
  install.
- Verify no double install prompt appears (confirms the worker-side install was
  removed).
- Verify the staged APK lands in `PathUtil.getAppDownloadDir` and install reads it.
- Verify install failure falls back to the `installApp()` prompt.
