package com.aibrain.app.brain

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.util.notaPara
import java.text.Normalizer

/**
 * Fase 9 — AI Brain (IA Auxiliar).
 * Opera sobre o CONJUNTO inteiro de IAs para gerar recomendações — a fase
 * mais dependente de estrutura do roadmap (depende do catálogo + categorias
 * + ranking já prontos das Fases 2/3/4).
 *
 * Não depende de nenhuma API externa de IA: usa correspondência de
 * palavras-chave sobre as categorias já existentes (Fase 3), mantendo o
 * app leve e funcionando offline.
 */

/**
 * Fase 9.2 — retorno estruturado de uma recomendação do AI Brain.
 *
 * Fase 13.3 — [categoriaDetectada] continua sendo a categoria principal (mantém
 * compatibilidade com quem já lê esse campo); [categoriasDetectadas] traz até as
 * 2 categorias mais fortes da consulta, já usadas juntas para montar o ranking.
 * Fase 13.4 — [sugestaoTermos] só é preenchido quando nenhuma categoria é
 * detectada, com exemplos de palavras-chave para ajudar o usuário a reformular.
 */
data class RecomendacaoIA(
    val categoriaDetectada: Categoria?,
    val categoriasDetectadas: List<Categoria> = listOfNotNull(categoriaDetectada),
    val melhorOpcao: IA?,
    val segundaOpcao: IA?,
    val alternativasGratuitas: List<IA>,
    val sugestaoTermos: List<String> = emptyList()
)

/** Palavras-chave (além do rótulo/chave da própria categoria) usadas na detecção (Fase 9.1). */
private val PALAVRAS_CHAVE_POR_CATEGORIA: Map<Categoria, List<String>> = mapOf(
    Categoria.CONVERSA to listOf("conversa", "chat", "assistente", "perguntar", "duvida", "bate-papo"),
    Categoria.CODIGO to listOf("codigo", "programar", "programacao", "dev", "codar", "bug", "software"),
    Categoria.VIDEO to listOf("video", "filme", "editar video", "clipe", "gravar"),
    Categoria.IMAGEM to listOf("imagem", "foto", "desenho", "ilustracao", "arte visual"),
    Categoria.DESIGN to listOf("design", "logo", "identidade visual", "layout", "banner"),
    Categoria.MUSICA to listOf("musica", "cancao", "compor", "instrumental", "beat"),
    Categoria.VOZ to listOf("voz", "audio", "narracao", "locucao", "podcast"),
    Categoria.ESCRITA to listOf("escrita", "escrever", "texto", "redacao", "copywriting"),
    Categoria.ESTUDOS to listOf("estudos", "estudar", "aprender", "faculdade", "prova", "resumo"),
    Categoria.TRADUCAO to listOf("traducao", "traduzir", "idioma", "ingles", "espanhol"),
    Categoria.PRODUTIVIDADE to listOf("produtividade", "organizar", "planilha", "tarefas", "agenda"),
    Categoria.AGENTES_IA to listOf("agente", "automatizar tarefa", "assistente autonomo"),
    Categoria.NEGOCIOS to listOf("negocios", "negocio", "empresa", "vendas", "marketing", "startup"),
    Categoria.PESQUISA to listOf("pesquisa", "pesquisar", "buscar", "fontes", "referencias"),
    Categoria.AUTOMACAO to listOf("automacao", "automatizar", "fluxo", "integracao", "workflow")
)

