package com.aibrain.app.view

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.R
import com.aibrain.app.brain.*
import com.aibrain.app.navigation.GlobalNavigation
import java.util.UUID

/** Central operacional dos 12 blocos VNext, com estado persistente local. */
class WorkspaceVNextActivity : AppCompatActivity() {
    private lateinit var runtime: WorkspaceVNextRuntime
    private lateinit var content: LinearLayout
    private var projectId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = WorkspaceVNextRuntime(applicationContext)
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 84)
            setBackgroundColor(getColor(R.color.background))
        }
        root.addView(TextView(this).apply {
            text = "🧠 Workspace 2.0"
            textSize = 25f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.on_background))
            setPadding(0, 0, 0, 12)
        })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        GlobalNavigation.attach(this, root, GlobalNavigation.PROJETOS)
        render()
    }

    private fun render() {
        content.removeAllViews()
        val pid = projectId
        section("1 · Integração de contribuições", "Revisão por arquivo, decisão explícita e rollback.")
        section("2 · Prompt Actions unificado", "Histórico local: ${runtime.promptHistory().size} ações.")
        button("📋 Copiar prompt de teste") {
            copiar("Prompt preparado pelo IaBrain — revisão manual obrigatória.")
            runtime.recordPromptAction("Prompt preparado pelo IaBrain — revisão manual obrigatória.", null, null, PromptActionType.COPY)
            render()
        }
        section("3 · Prefill assistido", "Capacidade confirmada é obrigatória; sem suporte, o fluxo cai para copiar/abrir.")
        section("4 · Workspace 2.0", "Contexto local ${pid?.let { "do projeto $it" } ?: "global"}.")
        section("5 · CI Standard 2.0", "Estados locais/remotos explícitos; remoto sem evidência fica NÃO VERIFICADO.")
        section("6 · GitHub Workspace", "Estado rastreado localmente; nenhuma sincronização falsa: ${pid?.let { runtime.githubState(it).ciStatus } ?: ValidationStatus.NOT_VERIFIED}.")
        section("7 · Chat contextual", "Contexto de projeto pode ser persistido e reutilizado pelo fluxo existente.")
        section("8 · Orquestração Multi-IA", "Candidatos são ordenados por score e nome; execução exige aprovação do usuário.")
        button("🧩 Criar plano Multi-IA de demonstração") {
            val p = IaBrainWorkspaceOrchestrator.buildMultiAiPlan(
                "Analisar projeto",
                listOf(
                    AiCandidate("local-1", "IA Principal", 0.92, "melhor aderência"),
                    AiCandidate("local-2", "IA Alternativa", 0.84, "segunda opção")
                )
            )
            toast("Plano: ${p.candidates.joinToString { it.name }} — aprovação necessária")
        }
        section("9 · Skills / Workflows", "Execuções persistidas e sem envio automático.")
        section("10 · Memória de projeto", "${pid?.let { runtime.memories(it).size } ?: 0} memória(s).")
        button("🧠 Registrar memória") {
            if (pid == null) toast("Abra pelo detalhe de um projeto")
            else dialog("Título da memória") { title ->
                dialog("Conteúdo") { body ->
                    runtime.addMemory(ProjectMemoryEntry(UUID.randomUUID().toString(), pid, MemoryType.NOTE, title, body))
                    render()
                }
            }
        }
        section("11 · Contexto entre abas", "Snapshot persistente; navegador mantém as WebViews e abas.")
        button("🌐 Salvar contexto atual") {
            runtime.saveBrowserContext(BrowserContextSnapshot(null, emptyList(), "WorkspaceVNext", null))
            toast("Contexto salvo")
        }
        section("12 · Task Center", "${runtime.tasks(pid).size} tarefa(s) · pendentes ${runtime.tasks(pid).count { it.status == TaskStatus.PENDING }}.")
        button("📋 Abrir Task Center") { startActivity(Intent(this, TaskCenterActivity::class.java)) }
        button("➕ Nova tarefa") {
            dialog("Nova tarefa") { title -> runtime.createTask(title, pid); render() }
        }
        button("⚙️ Registrar skill padrão") {
            runtime.addSkill(SkillDefinition("review-local", "Revisão local", listOf("Ler contexto", "Analisar alterações", "Gerar relatório", "Aguardar aprovação")))
            toast("Skill registrada")
            render()
        }
        button("▶ Executar skill local") {
            val skill = runtime.skills().firstOrNull()
            if (skill == null) toast("Registre uma skill primeiro")
            else {
                runtime.runSkill(pid ?: "global", skill, "Workspace VNext")
                toast("Execução registrada; nenhum envio automático")
                render()
            }
        }
    }

    private fun section(title: String, body: String) {
        content.addView(TextView(this).apply {
            text = "$title\n$body"
            textSize = 16f
            setTextColor(getColor(R.color.on_background))
            setPadding(0, 10, 0, 12)
        })
    }

    private fun button(text: String, onClick: () -> Unit) {
        content.addView(Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 }
        })
    }

    private fun copiar(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("IaBrain", text))
        toast("Copiado — envio continua manual")
    }

    private fun dialog(title: String, onDone: (String) -> Unit) {
        val input = EditText(this)
        input.hint = title
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onDone) }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        fun criarIntent(context: Context, projectId: String? = null) = Intent(context, WorkspaceVNextActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}
