package com.aibrain.app.brain

import com.aibrain.app.model.CategoriaPrompt
import java.text.Normalizer

/**
 * Fase 17.7 — Detecção de tipo de tarefa e categoria a partir do texto do
 * usuário, primeiro passo do fluxo fixo do Prompt Builder (Fase 17.6:
 * [EstagioConstrutorPrompt.IDENTIFICANDO_INTENCAO]).
 *
 * Reaproveita o MECANISMO de detecção ponderada por palavras-chave das
 * Fases 13.2/13.3 ([com.aibrain.app.brain.detectarCategoria]/`detectarCategorias`),
 * mas aplicado a [CategoriaPrompt] em vez de `Categoria` — enums distintos
 * (Fase 16.2 é sobre tarefas, não sobre IAs), por isso a lógica é espelhada
 * aqui em vez de reutilizada diretamente. Mesmo offline, sem API externa.
 */

/** Palavras-chave (além do rótulo/chave da própria categoria) usadas na detecção. */
private val PALAVRAS_CHAVE_POR_CATEGORIA_PROMPT: Map<CategoriaPrompt, List<String>> = mapOf(
    CategoriaPrompt.PROGRAMACAO to listOf("codigo", "programar", "programacao", "dev", "funcao", "bug", "api", "refatorar"),
    CategoriaPrompt.VIDEO to listOf("video", "roteiro", "storyboard", "clipe", "gravar", "editar video"),
    CategoriaPrompt.IMAGEM to listOf("imagem", "foto", "retrato", "ilustracao", "arte visual", "personagem"),
    CategoriaPrompt.DESIGN to listOf("design", "logo", "identidade visual", "paleta", "banner", "cartao de visita"),
    CategoriaPrompt.ESCRITA to listOf("escrita", "escrever", "texto", "artigo", "conto", "ebook", "copywriting"),
    CategoriaPrompt.ESTUDOS to listOf("estudos", "estudar", "aprender", "resumo", "prova", "mapa mental"),
    CategoriaPrompt.MARKETING to listOf("marketing", "campanha", "anuncio", "concorrencia", "editorial"),
    CategoriaPrompt.REDES_SOCIAIS to listOf("redes sociais", "instagram", "reels", "post", "legenda", "bio"),
    CategoriaPrompt.NEGOCIOS to listOf("negocios", "negocio", "empresa", "investidor", "swot", "pitch"),
    CategoriaPrompt.DOCUMENTOS to listOf("documento", "contrato", "curriculo", "ata", "procedimento"),
    CategoriaPrompt.TRADUCAO to listOf("traducao", "traduzir", "idioma", "legenda", "localizacao"),
    CategoriaPrompt.VOZ to listOf("voz", "locucao", "podcast", "audiobook", "narracao", "ivr"),
    CategoriaPrompt.MUSICA to listOf("musica", "cancao", "compor", "instrumental", "jingle", "arranjo"),
    CategoriaPrompt.AUTOMACAO to listOf("automacao", "automatizar", "fluxo", "zapier", "make", "scraping", "planilha"),
    CategoriaPrompt.ENGENHARIA_DE_PROMPT to listOf("prompt", "few-shot", "raciocinio", "extracao", "avaliacao de prompt")
)

private const val PESO_CHAVE_OU_ROTULO = 3
private const val PESO_PALAVRA_CHAVE = 1

private fun String.semAcento(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

private fun pontuarCategoriasPrompt(normalizado: String): List<Pair<CategoriaPrompt, Int>> =
    CategoriaPrompt.entries.map { categoria ->
        val pontosChaveOuRotulo = listOf(categoria.chave.semAcento(), categoria.rotulo.lowercase().semAcento())
            .count { termo -> normalizado.contains(termo) } * PESO_CHAVE_OU_ROTULO
        val pontosPalavraChave = PALAVRAS_CHAVE_POR_CATEGORIA_PROMPT[categoria].orEmpty()
            .count { termo -> normalizado.contains(termo) } * PESO_PALAVRA_CHAVE
        categoria to (pontosChaveOuRotulo + pontosPalavraChave)
    }

/** Categoria de prompt mais provável a partir do texto livre do usuário; null se nada corresponder. */
fun detectarCategoriaPrompt(texto: String): CategoriaPrompt? {
    val normalizado = texto.lowercase().semAcento()
    if (normalizado.isBlank()) return null

    return pontuarCategoriasPrompt(normalizado)
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}

/**
 * Fase 17.6 — avança a sessão do estágio [EstagioConstrutorPrompt.IDENTIFICANDO_INTENCAO]
 * para [EstagioConstrutorPrompt.BUSCANDO_TEMPLATE] quando uma categoria é detectada.
 * Sem categoria detectada, a sessão permanece no mesmo estágio (fallback de
 * "nenhuma categoria" é tratado na Fase 17.10, junto do fallback de template).
 */
fun identificarIntencao(sessao: SessaoConstrutorPrompt, textoUsuario: String): SessaoConstrutorPrompt {
    val categoria = detectarCategoriaPrompt(textoUsuario)
    return sessao.copy(
        textoUsuario = textoUsuario,
        categoriaDetectada = categoria,
        estagio = if (categoria != null) {
            EstagioConstrutorPrompt.BUSCANDO_TEMPLATE
        } else {
            EstagioConstrutorPrompt.IDENTIFICANDO_INTENCAO
        }
    )
}