private fun String.semAcento(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

/**
 * Fase 13.2 — Peso de cada tipo de correspondência na detecção de categoria.
 * Um match exato na chave/rótulo da própria categoria (ex: "vídeo") é um
 * sinal muito mais forte do que uma palavra-chave auxiliar (ex: "clipe"),
 * então pesa mais — reduz empates em textos curtos como "editar áudio e vídeo".
 */
private const val PESO_CHAVE_OU_ROTULO = 3
private const val PESO_PALAVRA_CHAVE = 1

/**
 * Fase 9.1 — Detecta a categoria mais provável a partir de um texto livre
 * (ex: "quero criar vídeo"), comparando com o rótulo/chave de cada categoria
 * e suas palavras-chave associadas. Retorna null se nada corresponder.
 *
 * Fase 13.2 — a pontuação agora é ponderada: match na chave/rótulo da própria
 * categoria vale mais do que match em palavra-chave auxiliar.
 */
fun detectarCategoria(texto: String): Categoria? {
    val normalizado = texto.lowercase().semAcento()
    if (normalizado.isBlank()) return null

    return pontuarCategorias(normalizado)
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}

/**
 * Fase 13.3 — Detecta até as 2 categorias mais fortes do texto (em vez de só 1),
 * para consultas que misturam duas necessidades (ex: "vídeo com voz").
 * Mesmo mecanismo de pontuação ponderada da Fase 13.2; lista vazia se nada corresponder.
 */
fun detectarCategorias(texto: String, maximo: Int = 2): List<Categoria> {
    val normalizado = texto.lowercase().semAcento()
    if (normalizado.isBlank()) return emptyList()

    return pontuarCategorias(normalizado)
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(maximo)
        .map { it.first }
}

/** Pontuação ponderada de cada categoria contra o texto já normalizado (Fase 13.2/13.3). */
private fun pontuarCategorias(normalizado: String): List<Pair<Categoria, Int>> =
    Categoria.entries.map { categoria ->
        val pontosChaveOuRotulo = listOf(categoria.chave.semAcento(), categoria.rotulo.lowercase().semAcento())
            .count { termo -> normalizado.contains(termo) } * PESO_CHAVE_OU_ROTULO
        val pontosPalavraChave = PALAVRAS_CHAVE_POR_CATEGORIA[categoria].orEmpty()
            .count { termo -> normalizado.contains(termo) } * PESO_PALAVRA_CHAVE
        categoria to (pontosChaveOuRotulo + pontosPalavraChave)
    }

/**
 * Fase 9.1 + 9.2 — Gera a recomendação estruturada do AI Brain a partir do
 * texto do usuário, usando o ranking por categoria já existente (Fase 4.2).
 *
 * Fase 13.3 — quando a consulta aponta para 2 categorias (ex: "vídeo com voz"),
 * o ranking mescla as duas: cada IA entra com a maior nota entre as categorias
 * detectadas em que ela atua, e o resultado é ordenado por essa nota.
 * Fase 13.4 — quando nenhuma categoria é detectada, [RecomendacaoIA.sugestaoTermos]
 * traz uma amostra de palavras-chave reconhecidas, para orientar o usuário.
 */
fun List<IA>.recomendar(textoUsuario: String): RecomendacaoIA {
    val categorias = detectarCategorias(textoUsuario)
    if (categorias.isEmpty()) {
        return RecomendacaoIA(
            categoriaDetectada = null,
            categoriasDetectadas = emptyList(),
            melhorOpcao = null,
            segundaOpcao = null,
            alternativasGratuitas = emptyList(),
            sugestaoTermos = sugestaoDeTermos()
        )
    }

    val ranking = mapNotNull { ia ->
        val melhorNota = categorias.mapNotNull { ia.notaPara(it) }.maxOrNull()
        melhorNota?.let { ia to it }
    }.sortedByDescending { it.second }.map { it.first }

    val gratuitas = ranking.filter { it.gratuita }

    return RecomendacaoIA(
        categoriaDetectada = categorias.first(),
        categoriasDetectadas = categorias,
        melhorOpcao = ranking.getOrNull(0),
        segundaOpcao = ranking.getOrNull(1),
        alternativasGratuitas = gratuitas.take(3)
    )
}

/** Fase 13.4 — amostra de palavras-chave reconhecidas, para sugerir ao usuário quando nada é detectado. */
private fun sugestaoDeTermos(quantidade: Int = 6): List<String> =
    PALAVRAS_CHAVE_POR_CATEGORIA.values.mapNotNull { it.firstOrNull() }.take(quantidade)

/**
 * Fase 9.3 — Sugestão de categoria para uma nova IA a partir do nome/descrição
 * digitados na curadoria do catálogo (mesmo mecanismo da Fase 9.1).
 */
fun sugerirCategoria(nomeOuDescricao: String): Categoria? = detectarCategoria(nomeOuDescricao)

/**
 * Fase 9.3 — Gera uma descrição curta (até [maxCaracteres]) a partir de um texto
 * maior, respeitando o limite de "máximo 3 linhas" já documentado no modelo IA
 * (Fase 2.1). Corta em um espaço para não quebrar palavras no meio.
 */
fun gerarDescricaoCurta(textoCompleto: String, maxCaracteres: Int = 140): String {
    val texto = textoCompleto.trim()
    if (texto.length <= maxCaracteres) return texto

    val cortada = texto.substring(0, maxCaracteres)
    val ultimoEspaco = cortada.lastIndexOf(' ')
    val base = if (ultimoEspaco > 0) cortada.substring(0, ultimoEspaco) else cortada
    return "$base…"
}
