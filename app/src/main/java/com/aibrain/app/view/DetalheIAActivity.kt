package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.R
import com.aibrain.app.brain.BrainChatContext
import com.aibrain.app.brain.BrainDiscoveryEngine
import com.aibrain.app.brain.IAOpenContract
import com.aibrain.app.brain.IAUrlResolver
import com.aibrain.app.brain.UrlResolutionStatus
import com.aibrain.app.browser.BrowserActivity
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityDetalheIaBinding
import com.aibrain.app.model.CategoriaDinamica
import com.aibrain.app.model.IA
import com.aibrain.app.repository.CatalogoRepository
import kotlinx.coroutines.launch

/** Detalhes úteis de uma IA usando somente dados do catálogo sincronizado. */
class DetalheIAActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheIaBinding
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var imagemCache: ImagemCache
    private var iaAtual: IA? = null

    companion object {
        private const val EXTRA_IA = "extra_ia"

        fun criarIntent(context: Context, ia: IA): Intent = Intent(context, DetalheIAActivity::class.java).apply {
            putExtra(EXTRA_IA, ia)
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
        iaAtual = ia
        binding.btnVoltarDetalhe.setOnClickListener { finish() }
        exibirDados(ia)
    }

    @Suppress("DEPRECATION")
    private fun obterIADoIntent(): IA? = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_IA, IA::class.java)
    } else {
        intent.getParcelableExtra(EXTRA_IA)
    }

    private fun exibirDados(ia: IA) {
        binding.txtNome.text = ia.nome
        binding.txtDescricao.text = ia.descricao
        binding.txtTipoAcesso.text = "${ia.acesso.emoji} ${ia.acesso.rotulo}"
        binding.txtCategorias.text = ia.categorias.joinToString(" · ") { CategoriaDinamica.rotulo(it) }
        binding.txtCapacidades.text = BrainDiscoveryEngine.capacidadesDaIA(ia)
            .joinToString(" · ") { "✓ ${CategoriaDinamica.rotuloCurto(it)}" }
            .ifBlank { getString(R.string.detalhe_sem_capacidades) }
        binding.txtIdiomas.text = ia.idiomas.joinToString(" · ") { it.uppercase() }
            .ifBlank { getString(R.string.detalhe_nao_informado) }
        binding.txtCasosDeUso.text = ia.casosDeUso.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            ?.let { getString(R.string.detalhe_casos_de_uso, it) }
            ?: getString(R.string.detalhe_casos_de_uso_nao_informado)
        binding.txtPorqueUsarConteudo.text = construirPorQueUsar(ia)

        preencherNotas(ia)
        configurarFavorito(ia)
        carregarLogo(ia)
        binding.btnAbrirIA.setOnClickListener { abrirIANoNavegador(ia) }
        binding.btnCriarPrompt.setOnClickListener { criarPromptParaIA(ia) }
        binding.btnPerguntarChat.setOnClickListener { perguntarNoChat(ia) }
        binding.btnComparar.setOnClickListener { compararComRelacionadas(ia) }
    }

    private fun construirPorQueUsar(ia: IA): String = buildList {
        ia.categoriaPrincipal?.let { add("Especialidade principal: ${CategoriaDinamica.rotuloCurto(it)}") }
        ia.notas.maxByOrNull { it.value }?.let { (categoria, nota) ->
            add("Maior nota cadastrada: ${CategoriaDinamica.rotuloCurto(categoria)} ($nota/10)")
        }
        if (ia.gratuita) add("Possui uma forma de acesso ${ia.acesso.rotulo.lowercase()}")
        if (ia.casosDeUso.isNotEmpty()) add("Há casos de uso cadastrados para orientar a escolha")
    }.joinToString("\n") { "✓ $it" }.ifBlank { getString(R.string.detalhe_sem_dados_recomendacao) }

    private fun carregarLogo(ia: IA) {
        lifecycleScope.launch {
            imagemCache.carregar(ia.logo, ImagemCache.TAMANHO_DETALHE_DP)?.let(binding.imgLogo::setImageBitmap)
        }
    }

    private fun configurarFavorito(ia: IA) {
        atualizarIconeFavorito(favoritosRepositorio.isFavorita(ia.id))
        binding.btnFavorito.setOnClickListener {
            val favorita = favoritosRepositorio.alternarFavorita(ia.id)
            atualizarIconeFavorito(favorita)
            binding.btnFavorito.contentDescription = getString(if (favorita) R.string.detalhe_desfavoritar_desc else R.string.detalhe_favoritar_desc)
            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(if (favorita) R.string.favorito_adicionado else R.string.favorito_removido, ia.nome),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun atualizarIconeFavorito(favorita: Boolean) {
        binding.btnFavorito.setImageResource(if (favorita) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        binding.btnFavorito.contentDescription = getString(if (favorita) R.string.detalhe_desfavoritar_desc else R.string.detalhe_favoritar_desc)
    }

    /** A UI cria apenas o contrato; URL e capacidades são resolvidas pelo componente único. */
    private fun abrirIANoNavegador(ia: IA) {
        lifecycleScope.launch {
            val contrato = IAOpenContract(ia.id, ia.nome, null, UrlResolutionStatus.NOT_FOUND, "")
            val resolvido = IAUrlResolver(applicationContext).resolve(contrato)
            if (resolvido.urlStatus != UrlResolutionStatus.RESOLVED || resolvido.officialResolvedUrl == null) {
                Toast.makeText(this@DetalheIAActivity, R.string.detalhe_url_indisponivel, Toast.LENGTH_SHORT).show()
                return@launch
            }
            startActivity(BrowserActivity.criarIntent(this@DetalheIAActivity, resolvido))
        }
    }

    private fun compararComRelacionadas(ia: IA) {
        lifecycleScope.launch {
            val catalogo = runCatching { CatalogoRepository(applicationContext).carregarCatalogoSincronizado() }.getOrDefault(emptyList())
            val relacionadas = catalogo.filter { candidata ->
                candidata.id == ia.id || candidata.categorias.any { it in ia.categorias }
            }.sortedWith(compareByDescending<IA> { candidata ->
                candidata.categorias.count { it in ia.categorias } * 100 + candidata.notas.filterKeys { it in ia.categorias }.values.sum()
            }.thenBy { it.nome.lowercase() }).take(3)
            if (relacionadas.size < 2) {
                Toast.makeText(this@DetalheIAActivity, R.string.detalhe_comparacao_insuficiente, Toast.LENGTH_SHORT).show()
            } else {
                startActivity(CompararIAsActivity.criarIntent(this@DetalheIAActivity, relacionadas, ia.categorias.joinToString(" ")))
            }
        }
    }

    private fun criarPromptParaIA(ia: IA) {
        val objetivo = ia.categoriaPrincipal?.let(CategoriaDinamica::rotuloCurto)
            ?: ia.categorias.firstOrNull()?.let(CategoriaDinamica::rotuloCurto)
            ?: getString(R.string.detalhe_objetivo_generico)
        startActivity(Intent(this, CriadorPromptsActivity::class.java).putExtra(CriadorPromptsActivity.EXTRA_COMANDO, "Quero usar ${ia.nome} para $objetivo"))
    }

    private fun perguntarNoChat(ia: IA) {
        val contexto = "Quero entender se ${ia.nome} é adequada para ${ia.categorias.joinToString { CategoriaDinamica.rotuloCurto(it) }}."
        startActivity(Intent(this, AIBrainActivity::class.java).putExtra(BrainChatContext.EXTRA_TEXTO_INICIAL, contexto))
    }

    private fun preencherNotas(ia: IA) {
        binding.containerNotas.removeAllViews()
        if (ia.notas.isEmpty()) {
            binding.containerNotas.addView(TextView(this).apply {
                text = getString(R.string.detalhe_sem_notas)
                setTextColor(getColor(R.color.on_background_muted))
                setPadding(0, 4, 0, 4)
            })
            return
        }
        ia.notas.entries.sortedByDescending { it.value }.forEach { (chaveCategoria, nota) ->
            binding.containerNotas.addView(TextView(this).apply {
                text = "${CategoriaDinamica.rotuloCurto(chaveCategoria)}: $nota/10"
                setTextColor(getColor(R.color.on_background))
                setPadding(0, 4, 0, 4)
            })
        }
    }
}
