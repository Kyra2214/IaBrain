package com.aibrain.app.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.brain.EstagioConstrutorPrompt
import com.aibrain.app.brain.IAUrlResolver
import com.aibrain.app.brain.BrowserOpenMode
import com.aibrain.app.brain.IAOpenContract
import com.aibrain.app.brain.PrefillCapability
import com.aibrain.app.brain.PromptBuilderDraft
import com.aibrain.app.brain.SessaoConstrutorPrompt
import com.aibrain.app.data.AssistenteIARepository
import com.aibrain.app.groq.GroqClient
import com.aibrain.app.groq.PromptGeneratorGroq
import com.aibrain.app.groq.ResultadoComFallback
import com.aibrain.app.groq.enviarComFallback
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
import com.aibrain.app.R
import com.aibrain.app.data.PromptDadosLocaisRepository
import com.aibrain.app.databinding.ActivityCriadorPromptsBinding
import com.aibrain.app.model.IA
import com.aibrain.app.model.MensagemChat
import com.aibrain.app.model.Prompt
import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.browser.BrowserActivity
import com.google.android.material.snackbar.Snackbar
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.repository.PromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Aba "🤖 Criador de Prompts" (Fase 17.1/17.2) — Assistente Inteligente de
 * Prompts (Prompt Builder), acessível a partir da navegação principal.
 *
 * Fase 17.18 — passa a consumir o pipeline completo do Prompt Builder já
 * implementado no pacote `brain` (Fases 17.6-17.17), mantendo uma
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
    private lateinit var assistenteIARepositorio: AssistenteIARepository
    /** Fase 25 — evita enviar duas vezes enquanto a Groq ainda está respondendo. */
    private var gerandoComIA = false
    private var draft = PromptBuilderDraft()
    private var sincronizandoEditor = false

    /** Fase 25 — o modo "Gerar com IA" fica ligado/desligado pelo chip do cabeçalho. */
    private val modoIAAtivo: Boolean
        get() = binding.chipModoIA.isChecked

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCriadorPromptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        catalogoRepositorio = CatalogoRepository(applicationContext)
        promptRepositorio = PromptRepository(applicationContext)
        dadosLocaisRepositorio = PromptDadosLocaisRepository(applicationContext)
        // Fase 25 — a API key da Groq é a mesma configurada no Assistente
        // de IA (Fase 18.1): um único cadastro serve às duas funções.
        assistenteIARepositorio = AssistenteIARepository(applicationContext)
        binding.chipModoIA.contentDescription = getString(R.string.criador_prompts_modo_ia_desc)

        configurarConversa()
        configurarEntrada()
        configurarEditorProfissional()
        configurarAcoesResultado()
        binding.btnVoltarCriadorPrompts.setOnClickListener { finish() }
        intent.getStringExtra(EXTRA_COMANDO)?.let { binding.editMensagemConversa.setText(it); binding.editMensagemConversa.setSelection(it.length) }

        carregarDados()
    }

    private fun configurarEditorProfissional() {
        binding.spinnerCategoriaPrompt.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            CategoriaPrompt.entries.map { "${it.emoji} ${it.rotulo}" }
        )
        binding.editPromptTitulo.addTextChangedListener { atualizarDraft() }
        binding.editPromptObjetivo.addTextChangedListener { atualizarDraft() }
        binding.editPromptContexto.addTextChangedListener { atualizarDraft() }
        binding.editPromptTarefa.addTextChangedListener { atualizarDraft() }
        binding.editPromptRestricoes.addTextChangedListener { atualizarDraft() }
        binding.editPromptFormato.addTextChangedListener { atualizarDraft() }
        binding.editPromptLivre.addTextChangedListener { atualizarDraft() }
        binding.editPromptVariaveis.addTextChangedListener { atualizarDraft() }
        binding.editPromptComando.addTextChangedListener { atualizarDraft() }
        binding.spinnerCategoriaPrompt.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                draft = draft.copy(categoria = CategoriaPrompt.entries.getOrNull(position) ?: CategoriaPrompt.ENGENHARIA_DE_PROMPT)
            }
        }
        binding.spinnerIaDestino.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val ia = catalogoIA.getOrNull(position - 1)
                draft = draft.copy(iaDestinoId = ia?.id, iaDestinoNome = ia?.nome)
            }
        }
        binding.btnDetectarVariaveis.setOnClickListener { draft = draft.detectarVariaveis(); renderEditor() }
        binding.btnSalvarPromptEditor.setOnClickListener { salvarPromptEditor() }
        binding.btnCopiarPromptEditor.setOnClickListener { copiarPreview() }
        binding.btnAbrirIaPromptEditor.setOnClickListener { abrirIaDoEditor() }

        val prompt = obterPromptDoIntent()
        draft = if (prompt != null) {
            PromptBuilderDraft.fromPrompt(prompt, intent.getBooleanExtra(EXTRA_DUPLICAR, false))
        } else {
            PromptBuilderDraft(
                textoLivre = intent.getStringExtra(EXTRA_TEXTO_INICIAL).orEmpty(),
                objetivo = intent.getStringExtra(EXTRA_OBJETIVO).orEmpty(),
                iaDestinoId = intent.getStringExtra(EXTRA_IA_ID),
                iaDestinoNome = intent.getStringExtra(EXTRA_IA_NOME),
                comandoRelacionado = intent.getStringExtra(EXTRA_COMANDO)
            ).detectarVariaveis()
        }
        renderEditor()
    }

    @Suppress("DEPRECATION")
    private fun obterPromptDoIntent(): Prompt? = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_PROMPT, Prompt::class.java)
    } else intent.getParcelableExtra(EXTRA_PROMPT)

    private fun atualizarDraft() {
        if (sincronizandoEditor) return
        draft = draft.copy(
            titulo = binding.editPromptTitulo.text?.toString().orEmpty(),
            objetivo = binding.editPromptObjetivo.text?.toString().orEmpty(),
            contexto = binding.editPromptContexto.text?.toString().orEmpty(),
            tarefa = binding.editPromptTarefa.text?.toString().orEmpty(),
            restricoes = binding.editPromptRestricoes.text?.toString().orEmpty(),
            formatoSaida = binding.editPromptFormato.text?.toString().orEmpty(),
            textoLivre = binding.editPromptLivre.text?.toString().orEmpty(),
            comandoRelacionado = binding.editPromptComando.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ).detectarVariaveis()
        renderEditor()
    }

    private fun renderEditor() {
        sincronizandoEditor = true
        binding.editPromptTitulo.setText(draft.titulo)
        val categoriaIndex = CategoriaPrompt.entries.indexOf(draft.categoria)
        if (categoriaIndex >= 0) binding.spinnerCategoriaPrompt.setSelection(categoriaIndex)
        binding.editPromptObjetivo.setText(draft.objetivo)
        binding.editPromptContexto.setText(draft.contexto)
        binding.editPromptTarefa.setText(draft.tarefa)
        binding.editPromptRestricoes.setText(draft.restricoes)
        binding.editPromptFormato.setText(draft.formatoSaida)
        binding.editPromptLivre.setText(draft.textoLivre)
        binding.editPromptVariaveis.setText(draft.valoresVariaveis.entries.joinToString("\n") { "${it.key}=${it.value}" })
        binding.editPromptComando.setText(draft.comandoRelacionado.orEmpty())
        binding.txtVariaveisDetectadas.text = if (draft.variaveis.isEmpty()) "" else draft.variaveis.joinToString(" · ") { "{{${it.nome}}}" }
        binding.txtPreviewPrompt.text = draft.preview().ifBlank { getString(R.string.prompt_builder_preview_vazio) }
        sincronizandoEditor = false
    }

    private fun valoresDigitados(): Map<String, String> = binding.editPromptVariaveis.text?.toString().orEmpty().lines()
        .mapNotNull { linha -> linha.split("=", limit = 2).takeIf { it.size == 2 }?.let { PromptBuilderDraft.normalizarNome(it[0]) to it[1].trim() } }
        .toMap()

    private fun draftAtualizadoComValores(): PromptBuilderDraft {
        draft = draft.copy(valoresVariaveis = valoresDigitados()).detectarVariaveis()
        return draft
    }

    private fun salvarPromptEditor() {
        val atual = draftAtualizadoComValores()
        if (atual.preview().isBlank()) {
            Snackbar.make(binding.root, getString(R.string.prompt_builder_preview_vazio), Snackbar.LENGTH_SHORT).show()
            return
        }
        binding.btnSalvarPromptEditor.isEnabled = false
        binding.btnSalvarPromptEditor.text = getString(R.string.prompt_builder_salvando)
        lifecycleScope.launch {
            try {
                dadosLocaisRepositorio.salvarOuAtualizarPrompt(atual.toPrompt())
                Snackbar.make(binding.root, getString(R.string.prompt_builder_salvo), Snackbar.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Snackbar.make(binding.root, getString(R.string.prompt_builder_erro_salvar), Snackbar.LENGTH_LONG).show()
            } finally {
                binding.btnSalvarPromptEditor.isEnabled = true
                binding.btnSalvarPromptEditor.text = getString(R.string.prompt_builder_salvar)
            }
        }
    }

    private fun copiarPreview() {
        val atual = draftAtualizadoComValores()
        val texto = atual.preview()
        if (texto.isBlank()) return
        val prompt = atual.toPrompt()
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(prompt.titulo, texto))
        dadosLocaisRepositorio.registrarUtilizacao(prompt.id)
        Snackbar.make(binding.root, getString(R.string.detalhe_prompt_copiado), Snackbar.LENGTH_SHORT).show()
    }

    private fun abrirIaDoEditor() {
        val atual = draftAtualizadoComValores()
        val id = atual.iaDestinoId
        val nome = atual.iaDestinoNome
        if (id.isNullOrBlank() || nome.isNullOrBlank()) {
            Snackbar.make(binding.root, getString(R.string.prompt_builder_sem_ia), Snackbar.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val contrato = IAUrlResolver(applicationContext).resolve(
                IAOpenContract(
                    id, nome, null,
                    com.aibrain.app.brain.UrlResolutionStatus.NOT_FOUND,
                    atual.preview(), PrefillCapability.UNKNOWN, false, BrowserOpenMode.OPEN_ONLY
                )
            )
            if (contrato.urlStatus != com.aibrain.app.brain.UrlResolutionStatus.RESOLVED) {
                Snackbar.make(binding.root, getString(R.string.prompt_builder_sem_ia), Snackbar.LENGTH_LONG).show()
            } else {
                startActivity(BrowserActivity.criarIntent(this@CriadorPromptsActivity, contrato))
            }
        }
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
            val nomes = listOf("Nenhuma IA selecionada") + catalogoIA.map { it.nome }
            binding.spinnerIaDestino.adapter = android.widget.ArrayAdapter(
                this@CriadorPromptsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                nomes
            )
            draft.iaDestinoId?.let { id ->
                val indice = catalogoIA.indexOfFirst { it.id == id }
                if (indice >= 0) binding.spinnerIaDestino.setSelection(indice + 1)
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

    /**
     * Fase 25 — modo "Gerar com IA": envia o texto livre do usuário para a
     * Groq (com a API key cadastrada no Assistente de IA) e a IA responde
     * com um prompt completo, exibido como mensagem do assistente; se o
     * modo estiver desligado, segue o fluxo clássico de perguntas.
     */
    private fun gerarPromptComIA(textoUsuario: String) {
        val apiKey = assistenteIARepositorio.obterApiKey()
        if (apiKey.isNullOrBlank()) {
            adicionarMensagemAssistente(getString(R.string.criador_prompts_sem_api_key))
            return
        }
        gerandoComIA = true
        binding.btnGerarPrompt.isEnabled = false
        binding.progressGerarIA.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                val promptSistema = PromptGeneratorGroq.construirPromptSistema()
                enviarComFallback(GroqClient(apiKey), textoUsuario, promptSistema)
            }

            binding.progressGerarIA.visibility = android.view.View.GONE
            binding.btnGerarPrompt.isEnabled = binding.editMensagemConversa.text?.isNotBlank() == true
            gerandoComIA = false

            when (resultado) {
                is ResultadoComFallback.Sucesso -> {
                    // Sessão fica pronta para salvar/ver o prompt entregue.
                    sessao = sessao.copy(
                        estagio = EstagioConstrutorPrompt.PROMPT_ENTREGUE,
                        textoUsuario = textoUsuario,
                        promptFinal = resultado.texto.trim()
                    )
                    adicionarMensagemAssistente(sessao.promptFinal!!)
                    binding.containerAcoesResultado.visibility = android.view.View.VISIBLE
                }
                is ResultadoComFallback.Falha -> {
                    adicionarMensagemAssistente(
                        getString(R.string.criador_prompts_falha_ia, resultado.motivo)
                    )
                }
            }
        }
    }

    private fun configurarAcoesResultado() {
        binding.btnVerSalvarPrompt.setOnClickListener { salvarEAbrirDetalhePrompt() }
        binding.btnAbrirIARecomendada.setOnClickListener {
            lifecycleScope.launch { abrirIARecomendadaNoNavegador(this@CriadorPromptsActivity, sessao) }
        }
    }

    private fun enviarMensagem() {
        val texto = binding.editMensagemConversa.text?.toString()?.trim().orEmpty()
        if (texto.isEmpty()) return
        if (gerandoComIA) return

        binding.txtConversaVazia.visibility = View.GONE
        adicionarMensagemUsuario(texto)
        binding.editMensagemConversa.text?.clear()
        binding.btnGerarPrompt.isEnabled = false

        if (modoIAAtivo) {
            gerarPromptComIA(texto)
        } else {
            avancarSessao(texto)
        }
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
    companion object {
        const val EXTRA_COMANDO = "comando_inicial"
        const val EXTRA_PROMPT = "prompt_editor"
        const val EXTRA_DUPLICAR = "prompt_duplicar"
        const val EXTRA_TEXTO_INICIAL = "prompt_texto_inicial"
        const val EXTRA_OBJETIVO = "prompt_objetivo"
        const val EXTRA_IA_ID = "prompt_ia_id"
        const val EXTRA_IA_NOME = "prompt_ia_nome"
    }
}
