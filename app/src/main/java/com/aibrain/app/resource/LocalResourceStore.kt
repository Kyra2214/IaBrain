package com.aibrain.app.resource

import android.content.Context
import java.io.File
import java.security.MessageDigest

class LocalResourceStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "resources")
    fun fileFor(resource: HeavyResource): File = File(File(root, resource.type), resource.filename)
    fun isValid(resource: HeavyResource): Boolean {
        val file = fileFor(resource)
        return file.isFile && file.length() == resource.sizeBytes && sha256(file) == resource.sha256.lowercase()
    }
    fun remove(resource: HeavyResource) { fileFor(resource).delete() }
    fun pathIfValid(resource: HeavyResource): String? = fileFor(resource).takeIf { isValid(resource) }?.absolutePath
    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            var count: Int
            while (input.read(buffer).also { count = it } >= 0) if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    internal fun directory(resource: HeavyResource): File = File(root, resource.type).apply { mkdirs() }
}
