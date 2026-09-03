package com.aibrain.app.brain

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Integra entregas de IAs que trabalham por ZIP sem sobrescrever a base.
 * A operação é somente análise/materialização em workspace temporário.
 */
object ZipIntegrationEngine {
    data class Artifact(
        val id: String,
        val functionId: String,
        val aiId: String,
        val zip: File,
        val declaredFiles: Set<String> = emptySet()
    )

    data class Entry(
        val artifactId: String,
        val path: String,
        val sha256: String,
        val size: Long
    )

    enum class ConflictType { PATH_TRAVERSAL, DUPLICATE_PATH, BASE_MODIFIED, CROSS_ARTIFACT_MODIFIED }

    data class Conflict(
        val type: ConflictType,
        val path: String,
        val artifacts: List<String>,
        val message: String
    )

    data class Analysis(
        val entries: List<Entry>,
        val conflicts: List<Conflict>,
        val safe: Boolean
    )

    fun analyze(
        artifacts: List<Artifact>,
        baseSha256ByPath: Map<String, String> = emptyMap()
    ): Analysis {
        val entries = mutableListOf<Entry>()
        val conflicts = mutableListOf<Conflict>()
        val owners = linkedMapOf<String, MutableList<Entry>>()

        artifacts.forEach { artifact ->
            require(artifact.id.isNotBlank()) { "Artifact sem ID" }
            require(artifact.functionId.isNotBlank()) { "Artifact sem função" }
            require(artifact.aiId.isNotBlank()) { "Artifact sem IA" }
            require(artifact.zip.isFile) { "ZIP não encontrado: ${artifact.zip}" }
            ZipFile(artifact.zip).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val normalized = normalize(entry.name)
                    if (normalized == null) {
                        conflicts += Conflict(
                            ConflictType.PATH_TRAVERSAL,
                            entry.name,
                            listOf(artifact.id),
                            "Caminho inseguro no ZIP"
                        )
                        return@forEach
                    }
                    val sha = zip.getInputStream(entry).use { digest(it.readBytes()) }
                    val record = Entry(artifact.id, normalized, sha, entry.size)
                    entries += record
                    owners.getOrPut(normalized) { mutableListOf() }.add(record)
                    baseSha256ByPath[normalized]?.let { baseSha ->
                        if (baseSha != sha) conflicts += Conflict(
                            ConflictType.BASE_MODIFIED,
                            normalized,
                            listOf(artifact.id),
                            "Arquivo também diverge da base conhecida"
                        )
                    }
                }
            }
        }

        owners.forEach { (path, records) ->
            val distinct = records.map { it.sha256 }.distinct()
            if (records.size > 1 && distinct.size > 1) {
                conflicts += Conflict(
                    ConflictType.CROSS_ARTIFACT_MODIFIED,
                    path,
                    records.map { it.artifactId }.distinct(),
                    "Mais de um ZIP altera o mesmo arquivo com conteúdo diferente"
                )
            } else if (records.size > 1) {
                conflicts += Conflict(
                    ConflictType.DUPLICATE_PATH,
                    path,
                    records.map { it.artifactId }.distinct(),
                    "Mais de um ZIP entrega o mesmo arquivo com o mesmo conteúdo"
                )
            }
        }
        return Analysis(entries, conflicts, conflicts.none { it.type == ConflictType.PATH_TRAVERSAL || it.type == ConflictType.CROSS_ARTIFACT_MODIFIED })
    }

    /** Extrai em workspace isolado somente depois da análise passar. */
    fun materialize(artifact: Artifact, workspace: File): List<Entry> {
        require(workspace.exists() || workspace.mkdirs()) { "Não foi possível criar workspace" }
        val analysis = analyze(listOf(artifact))
        check(analysis.conflicts.none { it.type == ConflictType.PATH_TRAVERSAL }) { "ZIP inseguro" }
        val entries = mutableListOf<Entry>()
        ZipFile(artifact.zip).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val normalized = normalize(entry.name) ?: error("ZIP inseguro")
                val target = File(workspace, normalized)
                val canonicalWorkspace = workspace.canonicalFile
                val canonicalTarget = target.canonicalFile
                require(canonicalTarget.path == canonicalWorkspace.path || canonicalTarget.path.startsWith(canonicalWorkspace.path + File.separator)) {
                    "Path traversal detectado"
                }
                canonicalTarget.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> canonicalTarget.outputStream().use { output -> input.copyTo(output) } }
                entries += Entry(artifact.id, normalized, digest(FileInputStream(canonicalTarget).use { it.readBytes() }), entry.size)
            }
        }
        return entries
    }

    private fun normalize(path: String): String? {
        if (path.isBlank() || path.startsWith("/") || path.contains('\\')) return null
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
