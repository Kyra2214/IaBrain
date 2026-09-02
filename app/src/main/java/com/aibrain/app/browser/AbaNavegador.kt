package com.aibrain.app.browser

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Modelo de dados de uma única aba do navegador interno.
 * Fase 21.2 — representa apenas UM item isolado (ainda não é o conjunto de
 * abas gerenciado pelo [BrowserTabManager], que entra na Fase 21.5).
 *
 * O ciclo de vida do WebView associado a cada aba (Fase 21.3/21.12) não é
 * representado aqui — este modelo guarda só o estado que precisa sobreviver
 * à troca de aba e à persistência entre sessões (Fase 21.7/21.10).
 *
 * Parcelable, mesmo padrão de [com.aibrain.app.model.IA] (Fase 5.1), para uso
 * futuro em savedInstanceState/Intent quando a `BrowserActivity` precisar.
 */
@Parcelize
data class AbaNavegador(
    val id: String = UUID.randomUUID().toString(),
    val nomeIA: String,
    val urlAtual: String,
    val iconeIA: String,             // mesma fonte de IA.logo (URL ou asset)
    val urlInicial: String = urlAtual, // Fase 21.9 — URL oficial da IA, p/ botão "página inicial" (urlAtual muda com a navegação)
    val tituloPagina: String? = null,
    val carregando: Boolean = false,
    val historico: List<String> = emptyList(),
    val podeVoltar: Boolean = false, // estado de navegação (Fase 21.3/21.4)
    val podeAvancar: Boolean = false,
    val posicaoScroll: Int = 0,
    val ultimaAtualizacao: Long = System.currentTimeMillis(),
    val fixada: Boolean = false      // pin (Fase 21.9/21.13)
) : Parcelable
