package com.sleke.library.catalog.presentation

import androidx.lifecycle.ViewModelStore
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogResult
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.EnterpriseIdentity
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogDownloadTest {
    @Test fun downloadUsesPackageAndTerminalObservationFinishes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app =
            CatalogApp(
                CatalogIdentity("sleke_apps", "1"),
                "original.package",
                "https://source",
                title = "Display name"
            )
        var started: CustomDownloadRequest? = null
        var observationReleased = false
        var retried: DownloadHandle? = null
        val handle = DownloadHandle("retained-operation")
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle {
                started =
                    request
                return handle
            }
            override fun observe(handle: DownloadHandle): Flow<DownloadState> = flow {
                try {
                    emit(DownloadState.Installed)
                    awaitCancellation()
                } finally {
                    observationReleased =
                        true
                }
            }
            override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle {
                retried =
                    handle
                return handle
            }
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean) =
                CatalogResult.Success(listOf(app))
        }
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun signOut(): CleanupReport = error("unused")
        }
        val packages = object : InstalledPackageSource {
            override suspend fun versionCode(packageName: String): Long? = null
        }
        val vm = SlekeAppsViewModel(repository, coordinator, packages, sessions)
        val store = ViewModelStore().apply { put("catalog", vm) }
        try {
            runCurrent()
            vm.startDownload(app)
            runCurrent()
            assertEquals("original.package", started?.packageName)
            assertEquals("https://source", started?.downloadUrl)
            assertEquals(handle, vm.state.value.handles["original.package"])
            assertEquals(DownloadState.Installed, vm.state.value.downloads["original.package"])
            assertTrue(observationReleased)
            vm.retryInstall(app)
            runCurrent()
            assertEquals(handle, retried)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun sessionRestorationReloadsCancelledPublicFetch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        val row = CatalogApp(CatalogIdentity("apks", "1"), "a.b", "source")
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean): CatalogResult {
                calls++
                if (calls == 1) awaitCancellation()
                return CatalogResult.Success(listOf(row))
            }
        }
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.Restoring)
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun signOut(): CleanupReport = error("unused")
        }
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle =
                error("unused")
            override fun observe(handle: DownloadHandle): Flow<DownloadState> = emptyFlow()
            override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle =
                error("unused")
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val packages = object : InstalledPackageSource {
            override suspend fun versionCode(packageName: String): Long? = null
        }
        val vm = SlekeAppsViewModel(repository, coordinator, packages, sessions)
        val store = ViewModelStore().apply { put("catalog", vm) }
        try {
            runCurrent()
            sessions.state.value =
                EnterpriseSessionState.NoMembership(EnterpriseIdentity("restored"))
            runCurrent()
            assertEquals(CatalogStatus.Populated, vm.state.value.status)
            assertEquals("a.b", vm.state.value.apps.single().packageName)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun identityChangeClearsPrivateHandlesButTemporaryOutageRetainsThem() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = CatalogApp(CatalogIdentity("enterprise/team/apps", "1"), "private.app", "private")
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(
                    EnterpriseSessionState.Allowed(EnterpriseIdentity("one"), "team", "Team")
                )
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun signOut(): CleanupReport = error("unused")
        }
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun start(request: CustomDownloadRequest) =
                DownloadHandle("private-operation")
            override fun observe(handle: DownloadHandle): Flow<DownloadState> = flow {
                emit(DownloadState.AwaitingAccess)
                awaitCancellation()
            }
            override suspend fun retryInstall(handle: DownloadHandle) = handle
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean) =
                CatalogResult.Success(listOf(app))
        }
        val packages = object : InstalledPackageSource {
            override suspend fun versionCode(packageName: String): Long? = null
        }
        val vm = com.sleke.library.enterprise.presentation.EnterpriseAppsViewModel(
            repository,
            coordinator,
            packages,
            sessions
        )
        val store = ViewModelStore().apply { put("catalog", vm) }
        try {
            runCurrent()
            vm.startDownload(app)
            runCurrent()
            sessions.state.value = EnterpriseSessionState.Unavailable(EnterpriseIdentity("one"))
            runCurrent()
            assertEquals(DownloadHandle("private-operation"), vm.state.value.handles["private.app"])
            sessions.state.value =
                EnterpriseSessionState.Allowed(EnterpriseIdentity("two"), "team", "Team")
            runCurrent()
            assertTrue(vm.state.value.handles.isEmpty())
            assertTrue(vm.state.value.downloads.isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun transferFailureRetriesTransferBeforeInstallation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var transfers = 0
        var installs = 0
        val app = CatalogApp(CatalogIdentity("sleke_apps", "1"), "a.b", "source")
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle {
                transfers++
                return DownloadHandle("attempt-$transfers")
            }
            override fun observe(handle: DownloadHandle): Flow<DownloadState> =
                flowOf(DownloadState.Failed(DownloadFailure.TRANSFER))
            override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle {
                installs++
                return handle
            }
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean) =
                CatalogResult.Success(listOf(app))
        }
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun signOut(): CleanupReport = error("unused")
        }
        val packages = object : InstalledPackageSource {
            override suspend fun versionCode(packageName: String): Long? = null
        }
        val vm = SlekeAppsViewModel(repository, coordinator, packages, sessions)
        val store = ViewModelStore().apply { put("catalog", vm) }
        try {
            runCurrent()
            vm.startDownload(app)
            runCurrent()
            vm.retryInstall(app)
            runCurrent()
            assertEquals(2, transfers)
            assertEquals(0, installs)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun recreationReattachesRetainedOperationWithoutStartingAnother() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = CatalogApp(CatalogIdentity("sleke_apps", "1"), "retained.package", "source")
        val retained = DownloadHandle("retained")
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun findActive(packageName: String, scope: CatalogScope) =
                retained.takeIf {
                    packageName ==
                        "retained.package"
                }
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle =
                error("must reattach")
            override fun observe(handle: DownloadHandle): Flow<DownloadState> = flow {
                emit(DownloadState.PendingConfirmation)
                awaitCancellation()
            }
            override suspend fun retryInstall(handle: DownloadHandle) = handle
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val fixture = createViewModel(app, coordinator)
        try {
            runCurrent()
            assertEquals(retained, fixture.first.state.value.handles["retained.package"])
            assertEquals(
                DownloadState.PendingConfirmation,
                fixture.first.state.value.downloads["retained.package"]
            )
        } finally {
            fixture.second.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun startFailureIsRenderedAndCancellationIsNotReportedAsFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = CatalogApp(CatalogIdentity("sleke_apps", "1"), "a.b", "source")
        var cancelled = false
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle {
                if (cancelled) throw CancellationException("obsolete")
                throw IllegalArgumentException("scheduler unavailable")
            }
            override fun observe(handle: DownloadHandle): Flow<DownloadState> = emptyFlow()
            override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle =
                error("unused")
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val fixture = createViewModel(app, coordinator)
        try {
            runCurrent()
            fixture.first.startDownload(app)
            runCurrent()
            assertEquals(
                DownloadState.Failed(DownloadFailure.TRANSFER),
                fixture.first.state.value.downloads["a.b"]
            )
            cancelled = true
            fixture.first.startDownload(app.copy(packageName = "cancelled.package"))
            runCurrent()
            assertNull(fixture.first.state.value.downloads["cancelled.package"])
        } finally {
            fixture.second.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun enterpriseSignOutAttemptsAllCleanupStages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var attempted = false
        var revokedBeforeCancellation = false
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle =
                error("unused")
            override fun observe(handle: DownloadHandle): Flow<DownloadState> = emptyFlow()
            override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle =
                error("unused")
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() {
                attempted = true
                check(revokedBeforeCancellation)
                error("scheduler unavailable")
            }
        }
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(
                    EnterpriseSessionState.Allowed(EnterpriseIdentity("one"), "team", "Team")
                )
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
                revokedBeforeCancellation =
                    true
            }
            override suspend fun signOut(): CleanupReport {
                check(attempted) { "Unsubmitted work must be invalidated before Firebase cleanup" }
                state.value = EnterpriseSessionState.SignedOut
                return CleanupReport()
            }
        }
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean) =
                CatalogResult.Success(emptyList())
        }
        val packages = object : InstalledPackageSource {
            override suspend fun versionCode(packageName: String): Long? = null
        }
        val vm = SlekeAppsViewModel(repository, coordinator, packages, sessions)
        val store = ViewModelStore().apply { put("catalog", vm) }
        try {
            runCurrent()
            vm.enterpriseSignOut()
            runCurrent()
            assertEquals(EnterpriseSessionState.SignedOut, sessions.state.value)
            assertTrue(revokedBeforeCancellation)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun installerRetryFailureIsRendered() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = CatalogApp(CatalogIdentity("sleke_apps", "1"), "a.b", "source")
        val coordinator = object : CustomDownloadCoordinator {
            override suspend fun findActive(packageName: String, scope: CatalogScope) =
                DownloadHandle("existing")
            override suspend fun start(request: CustomDownloadRequest): DownloadHandle =
                error("unused")
            override fun observe(handle: DownloadHandle): Flow<DownloadState> =
                flowOf(DownloadState.Failed(DownloadFailure.INSTALLATION))
            override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle =
                error("staging failed")
            override suspend fun cancel(handle: DownloadHandle) = Unit
            override suspend fun cancelEnterpriseWork() = Unit
        }
        val fixture = createViewModel(app, coordinator)
        try {
            runCurrent()
            fixture.first.retryInstall(app)
            runCurrent()
            assertEquals(
                DownloadState.Failed(DownloadFailure.STAGING),
                fixture.first.state.value.downloads["a.b"]
            )
        } finally {
            fixture.second.clear()
            Dispatchers.resetMain()
        }
    }
    private fun createViewModel(
        app: CatalogApp,
        coordinator: CustomDownloadCoordinator
    ): Pair<SlekeAppsViewModel, ViewModelStore> {
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean) =
                CatalogResult.Success(listOf(app))
        }
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun signOut(): CleanupReport = error("unused")
        }
        val packages = object : InstalledPackageSource {
            override suspend fun versionCode(packageName: String): Long? = null
        }
        val vm = SlekeAppsViewModel(repository, coordinator, packages, sessions)
        return vm to ViewModelStore().apply { put("catalog", vm) }
    }
}
