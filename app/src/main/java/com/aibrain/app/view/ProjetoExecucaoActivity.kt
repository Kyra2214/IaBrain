package com.aibrain.app.view

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.brain.ProjectExecutionEngine
import com.aibrain.app.brain.ProjectExecutionStatus
import com.aibrain.app.brain.abrirExecucaoProjeto
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.IARepository
import com.aibrain.app.data.local.PromptRoomRepository
import com.aibrain.app.data.local.ProjetoExecucaoRepository
import com.aibrain.app.data.local.ProjetoIARepository
import com.aibrain.app.data.local.ProjetoRepository
import kotlinx.coroutines.launch

class ProjetoExecucaoActivity : AppCompatActivity() {
    private val projetoId get() = intent.getStringExtra(EXTRA_PROJETO_ID).orEmpty()
    private val funcaoId get() = intent.getStringExtra(EXTRA_FUNCAO_ID).orEmpty()
    private lateinit var status: TextView
    private lateinit var prompt: TextView
    private var executionId: String? = null
    private var iaId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        status = TextView(this)
        prompt = TextView(this)
        val abrir = Button(this).apply { text = "Abrir IA" }
        val registrar = Button(this).apply { text = "Registrar resultado"; isEnabled = false }
        root.addView(status); root.addView(prompt); root.addView(abrir); root.addView(registrar)
        setContentView(root)

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val projeto = ProjetoRepository(applicationContext).buscar(projetoId)
            val funcao = db.projetoFuncaoDao().buscar(funcaoId)
            if (projeto == null || funcao == null) { finish(); return@launch }
            val engine = ProjectExecutionEngine(IARepository(applicationContext), ProjetoIARepository(applicationContext), ProjetoExecucaoRepository(applicationContext), PromptRoomRepository(applicationContext))
            val plan = engine.prepare(projeto, funcao)
            status.text = "Status: ${plan.state.status}\nIA: ${plan.state.iaId ?: "nenhuma"}\nConfiança: ${"%.0f".format(plan.state.confidence * 100)}%"
            prompt.text = plan.state.prompt ?: plan.state.reason
            executionId = plan.state.executionId
            iaId = plan.state.iaId
            registrar.isEnabled = plan.state.status == ProjectExecutionStatus.WAITING_USER && executionId != null
            abrir.isEnabled = iaId != null && plan.state.prompt != null
        }

        abrir.setOnClickListener {
            lifecycleScope.launch {
                val id = executionId
                if (id != null && !ProjetoExecucaoRepository(applicationContext).iniciar(id)) {
                    Toast.makeText(this@ProjetoExecucaoActivity, "Esta execução não está mais disponível.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                status.text = "Status: ${ProjectExecutionStatus.RUNNING}\nAguardando resultado da IA..."
                val ok = iaId?.let { abrirExecucaoProjeto(this@ProjetoExecucaoActivity, it, prompt.text.toString()) } == true
                if (!ok) {
                    id?.let { ProjetoExecucaoRepository(applicationContext).concluir(it, null, "URL oficial da IA não pôde ser resolvida") }
                    Toast.makeText(this@ProjetoExecucaoActivity, "Não foi possível resolver a URL oficial da IA.", Toast.LENGTH_LONG).show()
                }
            }
        }
        registrar.setOnClickListener { pedirResultado() }
    }

    private fun pedirResultado() {
        val campo = android.widget.EditText(this).apply { hint = "Cole ou descreva o resultado recebido da IA"; minLines = 6; setPadding(24, 16, 24, 16) }
        AlertDialog.Builder(this).setTitle("Resultado da execução").setView(campo).setNegativeButton("Cancelar", null).setPositiveButton("Concluir") { _, _ ->
            lifecycleScope.launch {
                executionId?.let { ProjetoExecucaoRepository(applicationContext).concluir(it, campo.text?.toString().orEmpty()) }
                Toast.makeText(this@ProjetoExecucaoActivity, "Função concluída e registrada no histórico.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.show()
    }

    companion object {
        const val EXTRA_PROJETO_ID = "projeto_id"
        const val EXTRA_FUNCAO_ID = "funcao_id"
    }
}
