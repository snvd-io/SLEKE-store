package com.sleke.library.enterprise.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileSessionEpochStorageTest {
    @Test fun atomicCounterSurvivesFreshStoreAndGateInstances() = runTest {
        val directory = Files.createTempDirectory("session-epoch").toFile()
        try {
            val first = SessionMutationGate(FileSessionEpochStorage(directory))
            val previous = first.capture()
            val current = first.invalidate()
            val restarted = SessionMutationGate(FileSessionEpochStorage(directory))
            assertEquals(current, restarted.capture())
            assertFalse(restarted.commitIfCurrent(previous) { error("obsolete write") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun pendingRevocationRecoversBeforeOldSavedEpochCanBeUsed() = runTest {
        val directory = Files.createTempDirectory("pending-session-epoch").toFile()
        try {
            File(directory, "mutation-epoch").writeText("v1\n41\n")
            File(directory, "mutation-epoch.pending").writeText("v1\n42\n")
            val restarted = SessionMutationGate(FileSessionEpochStorage(directory))
            assertEquals(42L, restarted.capture())
            assertFalse(restarted.commitIfCurrent(41L) { error("obsolete saved provider") })
            assertFalse(File(directory, "mutation-epoch.pending").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
