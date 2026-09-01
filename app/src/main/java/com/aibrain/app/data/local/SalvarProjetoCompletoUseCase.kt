package com.aibrain.app.data.local

import androidx.room.withTransaction
import com.aibrain.app.brain.ProjetoRecommendation
import java.util.UUID

/** Salva Projeto → Funções → IAs recomendadas em uma única transação Room. */
class SalvarProjetoCompletoUseCase(private val database: AppDatabase) {
    suspend operator fun invoke(recommendation: ProjetoRecommendation, nome: String = recommendation.intent.tipoProjeto ?: "Novo projeto"): String {
        val agora = System.currentTimeMillis()
        val projetoId = UUID.randomUUID().toString()
        val funcoes = recommendation.funcoes.mapIndexed { index, funcao ->
            ProjetoFuncaoEntity("$projetoId-funcao-$index", projetoId, funcao.nome, funcao.categoria.chave, index, "ATIVA")
        }
        val vinculos = recommendation.recomendacoes.mapIndexedNotNull { index, rec ->
            rec.ia?.let { ia -> ProjetoIAEntity("$projetoId-ia-$index", projetoId, funcoes[index].id, ia.id, index, rec.motivo, false) }
        }
        database.withTransaction {
            database.projetoDao().salvar(ProjetoEntity(projetoId, nome, recommendation.intent.textoOriginal, recommendation.intent.plataforma, recommendation.intent.complexidade.name, recommendation.intent.acessoPreferido?.chave, agora, agora, "ATIVO"))
            database.projetoFuncaoDao().salvarTodos(funcoes)
            database.projetoIADao().salvarTodos(vinculos)
            database.projetoContextoDao().salvar(ProjetoContextoEntity("$projetoId-contexto", projetoId, recommendation.intent.textoOriginal, recommendation.stack.itens.map { it.nome }, "", recommendation.intent.restricoes.joinToString(), "ANALISADO", emptyList(), agora))
        }
        return projetoId
    }
}
