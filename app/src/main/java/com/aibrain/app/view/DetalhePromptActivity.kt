package com.aibrain.app.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.R
import com.aibrain.app.data.PromptDadosLocaisRepository
import com.aibrain.app.databinding.ActivityDetalhePromptBinding
import com.aibrain.app.model.Prompt
import com.google.android.material.snackbar.Snackbar

/**
 * Tela de detalhes de UM prompt da Biblioteca (Fase 16.5).
 * Exibe: título, categoria/subcaso, nível, descrição, objetivo,
 * compatibilidade, texto completo do template, tags e data de criação.
 *
 * Somente exibição de dados. Copiar (16.6) envia o texto do template para
 * a área de transferência; o campo do template é editável (16.7) — a
 * edição feita pelo usuário antes de tocar em "Copiar" ou "Compartilhar"
 * é o que é usado, sem alterar o `Prompt` original nem persistir a
 * mudança em nenhum repositório. Compartilhar (16.8) abre o seletor
 * padrão do Android (Intent.ACTION_SEND) com o mesmo texto.
 *
 * Fase 16.15 — botão de favorito no topo da tela, mesmo padrão de
 * [DetalheIAActivity] (Fase 7.1), reaproveitando o [PromptDadosLocaisRepository]
 * (Fase 16.14).
 */
class DetalhePromptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalhePromptBinding
    private lateinit var dadosLocaisRepositorio: PromptDadosLocaisRepository

    companion object {
        private const val EXTRA_PROMPT = "extra_prompt"

        /** Helper para abrir esta tela passando o prompt selecionado. */
        fun criarIntent(context: Context, prompt: Prompt): Intent {
            return Intent(context, DetalhePromptActivity::class.java).apply {
                putExtra(EXTRA_PROMPT, prompt)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalhePromptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dadosLocaisRepositorio = PromptDadosLocaisRepository(applicationContext)

        val prompt = obterPromptDoIntent() ?: run {
            finish()
            return
        }

        exibirDados(prompt)
    }

    @Suppress("DEPRECATION")
    private fun obterPromptDoIntent(): Prompt? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PROMPT, Prompt::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_PROMPT)
        }
    }

    private fun exibirDados(prompt: Prompt) {
        binding.txtCategoriaSubcaso.text =
            "${prompt.categoria.emoji} ${prompt.categoria.rotulo} · ${prompt.subcaso}"
        binding.txtTitulo.text = prompt.titulo
        binding.txtNivel.text = getString(R.string.detalhe_prompt_nivel, prompt.nivel)
        binding.txtDescricaoCurta.text = prompt.descricaoCurta
        binding.txtObjetivo.text = prompt.objetivo
        binding.txtCompatibilidade.text = prompt.melhorPara.joinToString(" · ")
        binding.edtTemplate.setText(prompt.template)
        binding.txtTags.text = prompt.tags.joinToString(" ") { "#$it" }
        binding.txtDataCriacao.text = getString(R.string.detalhe_prompt_criado_em, prompt.dataCriacao)

        configurarFavorito(prompt)

        binding.btnCopiarPrompt.setOnClickListener {
            copiarPrompt(prompt)
        }
        binding.btnCompartilharPrompt.setOnClickListener {
            compartilharPrompt(prompt)
        }
    }

    /** Fase 16.15 — Alternar/exibir estado de favorito deste prompt. */
    private fun configurarFavorito(prompt: Prompt) {
        atualizarIconeFavorito(dadosLocaisRepositorio.isFavorito(prompt.id))
        binding.btnFavoritoPrompt.setOnClickListener {
            val agoraFavorito = dadosLocaisRepositorio.alternarFavorito(prompt.id)
            atualizarIconeFavorito(agoraFavorito)
            val mensagem = if (agoraFavorito) {
                getString(R.string.prompt_favorito_adicionado, prompt.titulo)
            } else {
                getString(R.string.prompt_favorito_removido, prompt.titulo)
            }
            Snackbar.make(binding.root, mensagem, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun atualizarIconeFavorito(favorito: Boolean) {
        binding.btnFavoritoPrompt.setImageResource(
            if (favorito) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    /**
     * Fase 16.6/16.7 — Copia o texto do template (Fase 16.7: já refletindo
     * qualquer edição feita pelo usuário no campo antes deste toque) para a
     * área de transferência.
     */
    private fun copiarPrompt(prompt: Prompt) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textoParaCopiar = binding.edtTemplate.text.toString()
        val clip = ClipData.newPlainText(prompt.titulo, textoParaCopiar)
        clipboardManager.setPrimaryClip(clip)
        dadosLocaisRepositorio.registrarUtilizacao(prompt.id)

        Snackbar.make(binding.root, getString(R.string.detalhe_prompt_copiado), Snackbar.LENGTH_SHORT).show()
    }

    /** Fase 16.8 — Compartilha o texto do template via Intent de compartilhamento do Android. */
    private fun compartilharPrompt(prompt: Prompt) {
        val textoParaCompartilhar = binding.edtTemplate.text.toString()
        val intentCompartilhar = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, prompt.titulo)
            putExtra(Intent.EXTRA_TEXT, textoParaCompartilhar)
        }
        dadosLocaisRepositorio.registrarUtilizacao(prompt.id)
        startActivity(Intent.createChooser(intentCompartilhar, prompt.titulo))
    }
}
