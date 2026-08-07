package com.aibrain.app.view

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.brain.EstagioConstrutorPrompt
import com.aibrain.app.brain.SessaoConstrutorPrompt
import com.aibrain.app.brain.abrirIARecomendadaNoNavegador
import com.aibrain.app.brain.avancarAdaptacaoIADestino
import com.aibrain.app.brain.avancarBuscaTemplateComFallback
import com.aibrain.app.brain.avancarRecomendacaoIA
import com.aibrain.app.brain.avancarSubstituicaoVariaveis
import com.aibrain.app.brain.criarPromptGeradoASalvar
import com.aibrain.app.brain.gerarMensagemRespostaFinal
import com.aibrain.app.brain.identificarIntencao
import com.aibrain.app.brain.proximaVariavelPendente
import com.aibrain.app.brain.registrarResposta
import com.aibrain.app.brain.textoPergunta
import com.aibrain.app.data.PromptDadosLocaisRepository
import com.aibrain.app.databinding.ActivityCriadorPromptsBinding
import com.aibrain.app.model.IA
import com.aibrain.app.model.MensagemChat
import com.aibrain.app.model.Prompt
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.repository.PromptRepository
import kotlinx.coroutines.launch

/**
 * Aba "🤖 Criador de Prompts" (Fase 17.1/17.2) — Assistente Inteligente de
 * Prompts (Prompt Builder), acessível a partir da navegação principal.
 *
 * Fase 17.18 — passa a consumir o pipeline completo do Prompt Builder já
 * implementado em `brain/*` (Fases 17.6-17.17), mantendo uma
 * [SessaoConstrutorPrompt] por conversa: cada mensagem do usuário avança a
 * sessão em sequência fixa (identificação → busca de template com fallback
 * → perguntas de refinamento, uma por vez → substituição de variáveis →
 * adaptação/recomendação de IA → resposta final enxuta), exibindo cada
 * mensagem do assistente no [ChatAdapter] conforme a sessão avança de
 * estágio. Os botões de ação (Fase 17.16/17.17) ficam ligados ao resultado
 * assim que a sessão chega em [EstagioConstrutorPrompt.PROMPT_ENTREGUE].
 */
class CriadorPromptsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCriadorPromptsBinding
    private lateinit var adapterConversa: ChatAdapter
    private lateinit var catalogoRepositorio: CatalogoRepository
    private lateinit var promptRepositorio: PromptRepository
    private lateinit var dadosLocaisRepositorio: PromptDadosLocaisRepository

    private var catalogoIA: List<IA> = emptyList()
    private var bibliotecaPrompts: List<Prompt> = emptyList()
    private var sessao = SessaoConstrutorPrompt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCriadorPromptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        catalogoRepositorio = CatalogoRepository(applicationContext)
        promptRepositorio = PromptRepository(applicationContext)
        dadosLocaisRepositorio = PromptDadosLocaisRepository(applicationContext)

        configurarConversa()
        configurarEntrada()
        configurarAcoesResultado()
        binding.btnVoltarCriadorPrompts.setOnClickListener { finish() }

        carregarDados()
    }

    private fun carregarDados() {
        lifecycleScope.launch {
            catalogoIA = try {
                catalogoRepositorio.carregarCatalogoSincronizado()
            } catch (e: Exception) {
                emptyList()
            }
            bibliotecaPrompts = try {
                promptRepositorio.carregarBiblioteca()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun configurarConversa() {
        adapterConversa = ChatAdapter()
        binding.recyclerConversa.layoutManager = LinearLayoutManager(this)
        binding.recyclerConversa.adapter = adapterConversa
    }

    /** Fase 17.2 — habilita "Gerar prompt" só com mensagem não vazia; envio limpa o campo. */
    private fun configurarEntrada() {
        binding.editMensagemConversa.addTextChangedListener {
            binding.btnGerarPrompt.isEnabled = !it.isNullOrBlank()
        }
        binding.btnGerarPrompt.setOnClickListener { enviarMensagem() }
    }

    private fun configurarAcoesResultado() {
        binding.btnVerSalvarPrompt.setOnClickListener { salvarEAbrirDetalhePrompt() }
        binding.btnAbrirIARecomendada.setOnClickListener {
            abrirIARecomendadaNoNavegador(this, sessao)
        }
    }

    private fun enviarMensagem() {
        val texto = binding.editMensagemConversa.text?.toString()?.trim().orEmpty()
        if (texto.isEmpty()) return

        binding.txtConversaVazia.visibility = View.GONE
        adicionarMensagemUsuario(texto)
        binding.editMensagemConversa.text?.clear()
        binding.btnGerarPrompt.isEnabled = false

        avancarSessao(texto)
    }

    private fun adicionarMensagemUsuario(texto: String) {
        adapterConversa.adicionarMensagem(MensagemChat(texto = texto, deUsuario = true))
        binding.recyclerConversa.scrollToPosition(adapterConversa.itemCount - 1)
    }

    private fun adicionarMensagemAssistente(texto: String) {
        adapterConversa.adicionarMensagem(MensagemChat(texto = texto, deUsuario = false))
        binding.recyclerConversa.scrollToPosition(adapterConversa.itemCount - 1)
    }

    /**
     * Fase 17.18 — avança a [sessao] conforme o estágio atual: cada mensagem
     * do usuário é tratada como resposta à pergunta pendente (se houver) ou
     * como o texto inicial da intenção (primeira mensagem da conversa).
     */
    private fun avancarSessao(texto: String) {
        sessao = if (sessao.estagio == EstagioConstrutorPrompt.PERGUNTANDO) {
            val variavelPendente = proximaVariavelPendente(sessao)
            if (variavelPendente != null) {
                registrarResposta(sessao, variavelPendente.nome, texto)
            } else {
                sessao
            }
        } else {
            identificarIntencao(sessao, texto)
        }

        processarProximaEtapa()
    }

    /**
     * Avança a sessão pelas etapas automáticas do fluxo fixo (Fase 17.6)
     * até chegar numa pergunta pendente (Fase 17.9) ou à entrega final
     * (Fase 17.14), exibindo a mensagem correspondente do assistente.
     */
    private fun processarProximaEtapa() {
        if (sessao.estagio == EstagioConstrutorPrompt.IDENTIFICANDO_INTENCAO) {
            adicionarMensagemAssistente(mensagemCategoriaNaoDetectada())
            return
        }

        if (sessao.estagio == EstagioConstrutorPrompt.BUSCANDO_TEMPLATE) {
            sessao = avancarBuscaTemplateComFallback(sessao, bibliotecaPrompts)
        }

        if (sessao.estagio == EstagioConstrutorPrompt.PERGUNTANDO) {
            val variavel = proximaVariavelPendente(sessao)
            if (variavel != null) {
                adicionarMensagemAssistente(textoPergunta(variavel))
                return
            }
            // Sem variáveis pendentes (todas com padrao/já respondidas) — pula direto.
            sessao = sessao.copy(estagio = EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS)
        }

        if (sessao.estagio == EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS) {
            sessao = avancarSubstituicaoVariaveis(sessao)
            sessao = avancarAdaptacaoIADestino(sessao)
            sessao = avancarRecomendacaoIA(sessao, catalogoIA)
        }

        if (sessao.estagio == EstagioConstrutorPrompt.PROMPT_ENTREGUE) {
            val mensagemFinal = gerarMensagemRespostaFinal(sessao)
            if (mensagemFinal != null) {
                adicionarMensagemAssistente(mensagemFinal.texto)
                binding.containerAcoesResultado.visibility = View.VISIBLE
            }
        }
    }

    private fun mensagemCategoriaNaoDetectada(): String =
        "Não entendi bem o que você precisa. Pode descrever com outras palavras?"

    /** Fase 17.15/17.16 — salva o prompt gerado na Biblioteca e abre a tela de Detalhe. */
    private fun salvarEAbrirDetalhePrompt() {
        val promptGerado = criarPromptGeradoASalvar(sessao) ?: return
        dadosLocaisRepositorio.salvarPromptGerado(promptGerado)
        val intent = intentParaDetalhePromptGerado(this, sessao) ?: return
        startActivity(intent)
    }
}
