package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.ValidatedApk
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class ApkArtifactFiles(private val root: File) {
    private fun directory(reference: String): File {
        require(UUID.fromString(reference).toString() == reference) { "Invalid artifact reference" }
        return File(root, reference)
    }
    fun partial(handle: DownloadHandle): File =
        File(directory(handle.value).apply { mkdirs() }, "base.part")
    fun output(handle: DownloadHandle): File = File(directory(handle.value), "base.apk")
    fun resolve(reference: String): File = File(directory(reference), "base.apk").also {
        require(it.isFile) { "Artifact unavailable" }
    }
    fun publish(handle: DownloadHandle) {
        Files.move(
            partial(handle).toPath(),
            output(handle).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
    fun purge(reference: String) {
        directory(reference).deleteRecursively()
    }
    fun copyForRetry(handle: DownloadHandle, artifact: ValidatedApk): ValidatedApk {
        resolve(artifact.reference).inputStream().use { input ->
            FileOutputStream(partial(handle)).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        publish(handle)
        return artifact.copy(reference = handle.value)
    }
}
