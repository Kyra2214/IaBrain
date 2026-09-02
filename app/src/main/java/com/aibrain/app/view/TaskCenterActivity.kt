package com.aibrain.app.view

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aibrain.app.R
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.ProjetoTarefaEntity
import com.aibrain.app.data.local.WorkspaceVNextRepository
import com.aibrain.app.navigation.GlobalNavigation
import kotlinx.coroutines.launch

/**
 * Task Center local do Workspace 2.0.
 * As tarefas são persistidas em Room e podem avançar somente por ação explícita
 * do usuário; nenhuma tarefa executa código ou envia prompt automaticamente.
 */
class TaskCenterActivity : AppCompatActivity() {
    private lateinit var lista: LinearLayout
    private lateinit var vazio: TextView
    private lateinit var repository: WorkspaceVNextRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WorkspaceVNextRepository(AppDatabase.getInstance(applicationContext))

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
            setPadding(20, 20, 20, 84)
        }

        val cabecalho = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        cabecalho.addView(TextView(this).apply {
            text = "Task Center"
            textSize = 26f
            setTextColor(getColor(R.color.on_background))
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        cabecalho.addView(Button(this).apply {
            text = "Voltar"
            setOnClickListener { finish() }
        })
        raiz.addView(cabecalho)

        raiz.addView(TextView(this).apply {
            text = "Tarefas locais de projetos · persistentes · sem execução automática"
            setTextColor(getColor(R.color.on_background_muted))
            setPadding(0, 4, 0, 12)
        })

        vazio = TextView(this).apply {
            text = "Nenhuma tarefa cadastrada.\nCrie uma pelo Workspace 2.0."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.on_background_muted))
            setPadding(16, 28, 16, 28)
        }
        raiz.addView(vazio)

        val scroll = ScrollView(this)
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(lista)
        raiz.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(raiz)
        GlobalNavigation.attach(this, raiz, GlobalNavigation.PROJETOS)
        observarTarefas()
    }

    private fun observarTarefas() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.todasTarefas().collect { tarefas -> renderizar(tarefas) }
            }
        }
    }

    private fun renderizar(tarefas: List<ProjetoTarefaEntity>) {
        lista.removeAllViews()
        vazio.visibility = if (tarefas.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        tarefas.forEach { tarefa ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundResource(R.drawable.bg_card)
            }
            card.addView(TextView(this).apply {
                text = tarefa.titulo
                textSize = 18f
                setTextColor(getColor(R.color.on_background))
                setTypeface(null, Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = "${tarefa.status} · prioridade ${tarefa.prioridade}\n${tarefa.detalhe}".trim()
                setTextColor(getColor(R.color.on_background_muted))
                setPadding(0, 6, 0, 8)
            })

            val acoes = LinearLayout(this).apply { gravity = Gravity.END }
            when (tarefa.status) {
                "PENDING", "WAITING_USER" -> acoes.addView(acao("Iniciar") { atualizarStatus(tarefa, "IN_PROGRESS") })
                "IN_PROGRESS" -> {
                    acoes.addView(acao("Aguardar") { atualizarStatus(tarefa, "WAITING_USER") })
                    acoes.addView(acao("Concluir") { atualizarStatus(tarefa, "COMPLETED") })
                }
                "FAILED" -> acoes.addView(acao("Retomar") { atualizarStatus(tarefa, "IN_PROGRESS") })
                "COMPLETED", "CANCELLED" -> Unit
            }
            if (tarefa.status != "COMPLETED" && tarefa.status != "CANCELLED") {
                acoes.addView(acao("Cancelar") { atualizarStatus(tarefa, "CANCELLED") })
            }
            card.addView(acoes)
            lista.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) })
        }
    }

    private fun acao(rotulo: String, clique: () -> Unit) = Button(this).apply {
        text = rotulo
        setOnClickListener { clique() }
    }

    private fun atualizarStatus(tarefa: ProjetoTarefaEntity, status: String) {
        lifecycleScope.launch {
            repository.atualizarTarefa(tarefa.copy(status = status, atualizadoEm = System.currentTimeMillis()))
        }
    }
}
