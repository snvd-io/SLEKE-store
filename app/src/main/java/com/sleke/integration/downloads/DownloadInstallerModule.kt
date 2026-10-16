package com.sleke.integration.downloads

import com.sleke.library.downloads.data.ApkArtifactFiles
import com.sleke.library.downloads.data.DownloadAccessSource
import com.sleke.library.downloads.data.FileOperationStore
import com.sleke.library.downloads.domain.ApkInstallGateway
import com.sleke.library.downloads.domain.InstalledPackageSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadInstallerModule {
    @Provides @Singleton
    fun installedPackages(source: AndroidInstalledPackageSource): InstalledPackageSource = source

    @Provides @Singleton
    fun gateway(
        driver: AndroidOwnedInstallerDriver,
        journal: FileOperationStore,
        packages: InstalledPackageSource,
        files: ApkArtifactFiles,
        access: DownloadAccessSource
    ): ApkInstallGateway =
        AuroraApkInstallGateway(driver, journal, packages, access, files::resolve)

    @Provides @Singleton
    fun receipts(journal: FileOperationStore) = OwnedInstallReceiptStore(journal)
}
