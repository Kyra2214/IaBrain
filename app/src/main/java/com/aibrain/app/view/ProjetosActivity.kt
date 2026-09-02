package com.aibrain.app.view

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.R
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.ProjetoRepository
import com.aibrain.app.navigation.GlobalNavigation
import kotlinx.coroutines.launch

/** Dashboard local de projetos; GitHub é opcional e não é requisito para criar um projeto. */
class ProjetosActivity : AppCompatActivity() {
    private lateinit var lista: LinearLayout
    private lateinit var vazio: TextView
    private lateinit var projetoRepository: ProjetoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projetoRepository = ProjetoRepository(applicationContext)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.background)); setPadding(20, 20, 20, 84) }
        val cabecalho = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        cabecalho.addView(TextView(this).apply { text = getString(R.string.projetos_titulo); textSize = 26f; setTextColor(getColor(R.color.on_background)); setTypeface(null, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        cabecalho.addView(Button(this).apply { text = getString(R.string.projetos_novo); setOnClickListener { startActivity(Intent(this@ProjetosActivity, CriarComIAActivity::class.java)) } })
        raiz.addView(cabecalho)
        raiz.addView(TextView(this).apply { text = getString(R.string.projetos_descricao); setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 4, 0, 16) })
        vazio = TextView(this).apply { text = getString(R.string.projetos_vazio); setTextColor(getColor(R.color.on_background_muted)); gravity = Gravity.CENTER; setPadding(16, 48, 16, 48) }
        raiz.addView(vazio)
        val scroll = ScrollView(this)
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(lista)
        raiz.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)
        GlobalNavigation.attach(this, raiz, GlobalNavigation.PROJETOS)
        observarProjetos()
    }

    override fun onResume() { super.onResume(); if (::lista.isInitialized) observarProjetos() }

    private fun observarProjetos() {
        lifecycleScope.launch {
            projetoRepository.observarTodos().collect { projetos ->
                lista.removeAllViews()
                vazio.visibility = if (projetos.isEmpty()) View.VISIBLE else View.GONE
                projetos.forEach { projeto ->
                    val card = LinearLayout(this@ProjetosActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundResource(R.drawable.bg_card) }
                    card.addView(TextView(this@ProjetosActivity).apply { text = projeto.nome; textSize = 19f; setTextColor(getColor(R.color.on_background)); setTypeface(null, android.graphics.Typeface.BOLD) })
                    card.addView(TextView(this@ProjetosActivity).apply { text = "${projeto.plataforma ?: "Stack não definida"} · ${projeto.complexidade}\n${projeto.status}"; setTextColor(getColor(R.color.on_background_muted)); setPadding(0, 6, 0, 8) })
                    card.addView(Button(this@ProjetosActivity).apply { text = getString(R.string.projetos_abrir); setOnClickListener { startActivity(ProjetoDetalheActivity.criarIntent(this@ProjetosActivity, projeto.id)) } })
                    lista.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) })
                }
            }
        }
    }
}
