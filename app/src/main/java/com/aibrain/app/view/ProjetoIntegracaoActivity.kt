package com.aibrain.app.view

import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.R
import com.aibrain.app.brain.DecisaoArquivo
import com.aibrain.app.brain.DecisaoIntegracao
import com.aibrain.app.brain.ProjetoIntegracaoEngine
import com.aibrain.app.brain.StatusContribuicao
import com.aibrain.app.brain.TipoMudanca
import com.aibrain.app.brain.WorkspaceFileStore
import com.aibrain.app.data.local.ProjetoWorkspaceRepository
import com.aibrain.app.navigation.GlobalNavigation
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProjetoIntegracaoActivity : AppCompatActivity() {
    private lateinit var projetoId: String
    private lateinit var conteudo: LinearLayout
    private lateinit var workspaceRepository: ProjetoWorkspaceRepository
    private lateinit var store: WorkspaceFileStore
    private val decisoes = linkedMapOf<String, DecisaoIntegracao>()
    private var nextViewId = 10000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projetoId = intent.getStringExtra(EXTRA_PROJETO_ID) ?: run { finish(); return }
        workspaceRepository = ProjetoWorkspaceRepository(applicationContext)
        store = WorkspaceFileStore(applicationContext)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.background)); setPadding(20, 20, 20, 84) }
        raiz.addView(TextView(this).apply { text = "Integração de contribuições"; textSize = 25f; setTextColor(getColor(R.color.on_background)); setTypeface(null, Typeface.BOLD) })
        conteudo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(ScrollView(this).apply { addView(conteudo) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)
        GlobalNavigation.attach(this, raiz, GlobalNavigation.PROJETOS)
        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            val contribuicoes = workspaceRepository.observarContribuicoes(projetoId).first()
            val pendentes = contribuicoes.filter { it.status != StatusContribuicao.INTEGRADA }
            if (pendentes.isEmpty()) { mostrarMensagem("Nenhuma contribuição pendente para integração."); return@launch }
            val ultima = pendentes.first()
            if (!store.workspaceExiste(projetoId)) {
                store.inicializarWorkspace(projetoId, ultima.id)
                workspaceRepository.atualizarStatusContribuicao(ultima.id, StatusContribuicao.INTEGRADA)
                mostrarMensagem("A primeira contribuição foi definida como workspace base.")
                return@launch
            }
            val analise = ProjetoIntegracaoEngine.analisar(store.snapshotWorkspace(projetoId), store.snapshotContribuicao(projetoId, ultima.id, ultima.nomeFonte))
            conteudo.removeAllViews(); decisoes.clear()
            conteudo.addView(TextView(this@ProjetoIntegracaoActivity).apply { text = "Fonte: ${ultima.nomeFonte}\n${analise.mudancas.size} arquivo(s) analisado(s)"; textSize = 16f; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 8, 0, 18) })
            analise.mudancas.forEach { adicionarMudanca(it.caminho, it.tipo) }
            if (analise.mudancas.isEmpty()) mostrarMensagem("Nenhuma diferença encontrada.") else conteudo.addView(Button(this@ProjetoIntegracaoActivity).apply {
                text = "Aplicar integração"
                setOnClickListener { aplicarIntegracao(ultima.id) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24 })
        }
    }

    private fun adicionarMudanca(caminho: String, tipo: TipoMudanca) {
        val bloco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
        bloco.addView(TextView(this).apply { text = "${tipo.name} · $caminho"; textSize = 15f; setTextColor(getColor(R.color.on_background)); setTypeface(null, Typeface.BOLD) })
        val grupo = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val aceitar = RadioButton(this).apply { id = nextViewId++; text = "Aceitar"; isEnabled = tipo != TipoMudanca.REMOVIDO }
        val manter = RadioButton(this).apply { id = nextViewId++; text = "Manter" }
        val remover = RadioButton(this).apply { id = nextViewId++; text = "Remover" }
        grupo.addView(aceitar); grupo.addView(manter); grupo.addView(remover)
        val decisaoInicial = when (tipo) {
            TipoMudanca.NOVO, TipoMudanca.MODIFICADO -> DecisaoIntegracao.ACEITAR_CONTRIBUICAO
            else -> DecisaoIntegracao.MANTER_ATUAL
        }
        decisoes[caminho] = decisaoInicial
        grupo.setOnCheckedChangeListener { _, id -> decisoes[caminho] = when (id) {
            aceitar.id -> DecisaoIntegracao.ACEITAR_CONTRIBUICAO
            remover.id -> DecisaoIntegracao.REMOVER
            else -> DecisaoIntegracao.MANTER_ATUAL
        } }
        when (decisaoInicial) {
            DecisaoIntegracao.ACEITAR_CONTRIBUICAO -> aceitar.isChecked = true
            DecisaoIntegracao.MANTER_ATUAL -> manter.isChecked = true
            DecisaoIntegracao.REMOVER -> remover.isChecked = true
        }
        bloco.addView(grupo); conteudo.addView(bloco)
    }

    private fun aplicarIntegracao(contribuicaoId: String) {
        lifecycleScope.launch {
            val resultado = store.aplicar(projetoId, contribuicaoId, decisoes.map { (caminho, decisao) -> DecisaoArquivo(caminho, decisao) })
            if (resultado.sucesso) {
                workspaceRepository.atualizarStatusContribuicao(contribuicaoId, StatusContribuicao.INTEGRADA)
                workspaceRepository.salvarIntegracao(projetoId, listOf(contribuicaoId), "CONCLUIDA", emptyList())
                workspaceRepository.registrarHistorico(projetoId, "INTEGRACAO_APLICADA", "${resultado.arquivosAplicados.size} aceito(s), ${resultado.arquivosRemovidos.size} removido(s)")
                Snackbar.make(conteudo, "Integração aplicada com sucesso.", Snackbar.LENGTH_LONG).show(); finish()
            } else Snackbar.make(conteudo, "Integração não aplicada: ${resultado.erros.joinToString()}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun mostrarMensagem(texto: String) {
        conteudo.removeAllViews(); conteudo.addView(TextView(this).apply { text = texto; textSize = 16f; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 20, 0, 20) })
    }

    companion object {
        private const val EXTRA_PROJETO_ID = "projeto_id"
        fun criarIntent(context: android.content.Context, projetoId: String) = android.content.Intent(context, ProjetoIntegracaoActivity::class.java).putExtra(EXTRA_PROJETO_ID, projetoId)
    }
}
