package com.aibrain.app.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.MainActivity
import com.aibrain.app.R
import com.aibrain.app.data.AssistenteIARepository
import com.aibrain.app.databinding.ActivityAssistenteIaBinding
import com.aibrain.app.groq.GroqClient
import com.aibrain.app.groq.ParserCuradoriaIA
import com.aibrain.app.groq.PromptCuradoriaIA
import com.aibrain.app.groq.ResultadoComFallback
import com.aibrain.app.groq.SnippetCatalogoIA
import com.aibrain.app.groq.SugestaoIA
import com.aibrain.app.groq.enviarComFallback
import com.aibrain.app.repository.CatalogoRepository
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela "Assistente de IA" (Fase 18.2) — gerencia a API key da Groq usada
 * exclusivamente pelo Assistente de curadoria (Fase 18), que sugere NOVAS
 * IAs (nome + site) para adicionar manualmente ao catálogo.
 *
 * Não tem qualquer relação com o `RecomendadorIA` offline (Fase 9), que
 * continua funcionando sem internet e sem chave nenhuma — esta tela é
 * só o cadastro da chave, reaproveitando [AssistenteIARepository] (Fase 18.1).
 *
 * Fase 18.3 — quando aberta a partir do onboarding ([WelcomeActivity] na
 * primeira execução, [EXTRA_VEIO_DO_ONBOARDING]), a pilha de back não tem
 * mais nenhuma Activity (`WelcomeActivity` já chamou `finish()`); nesse
 * caso "Voltar" leva explicitamente para [MainActivity] em vez de fechar
 * o app. Fora do onboarding (acesso normal pela navegação principal,
 * Fase 18.2), "Voltar" continua um simples `finish()`.
 */
class AssistenteIAActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistenteIaBinding
    private lateinit var repositorio: AssistenteIARepository
    private lateinit var adapterSugestoes: SugestaoIAAdapter
    private var veioDoOnboarding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssistenteIaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repositorio = AssistenteIARepository(applicationContext)
        veioDoOnboarding = intent.getBooleanExtra(EXTRA_VEIO_DO_ONBOARDING, false)

        adapterSugestoes = SugestaoIAAdapter(aoAdicionar = ::adicionarAoCatalogo)
        binding.recyclerSugestoesCuradoria.layoutManager = LinearLayoutManager(this)
        binding.recyclerSugestoesCuradoria.adapter = adapterSugestoes

        binding.btnVoltarAssistenteIA.setOnClickListener { voltar() }
        binding.btnIrConsoleGroq.setOnClickListener { irConsoleGroq() }
        binding.btnSalvarApiKey.setOnClickListener { salvarApiKey() }
        binding.btnRemoverApiKey.setOnClickListener { removerApiKey() }
        binding.btnConsultarCuradoria.setOnClickListener { consultarCuradoria() }

        atualizarEstadoCampo()
    }

    override fun onResume() {
        super.onResume()
        atualizarEstadoCampo()
    }

    /** Reflete se já existe chave salva: hint muda e o aviso de "já configurada" aparece. */
    private fun atualizarEstadoCampo() {
        val jaConfigurada = repositorio.temApiKey()
        binding.editApiKeyGroq.hint = getString(
            if (jaConfigurada) R.string.assistente_ia_campo_hint_configurada
            else R.string.assistente_ia_campo_hint_vazia
        )
        binding.txtAvisoJaConfigurada.visibility = if (jaConfigurada) View.VISIBLE else View.GONE
    }

    private fun salvarApiKey() {
        val valor = binding.editApiKeyGroq.text?.toString()?.trim().orEmpty()
        if (valor.isEmpty()) {
            mostrarSnackbar(R.string.assistente_ia_key_vazia)
            return
        }
        repositorio.salvarApiKey(valor)
        binding.editApiKeyGroq.text?.clear()
        atualizarEstadoCampo()
        mostrarSnackbar(R.string.assistente_ia_key_salva)

        // Fase 18.3 — vindo do onboarding, salvar a chave já segue o fluxo natural
        // para a listagem principal, sem exigir um toque extra em "Voltar".
        if (veioDoOnboarding) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun removerApiKey() {
        repositorio.removerApiKey()
        binding.editApiKeyGroq.text?.clear()
        atualizarEstadoCampo()
        mostrarSnackbar(R.string.assistente_ia_key_removida)
    }

    /**
     * Fase 18.6 — envia a pergunta livre do curador para a Groq, com o
     * prompt de sistema fixo ([PromptCuradoriaIA]) que instrui a IA a só
     * sugerir IAs ainda ausentes do catálogo local.
     */
    private fun consultarCuradoria() {
        val pergunta = binding.editPerguntaCuradoria.text?.toString()?.trim().orEmpty()
        if (pergunta.isEmpty()) {
            mostrarSnackbar(R.string.assistente_ia_curadoria_pergunta_vazia)
            return
        }
        val apiKey = repositorio.obterApiKey()
        if (apiKey.isNullOrBlank()) {
            mostrarSnackbar(R.string.assistente_ia_curadoria_sem_api_key)
            return
        }

        binding.progressCuradoria.visibility = View.VISIBLE
        binding.btnConsultarCuradoria.isEnabled = false

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                val nomesJaNoCatalogo = try {
                    CatalogoRepository(applicationContext).carregarCatalogo().map { it.nome }
                } catch (e: Exception) {
                    emptyList()
                }
                val promptSistema = PromptCuradoriaIA.construir(nomesJaNoCatalogo)
                enviarComFallback(GroqClient(apiKey), pergunta, promptSistema)
            }

            binding.progressCuradoria.visibility = View.GONE
            binding.btnConsultarCuradoria.isEnabled = true

            when (resultado) {
                is ResultadoComFallback.Sucesso -> exibirSugestoes(resultado.texto)
                is ResultadoComFallback.Falha -> exibirMensagem(
                    getString(R.string.assistente_ia_curadoria_falha, resultado.motivo)
                )
            }
        }
    }

    /**
     * Fase 18.7/18.8 — parseia a resposta bruta ([ParserCuradoriaIA]) e
     * exibe a lista de sugestões (Fase 18.8); resposta sem sugestões
     * válidas (vazia ou malformada) cai numa mensagem amigável em vez de
     * uma lista vazia, sem nunca crashar.
     */
    private fun exibirSugestoes(respostaBruta: String) {
        val sugestoes = ParserCuradoriaIA.parsear(respostaBruta)
        if (sugestoes.isEmpty()) {
            exibirMensagem(
                if (respostaBruta.isBlank()) getString(R.string.assistente_ia_curadoria_sem_sugestoes)
                else getString(R.string.assistente_ia_curadoria_resposta_malformada)
            )
            return
        }
        binding.txtRespostaCuradoria.visibility = View.GONE
        binding.recyclerSugestoesCuradoria.visibility = View.VISIBLE
        adapterSugestoes.submitList(sugestoes)
    }

    private fun exibirMensagem(texto: String) {
        binding.recyclerSugestoesCuradoria.visibility = View.GONE
        binding.txtRespostaCuradoria.visibility = View.VISIBLE
        binding.txtRespostaCuradoria.text = texto
    }

    /**
     * Fase 18.8 — "Adicionar ao catálogo": pré-preenche os campos da
     * sugestão em um snippet JSON ([SnippetCatalogoIA]) e copia pro
     * clipboard (mesmo padrão de [ClipboardManager] da Fase 16.6). A
     * inserção final em `ia_catalogo.json` continua manual/revisada pelo
     * curador — não escreve no arquivo automaticamente.
     */
    private fun adicionarAoCatalogo(sugestao: SugestaoIA) {
        val snippet = SnippetCatalogoIA.gerar(sugestao)
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(sugestao.nome, snippet))
        mostrarSnackbar(R.string.assistente_ia_curadoria_snippet_copiado)
    }

    private fun voltar() {
        if (veioDoOnboarding) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    /** Fase 19.6/19.7 — abre a página de console da Groq dentro do próprio app, reaproveitando o navegador interno (Fase 21), em vez de um navegador externo. */
    private fun irConsoleGroq() {
        startActivity(
            com.aibrain.app.browser.BrowserActivity.criarIntent(
                this,
                getString(R.string.assistente_ia_groq_console_nome),
                "https://console.groq.com/keys",
                ""
            )
        )
    }

    private fun mostrarSnackbar(resId: Int) {
        Snackbar.make(binding.root, resId, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        /** Fase 18.3 — sinaliza que a tela foi aberta pelo fluxo de onboarding ([WelcomeActivity]). */
        const val EXTRA_VEIO_DO_ONBOARDING = "extra_veio_do_onboarding"
    }
}

