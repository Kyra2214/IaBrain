package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityDetalheIaBinding
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.CategoriaDinamica
import com.aibrain.app.model.IA
import kotlinx.coroutines.launch

/**
 * Tela de detalhes de UMA IA (Fase 5.1).
 * Exibe: logo, nome, descrição, notas, categorias, idiomas, tipo de acesso.
 *
 * Ainda não abre o site (ação do botão "ABRIR IA" via Custom Tabs
 * entra na Fase 5.2) — aqui o botão só existe visualmente.
 */
class DetalheIAActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheIaBinding
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var imagemCache: ImagemCache

    companion object {
        private const val EXTRA_IA = "extra_ia"

        /** Helper para abrir esta tela passando a IA selecionada. */
        fun criarIntent(context: Context, ia: IA): Intent {
            return Intent(context, DetalheIAActivity::class.java).apply {
                putExtra(EXTRA_IA, ia)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalheIaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        favoritosRepositorio = FavoritosRepository(applicationContext)
        imagemCache = ImagemCache(applicationContext)

        val ia = obterIADoIntent() ?: run {
            finish()
            return
        }

        exibirDados(ia)
    }

    @Suppress("DEPRECATION")
    private fun obterIADoIntent(): IA? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_IA, IA::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_IA)
        }
    }

    private fun exibirDados(ia: IA) {
        binding.txtNome.text = ia.nome
        binding.txtDescricao.text = ia.descricao
        binding.txtTipoAcesso.text = if (ia.gratuita) getString(com.aibrain.app.R.string.detalhe_gratuita)
        else getString(com.aibrain.app.R.string.detalhe_paga)

        // Fase 26 — categorias dinâmicas (novas, fora do enum fixo) também são
        // exibidas com rótulo legível via [CategoriaDinamica.rotulo].
        binding.txtCategorias.text = ia.categorias
            .joinToString(" · ") { CategoriaDinamica.rotulo(it) }

        binding.txtIdiomas.text = ia.idiomas.joinToString(" · ") { it.uppercase() }

        preencherNotas(ia)
        configurarFavorito(ia)
        carregarLogo(ia)

        binding.btnAbrirIA.setOnClickListener {
            abrirIANoNavegador(ia)
        }
    }

    /** Fase 10.1 — carrega o logo via cache (memória/disco/rede ou recurso local). */
    private fun carregarLogo(ia: IA) {
        lifecycleScope.launch {
            val bitmap = imagemCache.carregar(ia.logo, ImagemCache.TAMANHO_DETALHE_DP)
            if (bitmap != null) {
                binding.imgLogo.setImageBitmap(bitmap)
            }
        }
    }

    /** Fase 7.1 — Alternar/exibir estado de favorito desta IA. */
    private fun configurarFavorito(ia: IA) {
        atualizarIconeFavorito(favoritosRepositorio.isFavorita(ia.id))
        binding.btnFavorito.setOnClickListener {
            val agoraFavorita = favoritosRepositorio.alternarFavorita(ia.id)
            atualizarIconeFavorito(agoraFavorita)
            val mensagem = if (agoraFavorita) {
                getString(com.aibrain.app.R.string.favorito_adicionado, ia.nome)
            } else {
                getString(com.aibrain.app.R.string.favorito_removido, ia.nome)
            }
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, mensagem, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun atualizarIconeFavorito(favorita: Boolean) {
        binding.btnFavorito.setImageResource(
            if (favorita) com.aibrain.app.R.drawable.ic_star_filled else com.aibrain.app.R.drawable.ic_star_outline
        )
    }

    /**
     * Fase 5.2 — Abre o site oficial da IA.
     * Fase 21.8 — passa a abrir o navegador interno ([com.aibrain.app.browser.BrowserActivity])
     * em vez de Custom Tabs: se já houver abas abertas, cria uma nova aba
     * (launchMode singleTask) em vez de substituir a atual.
     */
    private fun abrirIANoNavegador(ia: IA) {
        startActivity(
            com.aibrain.app.browser.BrowserActivity.criarIntent(this, ia.nome, ia.site, ia.logo)
        )
    }

    private fun preencherNotas(ia: IA) {
        binding.containerNotas.removeAllViews()

        ia.notas.entries
            .sortedByDescending { it.value }
            .forEach { (chaveCategoria, nota) ->
                // Fase 26 — rótulo legível tanto para categorias fixas (com emoji)
                // quanto para categorias novas criadas pela curadoria.
                val texto = "${CategoriaDinamica.rotulo(chaveCategoria)}: $nota"

                val linha = TextView(this).apply {
                    text = texto
                    setTextColor(getColor(com.aibrain.app.R.color.on_background))
                    textSize = 14f
                    setPadding(0, 4, 0, 4)
                }
                binding.containerNotas.addView(linha)
            }
    }
}
