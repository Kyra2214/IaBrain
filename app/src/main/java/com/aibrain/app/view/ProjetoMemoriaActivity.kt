package com.aibrain.app.view

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.ProjetoMemoriaEntity
import com.aibrain.app.data.local.WorkspaceVNextRepository
import com.aibrain.app.navigation.GlobalNavigation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Memória de projeto 2.0: CRUD local, tipagem explícita e revisão humana. */
class ProjetoMemoriaActivity : AppCompatActivity() {
    private lateinit var repository: WorkspaceVNextRepository
    private lateinit var lista: LinearLayout
    private var projetoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WorkspaceVNextRepository(AppDatabase.getInstance(applicationContext))
        projetoId = intent.getStringExtra(EXTRA_PROJECT_ID)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 84) }
        root.addView(TextView(this).apply { text = "🧠 Memória do projeto"; textSize = 24f; setTypeface(null, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "Registre decisões, arquitetura, problemas e soluções para reutilização no contexto do Chat."; textSize = 15f; setPadding(0, 8, 0, 12) })
        root.addView(Button(this).apply { text = "➕ Nova memória"; setOnClickListener { criarMemoria() } })
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        GlobalNavigation.attach(this, root, GlobalNavigation.PROJETOS)
        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            lista.removeAllViews()
            val pid = projetoId
            if (pid == null) { lista.addView(TextView(this@ProjetoMemoriaActivity).apply { text = "Nenhum projeto selecionado."; textSize = 16f }); return@launch }
            val memorias = repository.memorias(pid).first()
            if (memorias.isEmpty()) lista.addView(TextView(this@ProjetoMemoriaActivity).apply { text = "Nenhuma memória registrada ainda."; textSize = 16f; setPadding(0, 20, 0, 20) })
            memorias.forEach { adicionarCard(it) }
        }
    }

    private fun adicionarCard(memoria: ProjetoMemoriaEntity) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            background = GradientDrawable().apply { cornerRadius = 18f; setStroke(1, 0x33000000) }
        }
        card.addView(TextView(this).apply { text = "${memoria.tipo} · ${memoria.titulo}"; textSize = 17f; setTypeface(null, android.graphics.Typeface.BOLD) })
        card.addView(TextView(this).apply { text = memoria.conteudo; textSize = 15f; setPadding(0, 8, 0, 8) })
        card.addView(TextView(this).apply { text = "Atualizada: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(memoria.atualizadoEm))}"; textSize = 12f })
        card.addView(Button(this).apply {
            text = "🗑 Remover"
            setOnClickListener { AlertDialog.Builder(this@ProjetoMemoriaActivity).setTitle("Remover memória?").setMessage(memoria.titulo).setNegativeButton("Cancelar", null).setPositiveButton("Remover") { _, _ -> lifecycleScope.launch { repository.removerMemoria(memoria); carregar() } }.show() }
        })
        lista.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 10) })
    }

    private fun criarMemoria() {
        val pid = projetoId ?: run { toast("Abra pelo detalhe de um projeto"); return }
        val tipos = arrayOf("NOTE", "DECISION", "ARCHITECTURE", "PROBLEM", "SOLUTION")
        var tipo = tipos[0]
        val titulo = EditText(this).apply { hint = "Título" }
        val conteudo = EditText(this).apply { hint = "Conteúdo"; minLines = 4; gravity = android.view.Gravity.TOP }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 0); addView(titulo); addView(conteudo) }
        AlertDialog.Builder(this).setTitle("Nova memória").setSingleChoiceItems(tipos, 0) { _, which -> tipo = tipos[which] }.setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
            val t = titulo.text.toString().trim(); val c = conteudo.text.toString().trim()
            if (t.isBlank() || c.isBlank()) { toast("Título e conteúdo são obrigatórios"); return@setPositiveButton }
            lifecycleScope.launch { val now = System.currentTimeMillis(); repository.salvarMemoria(ProjetoMemoriaEntity(UUID.randomUUID().toString(), pid, tipo, t, c, now, now)); carregar() }
        }.show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        fun criarIntent(context: android.content.Context, projectId: String) = android.content.Intent(context, ProjetoMemoriaActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}
