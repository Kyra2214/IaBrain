package com.aibrain.app.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.R
import com.aibrain.app.brain.AnalisadorWorkspace
import com.aibrain.app.brain.ContribuicaoWorkspace
import com.aibrain.app.brain.FonteContribuicao
import com.aibrain.app.brain.StatusContribuicao
import com.aibrain.app.brain.ValidadorProjeto
import com.aibrain.app.brain.ZipWorkspaceImporter
import com.aibrain.app.data.local.ProjetoRepository
import com.aibrain.app.data.local.ProjetoWorkspaceRepository
import com.aibrain.app.navigation.GlobalNavigation
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class ProjetoDetalheActivity : AppCompatActivity() {
    private lateinit var projetoId: String
    private lateinit var conteudo: LinearLayout
    private lateinit var workspaceRepository: ProjetoWorkspaceRepository
    private lateinit var projetoRepository: ProjetoRepository
    private val escolherZip = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projetoId = intent.getStringExtra(EXTRA_PROJETO_ID) ?: run { finish(); return }
        workspaceRepository = ProjetoWorkspaceRepository(applicationContext)
        projetoRepository = ProjetoRepository(applicationContext)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.background)); setPadding(20, 20, 20, 84) }
        val topo = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        topo.addView(TextView(this).apply { text = getString(R.string.projeto_workspace); textSize = 25f; setTextColor(getColor(R.color.on_background)); setTypeface(null, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        topo.addView(Button(this).apply { text = getString(R.string.projeto_importar_zip); setOnClickListener { abrirSeletorZip() } })
        raiz.addView(topo)
        conteudo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this).apply { addView(conteudo) }
        raiz.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)
        GlobalNavigation.attach(this, raiz, GlobalNavigation.PROJETOS)
        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            val projeto = projetoRepository.buscar(projetoId) ?: return@launch
            val arquivos = workspaceRepository.arquivos(projetoId)
            conteudo.removeAllViews()
            conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = projeto.nome; textSize = 21f; setTextColor(getColor(R.color.on_background)); setTypeface(null, android.graphics.Typeface.BOLD) })
            conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = "${projeto.descricao}\nStack: ${projeto.plataforma ?: "não definida"} · ${projeto.complexidade}"; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 8, 0, 16) })
            conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = getString(R.string.projeto_github) + "\n" + getString(R.string.projeto_github_desconectado); setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 8, 0, 16) })
            conteudo.addView(Button(this@ProjetoDetalheActivity).apply { text = getString(R.string.projeto_validar); setOnClickListener { validar(arquivos) } })
            conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = getString(R.string.projeto_contribuicoes); textSize = 18f; setTextColor(getColor(R.color.on_background)); setPadding(0, 18, 0, 6) })
            if (arquivos.isEmpty()) conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = "Nenhum arquivo recebido no workspace."; setTextColor(getColor(R.color.on_background_muted)) })
            arquivos.groupBy { it.origem }.forEach { (origem, itens) -> conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = "$origem · ${itens.size} arquivo(s)"; setTextColor(getColor(R.color.on_background)); setPadding(0, 4, 0, 4) }) }
            conteudo.addView(TextView(this@ProjetoDetalheActivity).apply { text = getString(R.string.projeto_ci) + "\nAndroid/Kotlin, React/TypeScript e Python possuem perfis adaptáveis.\nBuild remoto: NÃO EXECUTADO."; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 20, 0, 8) })
        }
    }

    private fun abrirSeletorZip() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE) }, escolherZip) }

    @Deprecated("Compatibilidade com Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == escolherZip && resultCode == RESULT_OK) data?.data?.let { importar(it) }
    }

    private fun importar(uri: Uri) {
        lifecycleScope.launch {
            try {
                val arquivo = File(cacheDir, "contribuicao-${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri)?.use { input -> arquivo.outputStream().use(input::copyTo) }
                val nomeFonte = uri.lastPathSegment ?: "arquivo ZIP"
                val resultado = ZipWorkspaceImporter.importar(arquivo, nomeFonte)
                val contribuicao = ContribuicaoWorkspace(projetoId = projetoId, fonte = FonteContribuicao.ZIP, nomeFonte = nomeFonte, arquivos = resultado.arquivos, status = if (resultado.rejeitados.isEmpty()) StatusContribuicao.ANALISADA else StatusContribuicao.CONFLITO)
                workspaceRepository.importarContribuicao(contribuicao)
                val analise = AnalisadorWorkspace.comparar(emptyList(), listOf(contribuicao))
                workspaceRepository.salvarIntegracao(projetoId, listOf(contribuicao.nomeFonte), if (analise.conflitos.isEmpty()) "ANALISADA" else "CONFLITO", analise.conflitos)
                Snackbar.make(conteudo, getString(R.string.projeto_importado), Snackbar.LENGTH_LONG).show()
                carregar()
            } catch (_: Exception) { Snackbar.make(conteudo, getString(R.string.projeto_zip_invalido), Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun validar(arquivos: List<com.aibrain.app.brain.ArquivoWorkspace>) {
        lifecycleScope.launch {
            workspaceRepository.salvarRelatorio(projetoId, ValidadorProjeto.validar(arquivos))
            Snackbar.make(conteudo, getString(R.string.projeto_validado), Snackbar.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_PROJETO_ID = "projeto_id"
        fun criarIntent(context: android.content.Context, projetoId: String) = Intent(context, ProjetoDetalheActivity::class.java).putExtra(EXTRA_PROJETO_ID, projetoId)
    }
}
