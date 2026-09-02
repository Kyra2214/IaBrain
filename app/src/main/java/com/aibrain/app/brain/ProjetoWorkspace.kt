package com.aibrain.app.brain

import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** Fonte de uma contribuição recebida pelo workspace. */
enum class FonteContribuicao { ZIP, GITHUB, CHAT, BRAIN, PROMPT_BUILDER, BROWSER, LOCAL }
enum class StatusContribuicao { RECEBIDA, ANALISADA, INTEGRADA, CONFLITO, REJEITADA }
enum class NivelValidacao { LOCAL, AMBIENTE, REMOTO }
enum class StatusValidacao { OK, PENDENTE, FALHA, NAO_EXECUTADO, NAO_VERIFICADO, DEPENDE_AMBIENTE_EXTERNO }
enum class StatusGithub { DESCONECTADO, CONECTADO, SINCRONIZANDO, ERRO }

data class ArquivoWorkspace(val caminho: String, val hash: String, val tamanho: Long, val origem: String = "")
data class ArquivoMudanca(val caminho: String, val tipo: TipoMudanca, val fontes: List<String> = emptyList())
enum class TipoMudanca { NOVO, MODIFICADO, REMOVIDO, IGUAL, CONFLITO }

data class ContribuicaoWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val projetoId: String,
    val fonte: FonteContribuicao,
    val nomeFonte: String,
    val recebidoEm: Long = System.currentTimeMillis(),
    val arquivos: List<ArquivoWorkspace> = emptyList(),
    val status: StatusContribuicao = StatusContribuicao.RECEBIDA
)

data class ResultadoAnaliseContribuicao(
    val mudancas: List<ArquivoMudanca>,
    val conflitos: List<String>,
    val encontrouDocumentacao: Boolean,
    val encontrouTestes: Boolean,
    val encontrouConfiguracao: Boolean,
    val encontrouDependencias: Boolean
)

object AnalisadorWorkspace {
    fun comparar(base: List<ArquivoWorkspace>, contribuicoes: List<ContribuicaoWorkspace>): ResultadoAnaliseContribuicao {
        val basePorCaminho = base.associateBy { it.caminho }
        val porCaminho = contribuicoes.flatMap { c -> c.arquivos.map { it.caminho to c } }.groupBy({ it.first }, { it.second })
        val mudancas = mutableListOf<ArquivoMudanca>()
        val conflitos = mutableListOf<String>()
        porCaminho.forEach { (caminho, fontes) ->
            val arquivos = fontes.mapNotNull { fonte -> contribuicoes.firstOrNull { it.id == fonte.id }?.arquivos?.firstOrNull { it.caminho == caminho } }
            val hashes = arquivos.map { it.hash }.distinct()
            val origem = fontes.map { it.nomeFonte }.distinct()
            when {
                hashes.size > 1 -> { conflitos += caminho; mudancas += ArquivoMudanca(caminho, TipoMudanca.CONFLITO, origem) }
                basePorCaminho[caminho] == null -> mudancas += ArquivoMudanca(caminho, TipoMudanca.NOVO, origem)
                basePorCaminho[caminho]?.hash == hashes.firstOrNull() -> mudancas += ArquivoMudanca(caminho, TipoMudanca.IGUAL, origem)
                else -> mudancas += ArquivoMudanca(caminho, TipoMudanca.MODIFICADO, origem)
            }
        }
        basePorCaminho.keys.filter { it !in porCaminho }.forEach { mudancas += ArquivoMudanca(it, TipoMudanca.REMOVIDO) }
        val todos = porCaminho.keys
        return ResultadoAnaliseContribuicao(
            mudancas = mudancas.sortedBy { it.caminho }, conflitos = conflitos.distinct().sorted(),
            encontrouDocumentacao = todos.any(::pareceDocumentacao),
            encontrouTestes = todos.any(::pareceTeste),
            encontrouConfiguracao = todos.any(::pareceConfiguracao),
            encontrouDependencias = todos.any(::pareceDependencia)
        )
    }

    private fun pareceDocumentacao(path: String) = path.substringAfterLast('/').lowercase().let { it.endsWith(".md") || it == "readme" || it.startsWith("docs") }
    private fun pareceTeste(path: String) = path.lowercase().let { "/test/" in "/$it" || it.contains("test") || it.contains("spec") }
    private fun pareceConfiguracao(path: String) = path.substringAfterLast('/').lowercase() in setOf("gradle.properties", "settings.gradle", "settings.gradle.kts", "package.json", "pyproject.toml", "pom.xml", "dockerfile")
    private fun pareceDependencia(path: String) = path.substringAfterLast('/').lowercase() in setOf("build.gradle", "build.gradle.kts", "package.json", "requirements.txt", "pom.xml", "cargo.toml")
}

