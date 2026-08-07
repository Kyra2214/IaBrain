package com.aibrain.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Modelo de dados de uma única IA do catálogo.
 * Fase 2.1 — representa apenas UM item (ainda não é um conjunto/lista).
 *
 * Esse modelo espelha exatamente os campos definidos no ia_catalogo.json
 * (Fase 2.2), que será criado no próximo submódulo.
 *
 * Parcelable (Fase 5.1) — permite passar a IA inteira via Intent
 * para a tela de detalhes, sem precisar re-buscar no repositório.
 */
@Parcelize
data class IA(
    val id: String,
    val nome: String,
    val logo: String,           // URL ou nome do asset da logo
    val site: String,           // site oficial (aberto via Custom Tabs)
    val descricao: String,      // máximo 3 linhas
    val categorias: List<String> = emptyList(),
    val idiomas: List<String> = emptyList(),
    val gratuita: Boolean,
    // Fase 15.1 — classificação fina de acesso (🟢/🟡/🔴); se o catálogo (local
    // ou sincronizado, Fase 8) não trouxer o campo, deriva de `gratuita`
    // (mantendo o comportamento anterior: gratuita=true → GRATUITA, senão PAGA).
    val acesso: NivelAcesso = if (gratuita) NivelAcesso.GRATUITA else NivelAcesso.PAGA,
    val notas: Map<String, @JvmSuppressWildcards Int> = emptyMap(), // categoria -> nota (0-10)
    // Fase 19.1 — chave (Categoria.chave) da categoria em que a IA é mais forte/
    // reconhecida, distinta das demais categorias em que ela também atua (`categorias`).
    // Curadoria manual (Fase 19.2); null enquanto não curada — ranking (Fase 19.3)
    // trata null como "sem principal definida", sem alterar o comportamento atual.
    val categoriaPrincipal: String? = null
) : Parcelable
