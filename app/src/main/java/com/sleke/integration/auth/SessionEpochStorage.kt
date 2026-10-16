package com.sleke.integration.auth

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** A committed revocation survives a fresh host process before saved effects are accepted. */
interface SessionEpochStorage {
    fun load(): Long
    fun advance(nextEpoch: Long)
}

/** Only opaque generation numbers are stored, outside backup and the existing preference files. */
class FileSessionEpochStorage(private val directory: File) : SessionEpochStorage {
    private val counter = File(directory, "mutation-epoch")
    private val pending = File(directory, "mutation-epoch.pending")
    private val pendingTemporary = File(directory, "mutation-epoch.pending.new")

    override fun load(): Long = locked {
        val epoch = when {
            pending.exists() -> readOrFresh(pending)
            pendingTemporary.exists() -> freshEpoch()
            counter.exists() -> readOrFresh(counter)
            else -> freshEpoch()
        }
        // Also verifies that the durable record is writable before this process can accept work.
        writeAtomically(counter, epoch)
        Files.deleteIfExists(pending.toPath())
        Files.deleteIfExists(pendingTemporary.toPath())
        syncDirectory()
        epoch
    }

    override fun advance(nextEpoch: Long) = locked {
        try {
            if (readEpoch(counter) !=
                nextEpoch - 1L
            ) {
                throw IOException("Host epoch changed outside its owner")
            }
            // Recovery must see this marker if rewriting the final counter fails or the process dies.
            writeAtomically(pending, nextEpoch)
        } catch (failure: Exception) {
            // If a marker cannot be written, remove the reusable old generation where possible.
            try {
                Files.deleteIfExists(counter.toPath())
                syncDirectory()
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
        writeAtomically(counter, nextEpoch)
        Files.deleteIfExists(pending.toPath())
        syncDirectory()
    }

    private fun readOrFresh(file: File): Long = try {
        readEpoch(file)
    } catch (failure: IOException) {
        freshEpoch()
    }

    private fun readEpoch(file: File): Long {
        val parts = file.readText(Charsets.UTF_8).trim().split('\n')
        if (parts.size != 2 || parts[0] != "v1") throw IOException("Invalid host epoch record")
        return parts[1].toLongOrNull() ?: throw IOException("Invalid host epoch value")
    }

    private fun writeAtomically(target: File, epoch: Long) {
        val temporary = File(directory, "${target.name}.new")
        FileOutputStream(temporary).use { output ->
            output.write("v1\n$epoch\n".toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        syncDirectory()
    }

    private fun syncDirectory() {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }

    private fun <T> locked(block: () -> T): T {
        Files.createDirectories(directory.toPath())
        return FileChannel.open(
            File(directory, "mutation-epoch.lock").toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        ).use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun freshEpoch(): Long = UUID.randomUUID().mostSignificantBits
}
