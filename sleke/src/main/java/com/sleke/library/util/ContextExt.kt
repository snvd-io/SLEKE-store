package com.sleke.library.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import timber.log.Timber

fun Context.openApp(packageName: String) {
    runCatching {
        packageManager
            .getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.also(::startActivity)
    }.onFailure {
        Timber.tag("SlekeAppsScreen").e(it, "Failed to open app with package name: $packageName")
    }
}

fun Context.installApp(apkUri: String) {
    runCatching {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri.toUri(), "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(installIntent)
    }.onFailure {
        Timber.tag("SlekeAppsScreen").e(it, "Failed to install app from APK URI: $apkUri")
    }
}

fun Context.uninstallApp(packageName: String) {
    runCatching {
        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
            data = "package:$packageName".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(uninstallIntent)
    }.onFailure {
        Timber.tag("SlekeAppsScreen").e(it, "Failed to uninstall app with package name: $packageName")
    }
}

fun Context.extractPackageName(apkFilePath: String): String? {
    val packageInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageArchiveInfo(apkFilePath, PackageManager.PackageInfoFlags.of(0L))
    } else {
        packageManager.getPackageArchiveInfo(apkFilePath, PackageManager.GET_META_DATA)
    }
    return packageInfo?.packageName
}

fun Context.isAppInstalled(packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}