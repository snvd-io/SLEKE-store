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
            putExtra(SlekeConstants.EXTRA_IS_CUSTOM_STORE, true)
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
    return extractApkInfo(apkFilePath)?.packageName
}

/**
 * Metadata extracted from a downloaded APK archive, used to stage the file into the
 * location the installer expects and to install it with the correct parameters.
 */
data class ApkInfo(
    val packageName: String,
    val versionCode: Long,
    val targetSdk: Int
)

fun Context.extractApkInfo(apkFilePath: String): ApkInfo? {
    val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageArchiveInfo(apkFilePath, PackageManager.PackageInfoFlags.of(0L))
    } else {
        packageManager.getPackageArchiveInfo(apkFilePath, PackageManager.GET_META_DATA)
    } ?: return null

    val packageName = packageInfo.packageName ?: return null
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    val targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 1

    return ApkInfo(packageName, versionCode, targetSdk)
}

fun Context.isAppInstalled(packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}