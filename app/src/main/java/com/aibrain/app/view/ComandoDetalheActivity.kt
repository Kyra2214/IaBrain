package com.aibrain.app.view

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.data.local.ComandoRepository
import kotlinx.coroutines.launch

class ComandoDetalheActivity : AppCompatActivity() {
    private lateinit var repo: ComandoRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); repo = ComandoRepository(applicationContext)
        val texto = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,20,24,24) }
        val scroll = ScrollView(this); scroll.addView(texto); setContentView(scroll)
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        lifecycleScope.launch {
            val comando = repo.buscar(id) ?: return@launch
            repo.registrarUso(id)
            texto.addView(TextView(this@ComandoDetalheActivity).apply { text = "${comando.comando} · ${comando.nome}"; textSize=25f; typeface=Typeface.DEFAULT_BOLD })
            fun sec(titulo: String, valor: String) { texto.addView(TextView(this@ComandoDetalheActivity).apply { text = "$titulo\n$valor"; textSize=16f; setPadding(0,14,0,8) }) }
            sec("Descrição", comando.descricaoCurta); sec("Explicação", comando.explicacao); sec("Objetivo", comando.objetivo); sec("Quando usar", comando.quandoUsar); sec("Quando não usar", comando.quandoNaoUsar); sec("Sintaxe", comando.sintaxe); sec("Exemplo", comando.exemplo); sec("Categoria / IA recomendada", "${comando.categoria} · ${comando.iaRecomendada}"); sec("Recursos", "Web: ${comando.suportaWeb} · Arquivos: ${comando.suportaArquivos} · Projeto: ${comando.suportaProjeto} · Multi-IA: ${comando.suportaMultiplasIAs}"); sec("Complexidade / usos", "${comando.nivel} · ${comando.usoCount}")
            val favorito = Button(this@ComandoDetalheActivity).apply { text = if (comando.favorito) "Remover favorito" else "Favoritar"; setOnClickListener { lifecycleScope.launch { repo.alternarFavorito(id, comando.favorito); recreate() } } }
            texto.addView(favorito)
            texto.addView(Button(this@ComandoDetalheActivity).apply { text = "USAR COMANDO"; setOnClickListener { startActivity(Intent(this@ComandoDetalheActivity, CriadorPromptsActivity::class.java).putExtra(CriadorPromptsActivity.EXTRA_COMANDO, "${comando.comando} ")) } })
        }
    }
    companion object { const val EXTRA_ID = "comando_id" }
}
