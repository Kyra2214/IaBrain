package com.aibrain.app.view

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.R
import com.aibrain.app.brain.*
import com.aibrain.app.model.IA
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.data.local.IARepository
import com.aibrain.app.viewmodel.CriarComIAViewModel
import kotlinx.coroutines.launch

/** Experiência de descoberta por projeto; recomenda apenas IDs presentes no catálogo. */
class CriarComIAActivity : AppCompatActivity() {
    private lateinit var entrada: EditText
    private lateinit var resultado: LinearLayout
    private lateinit var progresso: ProgressBar
    private lateinit var viewModel: CriarComIAViewModel
    private var catalogo: List<IA> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 24) }
        val titulo = TextView(this).apply {
            text = getString(R.string.criar_com_ia_titulo)
            textSize = 26f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.01f
        }
        raiz.addView(titulo)
        raiz.addView(TextView(this).apply {
            text = getString(R.string.criar_com_ia_descricao)
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(0, 8, 0, 12)
        })
        entrada = EditText(this).apply {
            hint = getString(R.string.criar_com_ia_hint)
            minLines = 3
            maxLines = 7
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setHorizontallyScrolling(false)
            isSingleLine = false
            setPadding(12, 12, 12, 12)
        }
        raiz.addView(entrada, LinearLayout.LayoutParams(-1, -2))
        val analisar = Button(this).apply { text = getString(R.string.criar_com_ia_analisar); setOnClickListener { analisarProjeto() } }
        raiz.addView(analisar)
        progresso = ProgressBar(this).apply { visibility = View.GONE }
        raiz.addView(progresso)
        resultado = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultado) }
        raiz.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)
        viewModel = androidx.lifecycle.ViewModelProvider(this)[CriarComIAViewModel::class.java]
        lifecycleScope.launch {
            val fonteInicial = runCatching { CatalogoRepository(applicationContext).carregarCatalogoSincronizado() }.getOrDefault(emptyList())
            val iasLocais = IARepository(applicationContext)
            if (iasLocais.listarAtivas().isEmpty()) iasLocais.importar(fonteInicial)
            catalogo = iasLocais.listarAtivas()
        }
    }

    private fun analisarProjeto() {
        val texto = entrada.text.toString().trim()
        if (texto.isBlank()) { entrada.error = getString(R.string.criar_com_ia_vazio); return }
        entrada.setText("")
        val mensagemUsuario = TextView(this).apply {
            text = texto
            textSize = 16f
            setPadding(16, 14, 16, 14)
            setTextColor(getColor(R.color.on_background))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setBackgroundResource(R.drawable.bg_chat_user)
        }
        progresso.visibility = View.VISIBLE
        resultado.removeAllViews()
        resultado.addView(TextView(this).apply {
            text = "Você"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.END
            setPadding(0, 12, 0, 4)
        })
        resultado.addView(mensagemUsuario, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.END
            marginStart = 32
        })
        val recomendacao = catalogo.recomendarProjeto(texto)
        viewModel.salvarAnalise(recomendacao)
        progresso.visibility = View.GONE
        val intent = recomendacao.intent
        resultado.addView(secao("Análise do projeto", buildString {
            append("Tipo: ${intent.tipoProjeto ?: "não identificado"}\n")
            append("Complexidade: ${intent.complexidade.name.lowercase().replaceFirstChar { it.uppercase() }}\n")
            append("Áreas: ${intent.areas.joinToString { it.rotulo }.ifBlank { "não identificadas" }}")
        }))
        if (!recomendacao.encontrouCorrespondencia) {
            resultado.addView(TextView(this).apply { text = getString(R.string.criar_com_ia_sem_resultado); setPadding(0, 16, 0, 16) })
            return
        }
        resultado.addView(secao("Recomendações por função", ""))
        recomendacao.recomendacoes.forEach { rec ->
            val ia = rec.ia
            val bloco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
            bloco.addView(TextView(this).apply { text = rec.funcao.nome; textSize = 18f; setTypeface(null, Typeface.BOLD) })
            bloco.addView(TextView(this).apply { text = if (ia == null) rec.motivo else "${ia.nome} · nota ${rec.nota}\n${ia.acesso.emoji} ${ia.acesso.rotulo} · ${rec.motivo}" })
            if (ia != null) bloco.addView(Button(this).apply { text = getString(R.string.criar_com_ia_ver_ia); setOnClickListener { abrirDetalhe(ia) } })
            if (rec.alternativas.isNotEmpty()) bloco.addView(TextView(this).apply { text = "Alternativas: " + rec.alternativas.joinToString { it.nome }; setPadding(0, 4, 0, 4) })
            resultado.addView(bloco)
        }
        resultado.addView(secao("Stack sugerida", "${recomendacao.stack.itens.joinToString { it.nome }}\nCusto estimado: ${recomendacao.stack.custoMensalEstimado}"))
    }

    private fun secao(titulo: String, texto: String): TextView = TextView(this).apply {
        text = if (texto.isBlank()) titulo else "$titulo\n$texto"
        textSize = if (texto.isBlank()) 18f else 16f
        typeface = Typeface.create("sans-serif", if (texto.isBlank()) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0, 18, 0, 10)
    }
    private fun abrirDetalhe(ia: IA) { startActivity(DetalheIAActivity.criarIntent(this, ia)) }
}
