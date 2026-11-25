# microG package-override consent access for the SLEKE store — Design

Date: 2026-11-25
Status: Approved (guarded variant). Store side implemented; ROM side pending.
Scope: ROM microG (GmsCore) manifest, ROM privileged-permission allowlist, SLEKE store manifest

## Problem

Google sign-in in the store ends in an endless loading indicator on OdysseyOS 4.03.07 (Pixel 7).

1. The store calls `AccountManager.getAuthToken(account, "oauth2:https://www.googleapis.com/auth/googleplay", options, activity, ...)`. `options` carries `overridePackage = com.android.vending` and the Play certificate (`GoogleAccountTokenProvider.kt:87-103`, `SplashScreen.kt:393-411`).
2. microG does not return a token. It returns an intent for its consent dialog, `org.microg.gms.auth.AskPackageOverrideActivity`.
3. `AccountManager` starts that intent from the store's process. The activity manager refuses:

```
SecurityException: Permission Denial: starting Intent { cmp=com.google.android.gms/org.microg.gms.auth.AskPackageOverrideActivity ... }
from ... com.sleke.store (uid 10098) not exported from uid 10131
```

4. The exception is thrown inside `AccountManager`'s own callback, so the store's request never completes. The store has no timeout, so the spinner never stops.

The manifest of the GmsCore on the phone (0.3.17.252432-27, `/product/priv-app/GmsCore/GmsCore.apk`) and upstream microG `master` both declare the activity with `android:exported="false"`. The ROM carries no patch for this today.

## Goal

The SLEKE store, and only privileged apps, can start the consent dialog. The user approves once, the next token request succeeds, and the store reaches its home screen. No unprivileged app can start the dialog.

## Non-goals

- Exporting the activity to every app.
- Pre-approving the override for the store without user consent.
- Changing the store's login screen or adding the timeout. That is a separate store change.
- Changing other microG activities, unless verification shows a second denial (see Risks).

## Design

The activity reads the requesting package, account and override package from the intent extras, and asks the user to confirm. It does not check who started it. A plain `exported="true"` would let any installed app open the prompt with extras of its choosing. The design therefore exports it behind an existing platform permission that only privileged apps can hold.

### 1. microG manifest (ROM)

`play-services-core/src/main/AndroidManifest.xml`, the `AskPackageOverrideActivity` entry (line 538 upstream).

Before:

```xml
<activity
    android:name="org.microg.gms.auth.AskPackageOverrideActivity"
    android:excludeFromRecents="true"
    android:exported="false"
    android:process=":ui"
    android:theme="@style/Theme.App.Translucent" />
```

After:

```xml
<activity
    android:name="org.microg.gms.auth.AskPackageOverrideActivity"
    android:excludeFromRecents="true"
    android:exported="true"
    android:permission="android.permission.GET_ACCOUNTS_PRIVILEGED"
    android:process=":ui"
    android:theme="@style/Theme.App.Translucent" />
```

`android.permission.GET_ACCOUNTS_PRIVILEGED` has protection level `signature|privileged`. Callers that pass the check:

- apps in `/system/priv-app` that request it and are listed in the privileged-permission allowlist (the store, after step 3);
- system_server, which launches notification intents for `AccountManager`;
- GmsCore itself, since it runs in the same uid.

User-installed apps cannot hold it, so they still cannot start the activity.

GmsCore must be rebuilt by the ROM build and signed by its pipeline. Repacking the shipped APK by hand changes its signature and is out of scope.

### 2. Store manifest

`app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.GET_ACCOUNTS_PRIVILEGED" />
```

On a normal user install the permission is not granted and nothing changes. On a privileged install it is granted only if step 3 is already in place.

### 3. ROM privileged-permission allowlist

`/system/etc/permissions/com.sleke.store.xml`. Add one entry to the existing list:

```xml
<permission name="android.permission.GET_ACCOUNTS_PRIVILEGED"/>
```

The file then lists `INSTALL_PACKAGES`, `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW` and `GET_ACCOUNTS_PRIVILEGED`.

## Rollout order

The allowlist must be in place before the store build that requests the permission is scanned.

1. Allowlist entry (step 3).
2. GmsCore build with the manifest change (step 1).
3. Store build with the `uses-permission` (step 2).

On the Pixel 7, do all three in one `adb root && adb remount` session, push the allowlist file first, back up each original file before overwriting it, then reboot once.

## Risks

- **Boot loop on an allowlist mismatch.** On 2026-11-25 the `preload` flavor requested `DELETE_PACKAGES`, which was not allowlisted, and system_server crashed on every boot with `Signature|privileged permissions not in privileged permission allowlist`. adbd stayed up, and restoring the original APK and rebooting recovered the phone.
- **A second consent screen.** After the override is approved, microG may return `org.microg.gms.auth.AskPermissionActivity`, also `exported="false"` upstream, for the scope consent. If verification shows a second denial, apply the same change to that activity.
- **Exposure of the dialog.** The dialog trusts its intent extras, which is why the export is guarded by a permission and not left open. Holders are limited to privileged apps and the system.
- **The store still hangs forever when `AccountManager` drops a callback.** The export fixes this cause. The missing timeout is a separate store change.

## Verification

1. GmsCore manifest: `aapt2 dump xmltree --file AndroidManifest.xml GmsCore.apk` shows `exported=true` and `permission="android.permission.GET_ACCOUNTS_PRIVILEGED"` on `AskPackageOverrideActivity`.
2. Store permission: `adb shell dumpsys package com.sleke.store | grep GET_ACCOUNTS_PRIVILEGED` shows `granted=true`.
3. Positive: tap Google sign-in in the store. The consent dialog appears, and `adb logcat` has no `Permission Denial ... AskPackageOverrideActivity`. After approval the token request succeeds and the store leaves the loading screen.
4. Negative: from a user-installed test app, start the activity directly. It must fail with a permission denial.
5. Boot: the phone reaches `sys.boot_completed=1` and Carthage is on screen.

## Rollback

Push the backed-up original `GmsCore.apk`, `com.sleke.store.xml` and `SlekeStore.apk` back with `adb root && adb remount`, restore owners and SELinux labels with `restorecon -R`, then reboot. The same recovery works from a boot loop.

## Alternative, store only (not tested)

`GoogleAccountTokenProvider` already has a branch that calls `getAuthToken(account, type, options, notifyAuthFailure = true, callback, handler)` without an activity. In that mode `AccountManagerService` posts a notification for the consent intent, and the system launches it, which is allowed for non-exported activities. The first request returns the intent and no token. After the user approves in the notification, a second request would return a token.

This needs no ROM change. The store would have to explain the notification step and request the token again when the user returns. Whether microG's override flow completes this way has not been tried, and it should be tried before changing the ROM.

## Open questions

1. Where is the ROM's microG source or build recipe? It was not found on this machine.
2. Does a second consent screen follow the override approval?
3. Resolved: the ROM ships the `vanilla` flavor. The original system store (4.8.1) requested neither `INSTALL_PACKAGES` nor `DELETE_PACKAGES` and has no `-preload` version suffix, so the permission goes in the main manifest. The `preload` flavor also requests `DELETE_PACKAGES`, which is not in the allowlist. Do not ship `preload` to the ROM unless that entry is added too.
