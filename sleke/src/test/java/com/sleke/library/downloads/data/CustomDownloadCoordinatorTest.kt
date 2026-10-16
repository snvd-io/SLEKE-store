package com.sleke.library.downloads.data

import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.ApkInstallGateway
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallCancellation
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstallReconciliation
import com.sleke.library.downloads.domain.InstallSubmission
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.downloads.domain.ValidatedApk
import com.sleke.library.enterprise.data.FirebaseSessionSource
import com.sleke.library.enterprise.data.MembershipResult
import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.EnterpriseIdentity
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CustomDownloadCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = CustomDownloadRequest("a.b", "https://example.test/a.apk")
    private val retained = DownloadHandle("00000000-0000-0000-0000-000000000001")

    @Test fun concurrentStartsReturnTheOneScheduledHandle() = runTest {
        val scheduler = Scheduler()
        val store = FileOperationStore(temporary.newFolder())
        val coordinator = coordinator(store, scheduler)
        val results = listOf(
            async {
                coordinator.start(request)
            },
            async { coordinator.start(request) }
        ).awaitAll()
        assertEquals(results[0], results[1])
        assertEquals(1, scheduler.enqueues)
        assertEquals(results[0], scheduler.current)
    }

    @Test fun retainedWorkIdIsReturnedInsteadOfUnsubmittedCandidate() = runTest {
        val scheduler = Scheduler().apply { retainDuringEnqueue = retained }
        val store = FileOperationStore(temporary.newFolder())
        store.write(OperationRecord(retained, request, DownloadOwner.Public))
        val result = coordinator(store, scheduler).start(request)
        assertEquals(retained, result)
        assertEquals(1, scheduler.enqueues)
        assertEquals(1, store.records().count { !it.state.isTerminal })
    }

    @Test fun privateOwnerChangeCannotReuseOrObserveOldArtifact() = runTest {
        val sessions =
            Session(
                EnterpriseSessionState.Allowed(
                    EnterpriseIdentity("bob"),
                    "enterprise",
                    "Enterprise"
                )
            )
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(
                retained,
                request.copy(scope = CatalogScope.Enterprise),
                DownloadOwner.Enterprise("alice", "enterprise")
            )
        )
        val scheduler = Scheduler().apply { current = retained }
        val coordinator = coordinator(store, scheduler, sessions)
        assertEquals(DownloadState.Cancelled, coordinator.observe(retained).first())
        val next = coordinator.start(request.copy(scope = CatalogScope.Enterprise))
        assertNotEquals(retained, next)
        assertEquals(DownloadOwner.Enterprise("bob", "enterprise"), store.read(next)!!.owner)
        assertEquals(DownloadState.Cancelled, store.read(retained)!!.state)
    }

    @Test fun invalidRequestNeverSchedulesWork() = runTest {
        val scheduler = Scheduler()
        val coordinator = coordinator(FileOperationStore(temporary.newFolder()), scheduler)
        try {
            coordinator.start(request.copy(packageName = ""))
            fail("Expected invalid request")
        } catch (
            _: IllegalArgumentException
        ) { }
        assertEquals(0, scheduler.enqueues)
    }

    @Test fun manualRetryCannotDuplicateAnAmbiguousHandoff() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        val apk = ValidatedApk(retained.value, request.packageName, 1, 26)
        store.write(
            OperationRecord(
                retained,
                request,
                DownloadOwner.Public,
                DownloadState.NeedsReconciliation,
                apk,
                InstallReceipt("attempt", retained.value)
            )
        )
        val scheduler = Scheduler()
        val result = coordinator(store, scheduler).retryInstall(retained)
        assertEquals(retained, result)
        assertEquals(0, scheduler.enqueues)
    }

    @Test fun signOutPurgesFailedOrCancelledPrivateArtifacts() = runTest {
        for (initialState in listOf(
            DownloadState.Failed(DownloadFailure.INSTALLATION),
            DownloadState.Cancelled
        )) {
            val store = FileOperationStore(temporary.newFolder())
            val files = ApkArtifactFiles(temporary.newFolder())
            files.partial(retained).writeText("validated fixture")
            files.publish(retained)
            val apk = ValidatedApk(retained.value, request.packageName, 1, 26)
            store.write(
                OperationRecord(
                    retained,
                    request.copy(scope = CatalogScope.Enterprise),
                    DownloadOwner.Enterprise("alice", "enterprise"),
                    initialState,
                    apk
                )
            )
            val coordinator =
                CustomDownloadCoordinatorImpl(
                    store,
                    Scheduler(),
                    Session(),
                    files,
                    processor(store, files::purge)
                )
            coordinator.cancelEnterpriseWork()
            assertEquals(DownloadState.Cancelled, store.read(retained)!!.state)
            assertFalse(files.output(retained).exists())
        }
    }

    @Test fun differentSdkIdentityRevokesWorkWhileMembershipIsUnavailable() = runTest {
        val owner = DownloadOwner.Enterprise("alice", "enterprise")
        for (state in listOf(
            EnterpriseSessionState.Checking(EnterpriseIdentity("bob")),
            EnterpriseSessionState.Unavailable(EnterpriseIdentity("bob"))
        )) {
            assertEquals(
                AccessDecision.REVOKED,
                CustomDownloadModule.access(Session(state), source("bob")).check(owner)
            )
        }
    }

    @Test fun restartSchedulesTheOriginalUnscheduledRequest() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(OperationRecord(retained, request, DownloadOwner.Public))
        val scheduler = Scheduler()
        val sessions = Session()
        val coordinator = CustomDownloadCoordinatorImpl(
            store,
            scheduler,
            sessions,
            ApkArtifactFiles(temporary.newFolder()),
            processor(store),
            backgroundScope
        )
        coordinator.initialize()
        runCurrent()
        assertEquals(retained, scheduler.current)
        assertEquals(1, scheduler.enqueues)
        sessions.state.value = EnterpriseSessionState.Unavailable(EnterpriseIdentity("bob"))
        runCurrent()
        assertEquals(1, scheduler.enqueues)
    }

    @Test fun sdkIdentityChangeRevokesHandoffBeforeTheListenerPublishesIt() = runTest {
        val sessions =
            Session(
                EnterpriseSessionState.Allowed(
                    EnterpriseIdentity("alice"),
                    "enterprise",
                    "Enterprise"
                )
            )
        val access = CustomDownloadModule.access(sessions, source("bob"))
        assertEquals(
            AccessDecision.REVOKED,
            access.check(DownloadOwner.Enterprise("alice", "enterprise"))
        )
        assertEquals(AccessDecision.ALLOWED, access.check(DownloadOwner.Public))
    }
    private fun source(uid: String) = object : FirebaseSessionSource {
        override val currentIdentity = EnterpriseIdentity(uid)
        override val identities = flowOf(currentIdentity)
        override suspend fun membership(): MembershipResult =
            error("Access checks do not query membership")
        override suspend fun signOut() = error("Access checks do not sign out")
    }
    private fun coordinator(
        store: FileOperationStore,
        scheduler: Scheduler,
        sessions: Session = Session()
    ) = CustomDownloadCoordinatorImpl(
        store,
        scheduler,
        sessions,
        ApkArtifactFiles(temporary.newFolder()),
        processor(store)
    )
    private fun processor(store: FileOperationStore, purge: (String) -> Unit = {}) =
        DownloadProcessor(
            store,
            object : ApkTransferSource {
                override suspend fun download(
                    request: CustomDownloadRequest,
                    handle: DownloadHandle,
                    progress: suspend (Int?) -> Unit
                ): ValidatedApk = error("No background work in start/observation")
                override fun purge(reference: String) = purge(reference)
            },
            object : ApkInstallGateway {
                override suspend fun submit(
                    handle: DownloadHandle,
                    apk: ValidatedApk
                ): InstallSubmission = error("No installer in start/observation")
                override suspend fun reconcile(handle: DownloadHandle) =
                    InstallReconciliation.Unknown
                override suspend fun cancel(handle: DownloadHandle) =
                    InstallCancellation.Unsupported
            },
            object : InstalledPackageSource {
                override suspend fun versionCode(packageName: String): Long? = null
            },
            object : DownloadAccessSource {
                override fun check(owner: DownloadOwner) = AccessDecision.ALLOWED
            }
        )
    private class Scheduler : CustomDownloadScheduler {
        var current: DownloadHandle? = null
        var retainDuringEnqueue: DownloadHandle? = null
        var enqueues = 0
        override suspend fun active(packageName: String) = current
        override suspend fun enqueue(handle: DownloadHandle, packageName: String): DownloadHandle {
            enqueues++
            current = retainDuringEnqueue ?: handle
            return current!!
        }
        override suspend fun cancel(handle: DownloadHandle) {
            if (current ==
                handle
            ) {
                current = null
            }
        }
    }
    private class Session(
        initial: EnterpriseSessionState = EnterpriseSessionState.SignedOut
    ) : EnterpriseSessionRepository {
        override val state = MutableStateFlow(initial)
        override suspend fun refresh() = state.value
        override suspend fun revokeAccess() {
            state.value = EnterpriseSessionState.SignedOut
        }
        override suspend fun signOut(): CleanupReport {
            state.value =
                EnterpriseSessionState.SignedOut
            return CleanupReport()
        }
    }
}
