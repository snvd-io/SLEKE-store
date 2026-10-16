/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.services.IPrivilegedCallback
import com.aurora.services.IPrivilegedService
import com.aurora.store.AuroraApp
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PackageUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
@Deprecated("Deprecated in favour of RootInstaller")
class ServiceInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerBase(context) {

    private lateinit var serviceConnection: ServiceConnection
    private val executor = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue())

    companion object {
        const val ACTION_INSTALL_REPLACE_EXISTING = 2
        const val PRIVILEGED_EXTENSION_PACKAGE_NAME = "com.aurora.services"
        const val PRIVILEGED_EXTENSION_SERVICE_INTENT = "com.aurora.services.IPrivilegedService"

        val installerInfo: InstallerInfo
            get() = InstallerInfo(
                id = 3,
                installer = Installer.SERVICE,
                installerPackageNames = listOf(PRIVILEGED_EXTENSION_PACKAGE_NAME),
                title = R.string.pref_install_mode_services,
                subtitle = R.string.services_installer_subtitle,
                description = R.string.services_installer_desc
            )
    }

    override fun installInternal(download: Download, owned: OwnedInstallRequest?) {
        super.installInternal(download, owned)

        when {
            owned == null && isAlreadyQueued(download.packageName) -> {
                Log.i(TAG, "${download.packageName} already queued")
            }

            PackageUtil.isInstalled(context, PRIVILEGED_EXTENSION_PACKAGE_NAME) -> {
                Log.i(TAG, "Received service install request for ${download.packageName}")
                val fileList = getFiles(download.packageName, download.versionCode, owned = owned)
                xInstall(
                    download.packageName,
                    fileList.map { file ->
                        getUri(file).also { uri ->
                            context.grantUriPermission(
                                PRIVILEGED_EXTENSION_PACKAGE_NAME,
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    },
                    fileList.map { it.absolutePath },
                    owned
                )
            }

            else -> {
                owned?.onFinished?.invoke(false)
                if (owned == null) {
                    postError(
                        download.packageName,
                        context.getString(R.string.installer_status_failure),
                        context.getString(R.string.installer_service_unavailable)
                    )
                }
            }
        }
    }

    private fun xInstall(
        packageName: String,
        uriList: List<Uri>,
        fileList: List<String>,
        owned: OwnedInstallRequest?
    ) {
        val receipt = owned?.let { HostInstallReceipt("service", it.operationId) }
        fun reportError(packageName: String, error: String?, extra: String?) {
            if (owned == null) {
                postError(packageName, error, extra)
            } else {
                Log.e(TAG, "Owned service installer error: $error")
            }
        }
        executor.execute {
            val readyWithAction = AtomicBoolean(false)
            Handler(Looper.getMainLooper()).post {
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (owned == null && isAlreadyQueued(packageName)) {
                            if (::serviceConnection.isInitialized) {
                                context.unbindService(connection)
                            }
                            readyWithAction.set(true)
                            return
                        }
                        if (owned == null) AuroraApp.enqueuedInstalls.add(packageName)
                        val service = IPrivilegedService.Stub.asInterface(binder)

                        fun submit() {
                            if (service.hasPrivilegedPermissions()) {
                                Log.i(TAG, context.getString(R.string.installer_service_available))

                                val callback = object : IPrivilegedCallback.Stub() {

                                    override fun handleResult(
                                        packageName: String,
                                        returnCode: Int
                                    ) {}

                                    override fun handleResultX(
                                        packageName: String,
                                        returnCode: Int,
                                        extra: String?
                                    ) {
                                        if (owned != null &&
                                            packageName != owned.download.packageName
                                        ) {
                                            return
                                        }
                                        if (owned == null) removeFromInstallQueue(packageName)
                                        if (owned ==
                                            null
                                        ) {
                                            handleCallback(packageName, returnCode, extra)
                                        } else {
                                            AuroraApp.scope.launch(Dispatchers.IO) {
                                                handleCallback(
                                                    packageName,
                                                    returnCode,
                                                    extra,
                                                    owned
                                                )
                                                runCatching { context.unbindService(connection) }
                                            }
                                        }
                                        readyWithAction.set(true)
                                    }
                                }

                                try {
                                    if (service.isMoreMethodImplemented) {
                                        try {
                                            // Binding/capability checks can wait: validate authority at the actual install call.
                                            if (owned != null &&
                                                receipt != null
                                            ) {
                                                owned.onPrepared(receipt)
                                            }
                                            service.installSplitPackageMore(
                                                packageName,
                                                uriList,
                                                ACTION_INSTALL_REPLACE_EXISTING,
                                                BuildConfig.APPLICATION_ID,
                                                callback,
                                                fileList
                                            )
                                            if (owned != null &&
                                                receipt != null
                                            ) {
                                                owned.onAccepted(receipt)
                                            }
                                        } catch (e: RemoteException) {
                                            // A transport failure may happen after the service accepted the request.
                                            if (owned == null) removeFromInstallQueue(packageName)
                                            reportError(
                                                packageName,
                                                e.localizedMessage,
                                                e.stackTraceToString()
                                            )
                                            readyWithAction.set(true)
                                        }
                                    } else if (owned != null) {
                                        // The capability check proves the newer entry point was not called.
                                        if (receipt != null) owned.onPrepared(receipt)
                                        service.installSplitPackageX(
                                            packageName,
                                            uriList,
                                            ACTION_INSTALL_REPLACE_EXISTING,
                                            BuildConfig.APPLICATION_ID,
                                            callback
                                        )
                                        if (owned != null &&
                                            receipt != null
                                        ) {
                                            owned.onAccepted(receipt)
                                        }
                                    } else {
                                        throw Exception("New method not implemented")
                                    }
                                } catch (th: Throwable) {
                                    if (owned != null) {
                                        // Unknown external acceptance: retain receipt and never invoke another entry point.
                                        readyWithAction.set(true)
                                        return
                                    }
                                    th.printStackTrace()
                                    try {
                                        service.installSplitPackageX(
                                            packageName,
                                            uriList,
                                            ACTION_INSTALL_REPLACE_EXISTING,
                                            BuildConfig.APPLICATION_ID,
                                            callback
                                        )
                                    } catch (e: RemoteException) {
                                        if (owned == null) removeFromInstallQueue(packageName)
                                        reportError(
                                            packageName,
                                            e.localizedMessage,
                                            e.stackTraceToString()
                                        )
                                        readyWithAction.set(true)
                                    }
                                }
                            } else {
                                owned?.onFinished?.invoke(false)
                                if (owned == null) removeFromInstallQueue(packageName)
                                reportError(
                                    packageName,
                                    context.getString(R.string.installer_status_failure),
                                    context.getString(R.string.installer_service_misconfigured)
                                )
                                readyWithAction.set(true)
                            }
                        }
                        if (owned == null) {
                            submit()
                        } else {
                            AuroraApp.scope.launch(Dispatchers.IO) {
                                try {
                                    submit()
                                } catch (exception: Exception) {
                                    Log.e(
                                        TAG,
                                        "Owned service handoff could not be confirmed",
                                        exception
                                    )
                                    readyWithAction.set(true)
                                    runCatching { context.unbindService(connection) }
                                }
                            }
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        if (owned == null) removeFromInstallQueue(packageName)
                        readyWithAction.set(true)
                        Log.e(TAG, "Disconnected from Aurora Services")
                    }
                }

                val intent = Intent(PRIVILEGED_EXTENSION_SERVICE_INTENT)
                intent.setPackage(PRIVILEGED_EXTENSION_PACKAGE_NAME)

                if (owned == null) serviceConnection = connection
                if (owned == null) {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } else {
                    try {
                        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                            AuroraApp.scope.launch(Dispatchers.IO) { owned.onFinished(false) }
                            readyWithAction.set(true)
                        }
                    } catch (exception: Exception) {
                        // This is an asynchronous failure, so it cannot launch another installer.
                        AuroraApp.scope.launch(Dispatchers.IO) { owned.onFinished(false) }
                        readyWithAction.set(true)
                        Log.e(TAG, "Unable to bind owned installation service", exception)
                    }
                }
            }
            while (!readyWithAction.get()) {
                Thread.sleep(1000)
            }
            Log.i(TAG, "Services Callback : install wait done")
        }
    }

    private fun handleCallbackUninstall(packageName: String, returnCode: Int, extra: String?) {
        Log.i(TAG, "Services Callback : $packageName $returnCode $extra")

        try {
            when (returnCode) {
                PackageInstaller.STATUS_SUCCESS -> {
                    AuroraApp.events.send(InstallerEvent.Uninstalled(packageName = packageName))
                }

                else -> postError(packageName, getErrorString(context, returnCode), extra)
            }
            if (::serviceConnection.isInitialized) {
                context.unbindService(serviceConnection)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun handleCallback(
        packageName: String,
        returnCode: Int,
        extra: String?,
        owned: OwnedInstallRequest? = null
    ) {
        Log.i(TAG, "Services Callback : $packageName $returnCode $extra")

        try {
            when (returnCode) {
                PackageInstaller.STATUS_SUCCESS -> {
                    if (owned ==
                        null
                    ) {
                        AuroraApp.events.send(
                            InstallerEvent.Installed(packageName = packageName)
                        )
                    }
                    // Installation is not yet finished if this is a shared library
                    if (owned != null ||
                        packageName == download?.packageName
                    ) {
                        onInstallationSuccess(owned)
                    }
                }

                else -> {
                    owned?.onFinished?.invoke(false)
                    if (owned ==
                        null
                    ) {
                        postError(packageName, getErrorString(context, returnCode), extra)
                    }
                }
            }
            if (owned == null && ::serviceConnection.isInitialized) {
                context.unbindService(serviceConnection)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    override fun postError(packageName: String, error: String?, extra: String?) {
        try {
            super.postError(packageName, error, extra)
            if (::serviceConnection.isInitialized) {
                context.unbindService(serviceConnection)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }
}
