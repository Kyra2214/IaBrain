package com.aibrain.app.resource

import android.content.Context
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResourceProgress(val resource: HeavyResource, val receivedBytes: Long, val totalBytes: Long)

class HeavyResourceManager(context: Context) {
    private val store = LocalResourceStore(context)
    suspend fun ensure(resource: HeavyResource, onProgress: (ResourceProgress) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (store.isValid(resource)) return@runCatching store.fileFor(resource).absolutePath
            val target = store.fileFor(resource); store.directory(resource)
            var offset = if (target.exists()) target.length() else 0L
            val connection = (URL(resource.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                if (offset > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) { target.delete(); offset = 0 }
                val total = if (connection.contentLengthLong > 0) offset + connection.contentLengthLong else resource.sizeBytes
                connection.inputStream.use { input -> FileOutputStream(target, offset > 0).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER); var received = offset; var count: Int
                    while (input.read(buffer).also { count = it } >= 0) { if (count == 0) continue; output.write(buffer, 0, count); received += count; onProgress(ResourceProgress(resource, received, total)) }
                }}
                if (!store.isValid(resource)) { store.remove(resource); error("Recurso baixado não passou na validação de tamanho/SHA-256") }
                target.absolutePath
            } finally { connection.disconnect() }
        }
    }
    companion object { private const val DEFAULT_BUFFER = 64 * 1024 }
}
