package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.R
import com.aibrain.app.brain.BrainDiscoveryEngine
import com.aibrain.app.databinding.ActivityCompararIasBinding
import com.aibrain.app.model.IA

/** Comparação local de duas ou três IAs, sem completar lacunas com suposições. */
class CompararIAsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompararIasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompararIasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnVoltarComparacao.setOnClickListener { finish() }

        val ias = obterIAs()
        if (ias.size < 2) {
            finish()
            return
        }
        val objetivo = intent.getStringExtra(EXTRA_OBJETIVO).orEmpty()
        binding.txtComparacaoObjetivo.text = if (objetivo.isBlank()) {
            getString(R.string.brain_comparacao_sem_objetivo)
        } else {
            getString(R.string.brain_comparacao_objetivo, objetivo)
        }
        preencherTabela(ias, objetivo)
    }

    @Suppress("DEPRECATION")
    private fun obterIAs(): List<IA> = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableArrayListExtra(EXTRA_IAS, IA::class.java).orEmpty()
    } else {
        intent.getParcelableArrayListExtra<IA>(EXTRA_IAS).orEmpty()
    }

    private fun preencherTabela(ias: List<IA>, objetivo: String) {
        val tabela = binding.tabelaComparacao
        tabela.removeAllViews()
        tabela.addView(linhaTabela(listOf(getString(R.string.brain_comparacao_criterio)) + ias.map { it.nome }, cabecalho = true))
        BrainDiscoveryEngine.comparar(ias, objetivo).forEach { linha ->
            tabela.addView(linhaTabela(listOf(linha.criterio) + linha.valores))
        }
    }

    private fun linhaTabela(valores: List<String>, cabecalho: Boolean = false): TableRow = TableRow(this).apply {
        layoutParams = TableRow.LayoutParams()
        valores.forEachIndexed { index, valor ->
            addView(TextView(this@CompararIAsActivity).apply {
                text = valor
                setTextColor(getColor(if (cabecalho || index == 0) R.color.on_surface else R.color.on_surface_variant))
                setTypeface(null, if (cabecalho || index == 0) Typeface.BOLD else Typeface.NORMAL)
                gravity = if (index == 0) Gravity.START else Gravity.CENTER
                minWidth = if (index == 0) 180 else 150
                setPadding(16, 16, 16, 16)
                contentDescription = "${valores.first()} ${if (index == 0) "" else "${valores.getOrNull(index)}: "}$valor"
                setBackgroundColor(getColor(if (cabecalho) R.color.surface_container else if (index == 0) R.color.surface_variant else R.color.surface))
            }, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    companion object {
        private const val EXTRA_IAS = "extra_ias_comparacao"
        private const val EXTRA_OBJETIVO = "extra_objetivo_comparacao"

        fun criarIntent(context: Context, ias: List<IA>, objetivo: String = ""): Intent =
            Intent(context, CompararIAsActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_IAS, ArrayList(ias.take(3)))
                putExtra(EXTRA_OBJETIVO, objetivo)
            }
    }
}
