package com.aibrain.app.data.local

import android.content.Context
import com.aibrain.app.brain.ArquivoWorkspace
import com.aibrain.app.brain.CiProfile
import com.aibrain.app.brain.ContribuicaoWorkspace
import com.aibrain.app.brain.FonteContribuicao
import com.aibrain.app.brain.ItemValidacao
import com.aibrain.app.brain.NivelValidacao
import com.aibrain.app.brain.RelatorioValidacao
import com.aibrain.app.brain.StatusContribuicao
import com.aibrain.app.brain.StatusGithub
import com.aibrain.app.brain.StatusValidacao
import java.util.UUID

class ProjetoWorkspaceRepository(context: Context) {
    private val database = AppDatabase.getInstance(context.applicationContext)

    fun observarContribuicoes(projetoId: String) = database.projetoWorkspaceDao().contribuicoes(projetoId)
    fun observarIntegracoes(projetoId: String) = database.projetoIntegracaoDao().observar(projetoId)
    fun observarValidacoes(projetoId: String) = database.projetoValidacaoDao().observar(projetoId)
    fun observarHistorico(projetoId: String) = database.projetoHistoricoDao().observar(projetoId)

    suspend fun importarContribuicao(contribuicao: ContribuicaoWorkspace) {
        database.projetoWorkspaceDao().salvarContribuicao(
            ProjetoContribuicaoEntity(contribuicao.id, contribuicao.projetoId, contribuicao.fonte.name, contribuicao.nomeFonte, contribuicao.recebidoEm, contribuicao.status.name)
        )
        database.projetoWorkspaceDao().salvarArquivos(contribuicao.arquivos.map {
            ProjetoArquivoWorkspaceEntity(contribuicao.projetoId, contribuicao.id, it.caminho, it.hash, it.tamanho, it.origem)
        })
        registrarHistorico(contribuicao.projetoId, "CONTRIBUICAO_RECEBIDA", "${contribuicao.fonte.name}: ${contribuicao.nomeFonte}")
    }

    suspend fun salvarIntegracao(projetoId: String, fontes: List<String>, status: String, conflitos: List<String>) {
        val numero = database.projetoIntegracaoDao().maiorNumero(projetoId) + 1
        database.projetoIntegracaoDao().salvar(ProjetoIntegracaoEntity(UUID.randomUUID().toString(), projetoId, numero, fontes, status, conflitos, System.currentTimeMillis(), if (status == "CONCLUIDA") System.currentTimeMillis() else null))
        registrarHistorico(projetoId, "INTEGRACAO", "${fontes.size} fonte(s); ${conflitos.size} conflito(s)")
    }

    suspend fun salvarRelatorio(projetoId: String, relatorio: RelatorioValidacao) {
        database.projetoValidacaoDao().salvarTodos(relatorio.itens.map { it.toEntity(projetoId) })
        registrarHistorico(projetoId, "VALIDACAO", relatorio.itens.joinToString { "${it.nome}: ${it.status.name}" })
    }

    suspend fun registrarHistorico(projetoId: String, tipo: String, detalhes: String) {
        database.projetoHistoricoDao().registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), projetoId, tipo, detalhes, System.currentTimeMillis()))
    }

    suspend fun salvarCiProfile(projetoId: String, profile: CiProfile) {
        database.projetoCiDao().salvar(ProjetoCiProfileEntity(UUID.randomUUID().toString(), projetoId, profile.stack, profile.validacoesObrigatorias, profile.testesRecomendados, profile.build, profile.lint, profile.analiseEstatica, profile.seguranca, System.currentTimeMillis()))
    }

    suspend fun salvarGithub(projetoId: String, status: StatusGithub, repositorio: String? = null, branch: String? = null, erro: String? = null) {
        database.projetoGithubDao().salvar(ProjetoGithubEntity(UUID.randomUUID().toString(), projetoId, status.name, repositorio, branch, if (status == StatusGithub.CONECTADO) System.currentTimeMillis() else null, erro))
        registrarHistorico(projetoId, "GITHUB_${status.name}", repositorio ?: "GitHub opcional")
    }

    suspend fun arquivos(projetoId: String): List<ArquivoWorkspace> = database.projetoWorkspaceDao().arquivos(projetoId).map { ArquivoWorkspace(it.caminho, it.hash, it.tamanho, it.origem) }
}

private fun ItemValidacao.toEntity(projetoId: String) = ProjetoValidacaoEntity(UUID.randomUUID().toString(), projetoId, nome, nivel.name, status.name, detalhes, System.currentTimeMillis())

fun ContribuicaoWorkspace.toWorkspaceSummary(): String = "${fonte.name} · $nomeFonte · ${arquivos.size} arquivo(s) · ${status.name}"
