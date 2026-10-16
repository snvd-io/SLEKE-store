package com.sleke.library.downloads.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.WorkManager
import com.sleke.library.downloads.domain.ApkInstallGateway
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.InstallAttemptRecorder
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.enterprise.data.FirebaseSessionSource
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CustomDownloadModule {
    @Provides @Singleton
    fun journal(@ApplicationContext context: Context): FileOperationStore =
        FileOperationStore(File(context.noBackupFilesDir, "sleke-download-journal"))

    @Provides @Singleton
    fun artifacts(@ApplicationContext context: Context) =
        ApkArtifactFiles(File(context.filesDir, "sleke-downloads"))

    @Provides fun recorder(store: FileOperationStore): InstallAttemptRecorder = store

    @Provides @Singleton
    fun transfer(
        client: HttpClient,
        files: ApkArtifactFiles,
        @ApplicationContext context: Context
    ): ApkTransferSource = ApkTransfer(
        client,
        files,
        ApkArchiveReader { file ->
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.let {
                ArchiveMetadata(
                    it.packageName,
                    PackageInfoCompat.getLongVersionCode(it),
                    it.applicationInfo?.targetSdkVersion ?: 1
                )
            }
        }
    )

    @Provides @Singleton
    fun scheduler(manager: WorkManager): CustomDownloadScheduler =
        WorkManagerDownloadScheduler(manager)

    @Provides @Singleton
    fun access(
        sessions: EnterpriseSessionRepository,
        source: FirebaseSessionSource
    ): DownloadAccessSource = object : DownloadAccessSource {
        override fun check(owner: DownloadOwner): AccessDecision {
            if (owner == DownloadOwner.Public) return AccessDecision.ALLOWED
            owner as DownloadOwner.Enterprise
            val state = sessions.state.value
            val sdkIdentity = source.currentIdentity
            if (state == EnterpriseSessionState.Restoring &&
                sdkIdentity == null
            ) {
                return AccessDecision.UNAVAILABLE
            }
            if (sdkIdentity?.uid != owner.uid) return AccessDecision.REVOKED
            return when (state) {
                is EnterpriseSessionState.Allowed -> if (owner.uid == state.identity.uid &&
                    owner.enterpriseId == state.enterpriseId
                ) {
                    AccessDecision.ALLOWED
                } else {
                    AccessDecision.REVOKED
                }
                is EnterpriseSessionState.Unavailable -> if (owner.uid ==
                    state.identity.uid
                ) {
                    AccessDecision.UNAVAILABLE
                } else {
                    AccessDecision.REVOKED
                }
                is EnterpriseSessionState.Checking -> if (owner.uid ==
                    state.identity.uid
                ) {
                    AccessDecision.UNAVAILABLE
                } else {
                    AccessDecision.REVOKED
                }
                EnterpriseSessionState.Restoring -> AccessDecision.UNAVAILABLE
                else -> AccessDecision.REVOKED
            }
        }
    }

    @Provides @Singleton
    fun processor(
        store: FileOperationStore,
        transfer: ApkTransferSource,
        gateway: ApkInstallGateway,
        packages: InstalledPackageSource,
        access: DownloadAccessSource
    ) = DownloadProcessor(store, transfer, gateway, packages, access)

    @Provides @Singleton
    fun implementation(
        store: FileOperationStore,
        scheduler: CustomDownloadScheduler,
        sessions: EnterpriseSessionRepository,
        files: ApkArtifactFiles,
        processor: DownloadProcessor
    ) = CustomDownloadCoordinatorImpl(store, scheduler, sessions, files, processor)

    @Provides
    fun coordinator(implementation: CustomDownloadCoordinatorImpl): CustomDownloadCoordinator =
        implementation
}
