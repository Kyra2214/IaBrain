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
import com.aibrain.app.brain.ProjetoIntegracaoEngine.analisar
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
            if (contribuicoes.isEmpty()) {
                mostrarMensagem("Nenhuma contribuição disponível.")
                return@launch
            }
            val ultima = contribuicoes.first()
            if (!store.workspaceExiste(projetoId)) {
                store.inicializarWorkspace(projetoId, ultima.id)
                mostrarMensagem("A primeira contribuição foi definida como workspace base.")
                return@launch
            }
            val base = store.snapshotWorkspace(projetoId)
            val candidata = store.snapshotContribuicao(projetoId, ultima.id, ultima.nomeFonte)
            val analise = analisar(base, candidata)
            conteudo.removeAllViews()
            conteudo.addView(TextView(this@ProjetoIntegracaoActivity).apply {
                text = "Fonte: ${ultima.nomeFonte}\n${analise.mudancas.size} arquivo(s) analisado(s)"
                textSize = 16f; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 8, 0, 18)
            })
            analise.mudancas.forEach { mudanca -> adicionarMudanca(mudanca.caminho, mudanca.tipo.name, mudanca.tipo.name) }
            if (analise.mudancas.isEmpty()) mostrarMensagem("Nenhuma diferença encontrada.")
            val aplicar = Button(this@ProjetoIntegracaoActivity).apply {
                text = "Aplicar integração"
                setOnClickListener { aplicarIntegracao(ultima.id) }
            }
            conteudo.addView(aplicar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24 })
        }
    }

    private fun adicionarMudanca(caminho: String, tipo: String, chave: String) {
        val bloco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
        bloco.addView(TextView(this).apply {
            text = "$tipo · $caminho"; textSize = 15f; setTextColor(getColor(R.color.on_background)); setTypeface(null, Typeface.BOLD)
        })
        val grupo = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val aceitar = RadioButton(this).apply { text = "Aceitar" }
        val manter = RadioButton(this).apply { text = "Manter" }
        val remover = RadioButton(this).apply { text = "Remover" }
        grupo.addView(aceitar); grupo.addView(manter); grupo.addView(remover)
        val tipoMudanca = runCatching { com.aibrain.app.brain.TipoMudanca.valueOf(tipo) }.getOrNull()
        when (tipoMudanca) {
            com.aibrain.app.brain.TipoMudanca.NOVO, com.aibrain.app.brain.TipoMudanca.MODIFICADO -> { aceitar.isChecked = true; decisoes[caminho] = DecisaoIntegracao.ACEITAR_CONTRIBUICAO }
            else -> { manter.isChecked = true; decisoes[caminho] = DecisaoIntegracao.MANTER_ATUAL }
        }
        grupo.setOnCheckedChangeListener { _, id -> decisoes[caminho] = when (id) {
            aceitar.id -> DecisaoIntegracao.ACEITAR_CONTRIBUICAO
            remover.id -> DecisaoIntegracao.REMOVER
            else -> DecisaoIntegracao.MANTER_ATUAL
        } }
        if (aceitar.id == -1) { aceitar.id = 100000 + decisoes.size; manter.id = 200000 + decisoes.size; remover.id = 300000 + decisoes.size }
        bloco.addView(grupo)
        conteudo.addView(bloco)
    }

    private fun aplicarIntegracao(contribuicaoId: String) {
        lifecycleScope.launch {
            val resultado = store.aplicar(projetoId, contribuicaoId, decisoes.map { (caminho, decisao) -> DecisaoArquivo(caminho, decisao) })
            if (resultado.sucesso) {
                workspaceRepository.salvarIntegracao(projetoId, listOf(contribuicaoId), "CONCLUIDA", emptyList())
                workspaceRepository.registrarHistorico(projetoId, "INTEGRACAO_APLICADA", "${resultado.arquivosAplicados.size} aceito(s), ${resultado.arquivosRemovidos.size} removido(s)")
                Snackbar.make(conteudo, "Integração aplicada com sucesso.", Snackbar.LENGTH_LONG).show()
                finish()
            } else {
                Snackbar.make(conteudo, "Integração não aplicada: ${resultado.erros.joinToString()}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarMensagem(texto: String) {
        conteudo.removeAllViews()
        conteudo.addView(TextView(this).apply { text = texto; textSize = 16f; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 20, 0, 20) })
    }

    companion object {
        private const val EXTRA_PROJETO_ID = "projeto_id"
        fun criarIntent(context: android.content.Context, projetoId: String) = android.content.Intent(context, ProjetoIntegracaoActivity::class.java).putExtra(EXTRA_PROJETO_ID, projetoId)
    }
}
