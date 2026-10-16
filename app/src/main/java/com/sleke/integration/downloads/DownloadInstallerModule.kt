package com.sleke.integration.downloads

import com.sleke.library.downloads.data.OwnedInstallerDriver
import com.sleke.library.downloads.presentation.InstallerUiHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadInstallerModule {
    @Binds
    abstract fun installerDriver(driver: AndroidOwnedInstallerDriver): OwnedInstallerDriver

    @Binds
    abstract fun installerUi(host: AuroraInstallerUiHost): InstallerUiHost
}
