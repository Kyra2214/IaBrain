package com.aibrain.app.brain

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Decisão explícita do usuário para cada arquivo durante uma integração. */
enum class DecisaoIntegracao { ACEITAR_CONTRIBUICAO, MANTER_ATUAL, REMOVER }

data class DecisaoArquivo(
    val caminho: String,
    val decisao: DecisaoIntegracao
)

data class ResultadoIntegracao(
    val sucesso: Boolean,
    val arquivosAplicados: List<String>,
    val arquivosRemovidos: List<String>,
    val erros: List<String>
)

/** Armazena as fontes recebidas e o workspace efetivo em armazenamento privado do app. */
class WorkspaceFileStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "projetos_workspace")

    fun persistirContribuicao(zip: File, projetoId: String, contribuicaoId: String): ZipImportResult {
        require(zip.isFile) { "ZIP não encontrado" }
        val dir = File(root, "$projetoId/contribuicoes/$contribuicaoId")
        dir.deleteRecursively()
        dir.mkdirs()
        val storedZip = File(dir, "fonte.zip")
        zip.inputStream().use { input -> storedZip.outputStream().use { output -> input.copyTo(output) } }
        return extrairSeguro(storedZip, File(dir, "arquivos"), contribuicaoId)
    }

    fun workspaceExiste(projetoId: String): Boolean = File(root, "$projetoId/workspace").isDirectory

    fun inicializarWorkspace(projetoId: String, contribuicaoId: String) {
        val origem = File(root, "$projetoId/contribuicoes/$contribuicaoId/arquivos")
        require(origem.isDirectory) { "Contribuição não encontrada" }
        val destino = File(root, "$projetoId/workspace")
        if (destino.exists()) return
        copiarDiretorioSeguro(origem, destino)
    }

    fun snapshotWorkspace(projetoId: String): List<ArquivoWorkspace> = snapshot(File(root, "$projetoId/workspace"))

    fun snapshotContribuicao(projetoId: String, contribuicaoId: String, origem: String): List<ArquivoWorkspace> =
        snapshot(File(root, "$projetoId/contribuicoes/$contribuicaoId/arquivos"), origem)

    fun aplicar(projetoId: String, contribuicaoId: String, decisoes: List<DecisaoArquivo>): ResultadoIntegracao {
        val atual = File(root, "$projetoId/workspace")
        val candidato = File(root, "$projetoId/contribuicoes/$contribuicaoId/arquivos")
        require(candidato.isDirectory) { "Contribuição não encontrada" }
        val staging = File(root, "$projetoId/.staging-${System.currentTimeMillis()}")
        val backup = File(root, "$projetoId/.backup-${System.currentTimeMillis()}")
        staging.deleteRecursively()
        backup.deleteRecursively()
        try {
            if (atual.exists()) copiarDiretorioSeguro(atual, staging) else staging.mkdirs()
            val aplicados = mutableListOf<String>()
            val removidos = mutableListOf<String>()
            decisoes.forEach { item ->
                validarCaminho(item.caminho)
                val destino = File(staging, item.caminho)
                when (item.decisao) {
                    DecisaoIntegracao.ACEITAR_CONTRIBUICAO -> {
                        val fonte = File(candidato, item.caminho)
                        require(fonte.isFile) { "Arquivo da contribuição não encontrado: ${item.caminho}" }
                        destino.parentFile?.mkdirs()
                        fonte.inputStream().use { input -> destino.outputStream().use { output -> input.copyTo(output) } }
                        aplicados += item.caminho
                    }
                    DecisaoIntegracao.MANTER_ATUAL -> Unit
                    DecisaoIntegracao.REMOVER -> if (destino.exists()) {
                        destino.delete()
                        removidos += item.caminho
                    }
                }
            }
            if (atual.exists() && !atual.renameTo(backup)) throw IllegalStateException("Falha ao criar backup do workspace")
            if (!staging.renameTo(atual)) {
                if (backup.exists()) backup.renameTo(atual)
                throw IllegalStateException("Falha ao promover staging para workspace")
            }
            backup.deleteRecursively()
            return ResultadoIntegracao(true, aplicados, removidos, emptyList())
        } catch (e: Exception) {
            staging.deleteRecursively()
            if (!atual.exists() && backup.exists()) backup.renameTo(atual)
            return ResultadoIntegracao(false, emptyList(), emptyList(), listOf(e.message ?: "Falha desconhecida"))
        }
    }

    private fun extrairSeguro(zip: File, destino: File, origem: String): ZipImportResult {
        destino.mkdirs()
        val arquivos = mutableListOf<ArquivoWorkspace>()
        val rejeitados = mutableListOf<String>()
        ZipFile(zip).use { pacote ->
            pacote.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val caminho = entry.name.replace('\\', '/')
                try { validarCaminho(caminho) } catch (_: IllegalArgumentException) { rejeitados += caminho; return@forEach }
                val arquivo = File(destino, caminho)
                val canonicalDestino = arquivo.canonicalFile
                val canonicalRaiz = destino.canonicalFile
                require(canonicalDestino.path == canonicalRaiz.path || canonicalDestino.path.startsWith(canonicalRaiz.path + File.separator))
                canonicalDestino.parentFile?.mkdirs()
                pacote.getInputStream(entry).use { input -> canonicalDestino.outputStream().use { output -> input.copyTo(output) } }
                val bytes = canonicalDestino.readBytes()
                arquivos += ArquivoWorkspace(caminho, sha256(bytes), bytes.size.toLong(), origem)
            }
        }
        return ZipImportResult(arquivos, rejeitados)
    }

    private fun snapshot(dir: File, origem: String = "WORKSPACE"): List<ArquivoWorkspace> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile }.map {
            val caminho = it.relativeTo(dir).path.replace(File.separatorChar, '/')
            ArquivoWorkspace(caminho, sha256(it.readBytes()), it.length(), origem)
        }.sortedBy { it.caminho }.toList()
    }

    private fun copiarDiretorioSeguro(origem: File, destino: File) {
        destino.mkdirs()
        origem.walkTopDown().filter { it.isFile }.forEach { arquivo ->
            val caminho = arquivo.relativeTo(origem).path.replace(File.separatorChar, '/')
            validarCaminho(caminho)
            val out = File(destino, caminho)
            out.parentFile?.mkdirs()
            arquivo.inputStream().use { input -> out.outputStream().use { output -> input.copyTo(output) } }
        }
    }

    private fun validarCaminho(caminho: String) {
        require(caminho.isNotBlank() && !caminho.startsWith("/") && !caminho.matches(Regex("^[A-Za-z]:/.*")) && caminho.split('/').none { it == ".." }) {
            "Caminho inseguro: $caminho"
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object ProjetoIntegracaoEngine {
    fun analisar(base: List<ArquivoWorkspace>, contribuicao: List<ArquivoWorkspace>): ResultadoAnaliseContribuicao {
        val baseMap = base.associateBy { it.caminho }
        val contribMap = contribuicao.associateBy { it.caminho }
        val caminhos = (baseMap.keys + contribMap.keys).sorted()
        val mudancas = caminhos.map { caminho ->
            val b = baseMap[caminho]
            val c = contribMap[caminho]
            when {
                b == null && c != null -> ArquivoMudanca(caminho, TipoMudanca.NOVO, listOf(c.origem))
                b != null && c == null -> ArquivoMudanca(caminho, TipoMudanca.REMOVIDO)
                b?.hash == c?.hash -> ArquivoMudanca(caminho, TipoMudanca.IGUAL, listOfNotNull(c?.origem))
                else -> ArquivoMudanca(caminho, TipoMudanca.MODIFICADO, listOfNotNull(c?.origem))
            }
        }
        return ResultadoAnaliseContribuicao(
            mudancas = mudancas,
            conflitos = emptyList(),
            encontrouDocumentacao = caminhos.any { pareceDocumentacao(it) },
            encontrouTestes = caminhos.any { pareceTeste(it) },
            encontrouConfiguracao = caminhos.any { pareceConfiguracao(it) },
            encontrouDependencias = caminhos.any { pareceDependencia(it) }
        )
    }

    fun decisoesPadrao(analise: ResultadoAnaliseContribuicao): List<DecisaoArquivo> = analise.mudancas.map {
        DecisaoArquivo(it.caminho, when (it.tipo) {
            TipoMudanca.NOVO, TipoMudanca.MODIFICADO -> DecisaoIntegracao.ACEITAR_CONTRIBUICAO
            TipoMudanca.REMOVIDO, TipoMudanca.IGUAL, TipoMudanca.CONFLITO -> DecisaoIntegracao.MANTER_ATUAL
        })
    }

    private fun pareceDocumentacao(path: String) = path.lowercase().let { it.endsWith(".md") || it.contains("/docs/") || it == "readme.md" }
    private fun pareceTeste(path: String) = path.lowercase().let { it.contains("test") || it.contains("spec") }
    private fun pareceConfiguracao(path: String) = path.substringAfterLast('/').lowercase() in setOf("gradle.properties", "settings.gradle", "settings.gradle.kts", "package.json", "pyproject.toml", "pom.xml", "dockerfile")
    private fun pareceDependencia(path: String) = path.substringAfterLast('/').lowercase() in setOf("build.gradle", "build.gradle.kts", "package.json", "requirements.txt", "pom.xml", "cargo.toml")
}
