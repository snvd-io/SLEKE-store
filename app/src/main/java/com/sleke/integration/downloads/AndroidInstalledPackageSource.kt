package com.sleke.integration.downloads

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.sleke.library.downloads.domain.InstalledPackageSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidInstalledPackageSource @Inject constructor(
    @ApplicationContext private val context: Context
) : InstalledPackageSource {
    override suspend fun versionCode(packageName: String): Long? = withContext(Dispatchers.IO) {
        try {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(packageName, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
