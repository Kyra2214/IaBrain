package com.aibrain.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Uma variável substituível dentro do [Prompt.template] (ex: {ESTILO}).
 * [padrao] é o valor de fallback usado pela Fase 17 quando o usuário não
 * responde a pergunta de refinamento daquela variável.
 */
@Parcelize
data class VariavelPrompt(
    val nome: String,
    val padrao: String? = null
) : Parcelable

/**
 * Modelo de dados de um único prompt da Biblioteca (Fase 16).
 * Fase 16.1 — representa apenas UM item (ainda não é um conjunto/lista),
 * mesmo papel que [IA] teve na Fase 2.1 para o catálogo de IAs.
 *
 * O campo [template] é texto fixo travado e bem estruturado — só os valores
 * em [variaveis] podem ser substituídos (Fase 17.11); nada aqui gera prompt
 * do zero. Esse contrato é a base que a Fase 17 vai consumir sem alterar.
 *
 * Parcelable (como [IA] na Fase 5.1) — permite passar o prompt inteiro via
 * Intent para a tela de detalhes (Fase 16.5) sem re-buscar no repositório.
 */
@Parcelize
data class Prompt(
    val id: String,
    val titulo: String,
    val categoria: CategoriaPrompt,
    val subcaso: String,
    val descricaoCurta: String,
    val objetivo: String,
    val nivel: String,
    val melhorPara: List<String> = emptyList(),
    val template: String,
    val variaveis: List<VariavelPrompt> = emptyList(),
    val tags: List<String> = emptyList(),
    val dataCriacao: String
) : Parcelable