data class ZipImportResult(val arquivos: List<ArquivoWorkspace>, val rejeitados: List<String>)
object ZipWorkspaceImporter {
    fun importar(zip: File, origem: String): ZipImportResult {
        require(zip.isFile) { "ZIP não encontrado" }
        val arquivos = mutableListOf<ArquivoWorkspace>()
        val rejeitados = mutableListOf<String>()
        ZipFile(zip).use { pacote ->
            pacote.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val caminho = entry.name.replace('\\', '/')
                if (caminho.isBlank() || caminho.startsWith("/") || caminho.matches(Regex("^[A-Za-z]:/.*")) || caminho.split('/').any { it == ".." }) { rejeitados += caminho; return@forEach }
                val bytes = pacote.getInputStream(entry).use { it.readBytes() }
                arquivos += ArquivoWorkspace(caminho, sha256(bytes), bytes.size.toLong(), origem)
            }
        }
        return ZipImportResult(arquivos, rejeitados)
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

data class RelatorioValidacao(val itens: List<ItemValidacao>) {
    val aprovadoLocalmente: Boolean get() = itens.filter { it.nivel != NivelValidacao.REMOTO }.all { it.status == StatusValidacao.OK }
    val possuiPendencias: Boolean get() = itens.any { it.status != StatusValidacao.OK }
}
data class ItemValidacao(val nome: String, val nivel: NivelValidacao, val status: StatusValidacao, val detalhes: String)

object ValidadorProjeto {
    fun validar(arquivos: List<ArquivoWorkspace>, comandosDisponiveis: Set<String> = emptySet()): RelatorioValidacao {
        val caminhos = arquivos.map { it.caminho }
        val duplicados = caminhos.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val temDocs = caminhos.any { it.lowercase().endsWith(".md") || it.lowercase().contains("readme") }
        val temTestes = caminhos.any { it.lowercase().contains("test") || it.lowercase().contains("spec") }
        return RelatorioValidacao(listOf(
            ItemValidacao("Estrutura de arquivos", NivelValidacao.LOCAL, if (duplicados.isEmpty()) StatusValidacao.OK else StatusValidacao.FALHA, if (duplicados.isEmpty()) "Sem caminhos duplicados" else "Duplicados: ${duplicados.joinToString()}"),
            ItemValidacao("Documentação", NivelValidacao.LOCAL, if (temDocs) StatusValidacao.OK else StatusValidacao.PENDENTE, if (temDocs) "Documentação encontrada" else "Nenhuma documentação identificada"),
            ItemValidacao("Testes disponíveis", NivelValidacao.LOCAL, if (temTestes) StatusValidacao.OK else StatusValidacao.PENDENTE, if (temTestes) "Testes identificados" else "Nenhum teste identificado"),
            ItemValidacao("Diff check", NivelValidacao.LOCAL, if ("diff-check" in comandosDisponiveis) StatusValidacao.OK else StatusValidacao.NAO_EXECUTADO, "A execução depende do runner local"),
            ItemValidacao("Build e testes de ambiente", NivelValidacao.AMBIENTE, StatusValidacao.NAO_EXECUTADO, "Depende de Gradle, Node, Python ou outro runner disponível"),
            ItemValidacao("CI remoto", NivelValidacao.REMOTO, StatusValidacao.DEPENDE_AMBIENTE_EXTERNO, "NÃO VALIDADO LOCALMENTE; depende de CI externo")
        ))
    }
}

data class CiProfile(
    val stack: String,
    val validacoesObrigatorias: List<String>,
    val testesRecomendados: List<String>,
    val build: String?,
    val lint: String?,
    val analiseEstatica: String?,
    val seguranca: String?,
    val documentacaoObrigatoria: Boolean = true
)
object IaBrainCiStandard {
    val perfis: Map<String, CiProfile> = mapOf(
        "ANDROID_KOTLIN" to CiProfile("Android/Kotlin", listOf("estrutura", "diff-check", "testes"), listOf("unit tests", "compile debug", "instrumentation"), "./gradlew assembleDebug", "./gradlew lint", "./gradlew detekt", "dependency and manifest review"),
        "REACT_TYPESCRIPT" to CiProfile("React/TypeScript", listOf("estrutura", "diff-check", "testes"), listOf("typecheck", "unit tests", "build"), "npm run build", "npm run lint", "npm run typecheck", "dependency audit"),
        "PYTHON" to CiProfile("Python", listOf("estrutura", "diff-check", "testes"), listOf("pytest", "type checking"), "python -m build", "ruff check", "mypy", "pip-audit")
    )
    fun perfilPara(stack: String): CiProfile? = perfis.entries.firstOrNull { it.value.stack.equals(stack, true) || it.key.equals(stack, true) }?.value
}
