package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.ValidatedApk
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

data class ArchiveMetadata(val packageName: String, val versionCode: Long, val targetSdk: Int)
fun interface ApkArchiveReader {
    fun read(file: File): ArchiveMetadata?
}

class ApkTransfer(
    private val client: HttpClient,
    private val files: ApkArtifactFiles,
    private val archive: ApkArchiveReader
) : ApkTransferSource {
    override suspend fun download(
        request: CustomDownloadRequest,
        handle: DownloadHandle,
        progress: suspend (Int?) -> Unit
    ): ValidatedApk {
        var published = false
        try {
            client.prepareGet(request.downloadUrl).execute { response ->
                if (!response.status.isSuccess()) throw IOException("APK response was unsuccessful")
                val length = response.contentLength()
                var copied = 0L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                FileOutputStream(files.partial(handle)).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        if (read == 0) {
                            yield()
                            continue
                        }
                        output.write(buffer, 0, read)
                        copied += read
                        progress(
                            length?.takeIf {
                                it > 0
                            }?.let { ((copied * 100) / it).coerceIn(0, 100).toInt() }
                        )
                    }
                    output.fd.sync()
                }
                if (length != null && copied != length) throw IOException("Incomplete APK response")
            }
            currentCoroutineContext().ensureActive()
            val metadata =
                archive.read(files.partial(handle)) ?: throw IOException("Invalid APK archive")
            if (metadata.packageName !=
                request.packageName
            ) {
                throw IOException("APK package mismatch")
            }
            currentCoroutineContext().ensureActive()
            files.publish(handle)
            published = true
            return ValidatedApk(
                handle.value,
                metadata.packageName,
                metadata.versionCode,
                metadata.targetSdk
            )
        } finally {
            if (!published) files.purge(handle.value)
        }
    }
    override fun purge(reference: String) = files.purge(reference)
}
