package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadHandle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private val handle = DownloadHandle("00000000-0000-0000-0000-000000000001")
    private val request = CustomDownloadRequest("a.b", "https://example.test/a.apk")

    @Test fun unsuccessfulHttpCannotPublishAnArtifact() = runTest {
        val files = ApkArtifactFiles(temporary.newFolder())
        val client = HttpClient(MockEngine { respond("not an apk", HttpStatusCode.NotFound) })
        try {
            val transfer =
                ApkTransfer(client, files, ApkArchiveReader { ArchiveMetadata("a.b", 10, 26) })
            assertFails { transfer.download(request, handle) {} }
            assertFalse(files.output(handle).exists())
            assertFalse(files.partial(handle).exists())
        } finally {
            client.close()
        }
    }

    @Test fun wrongPackageAndInvalidArchiveAreRemoved() = runTest {
        for (metadata in listOf(ArchiveMetadata("x.y", 10, 26), null)) {
            val files = ApkArtifactFiles(temporary.newFolder())
            val client =
                HttpClient(
                    MockEngine {
                        respond("apk bytes", HttpStatusCode.OK, headersOf("Content-Length", "9"))
                    }
                )
            try {
                val transfer = ApkTransfer(client, files, ApkArchiveReader { metadata })
                assertFails { transfer.download(request, handle) {} }
                assertFalse(files.output(handle).exists())
                assertFalse(files.partial(handle).exists())
            } finally {
                client.close()
            }
        }
    }

    @Test fun cancellationRemainsCancellationAndCleansPartial() = runTest {
        val files = ApkArtifactFiles(temporary.newFolder())
        val client = HttpClient(MockEngine { respond("apk bytes", HttpStatusCode.OK) })
        try {
            val transfer =
                ApkTransfer(
                    client,
                    files,
                    ApkArchiveReader {
                        throw CancellationException("cancelled")
                    }
                )
            try {
                transfer.download(request, handle) {}
                fail("Expected cancellation")
            } catch (
                _: CancellationException
            ) { }
            assertFalse(files.output(handle).exists())
            assertFalse(files.partial(handle).exists())
        } finally {
            client.close()
        }
    }

    @Test fun validatedArtifactKeepsRequestedIdentityAndExactBytes() = runTest {
        val files = ApkArtifactFiles(temporary.newFolder())
        val client =
            HttpClient(
                MockEngine {
                    respond("apk bytes", HttpStatusCode.OK, headersOf("Content-Length", "9"))
                }
            )
        try {
            val transfer =
                ApkTransfer(client, files, ApkArchiveReader { ArchiveMetadata("a.b", 0, 26) })
            val apk = transfer.download(request, handle) {}
            assertEquals("a.b", apk.packageName)
            assertEquals(0, apk.versionCode)
            assertEquals("apk bytes", files.resolve(apk.reference).readText())
        } finally {
            client.close()
        }
    }

    @Test fun referencesCannotEscapeOwnedDirectories() {
        val files = ApkArtifactFiles(temporary.newFolder())
        try {
            files.resolve("../foreign")
            fail("Expected invalid reference")
        } catch (
            _: IllegalArgumentException
        ) { }
    }
    private suspend fun assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected transfer validation failure")
        } catch (
            failure: java.io.IOException
        ) {
            assertNotNull(failure.message)
        }
    }
}
