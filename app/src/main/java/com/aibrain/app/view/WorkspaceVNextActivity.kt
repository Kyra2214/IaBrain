package com.aibrain.app.view

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.R
import com.aibrain.app.brain.*
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.BrowserContextoEntity
import com.aibrain.app.data.local.ProjetoMemoriaEntity
import com.aibrain.app.data.local.ProjetoSkillEntity
import com.aibrain.app.data.local.ProjetoTarefaEntity
import com.aibrain.app.data.local.WorkspaceVNextRepository
import com.aibrain.app.navigation.GlobalNavigation
import com.aibrain.app.repository.CatalogoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Central operacional dos 12 blocos VNext, com persistência Room como fonte única. */
class WorkspaceVNextActivity : AppCompatActivity() {
    private lateinit var repository: WorkspaceVNextRepository
    private lateinit var content: LinearLayout
    private var projectId: String? = null
    private var catalogo = emptyList<com.aibrain.app.model.IA>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WorkspaceVNextRepository(AppDatabase.getInstance(applicationContext))
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
        carregarEstado()
    }

    private fun carregarEstado() {
        lifecycleScope.launch {
            catalogo = runCatching { CatalogoRepository(applicationContext).carregarCatalogo() }.getOrDefault(emptyList())
            render()
        }
    }

    private fun render() {
        content.removeAllViews()
        val pid = projectId
        section("1 · Integração de contribuições", "Revisão por arquivo, decisão explícita e rollback.")
        section("2 · Prompt Actions unificado", "Ações seguem o contrato central e ficam registradas no histórico do prompt.")
        button("📋 Copiar prompt de teste") {
            val prompt = "Prompt preparado pelo IaBrain — revisão manual obrigatória."
            copiar(prompt)
            lifecycleScope.launch {
                repository.registrarAcao(
                    com.aibrain.app.data.local.PromptAcaoHistoricoEntity(
                        id = UUID.randomUUID().toString(),
                        promptId = "workspace",
                        acao = PromptActionType.COPY.name,
                        criadoEm = System.currentTimeMillis()
                    )
                )
            }
        }
        section("3 · Prefill assistido", "Só adapters com capacidade CONFIRMED podem tentar prefill; caso contrário, copiar/abrir.")
        section("4 · Workspace 2.0", "Contexto ${pid?.let { "do projeto $it" } ?: "global"} persistido localmente.")
        section("5 · CI Standard 2.0", "Validação local explícita; estados remotos não são inventados.")
        section("6 · GitHub Workspace", "Estado remoto permanece NOT_VERIFIED até existir evidência real.")
        section("7 · Chat contextual", "Memórias e contexto do projeto são carregados antes de abrir o Chat.")
        button("💬 Abrir Chat contextual do projeto") {
            lifecycleScope.launch {
                val contexto = buildProjectPrompt()
                startActivity(
                    Intent(this@WorkspaceVNextActivity, AIBrainActivity::class.java)
                        .putExtra(BrainChatContext.EXTRA_TEXTO_INICIAL, contexto)
                )
            }
        }
        section("8 · Orquestração Multi-IA", "Seleção explícita de candidatos; nenhuma IA recebe envio automático.")
        button("🧩 Selecionar IAs para plano") { escolherIAs() }
        section("9 · Skills / Workflows", "Skills ficam persistidas no Room e execução gera uma tarefa auditável.")
        button("⚙️ Registrar skill padrão") {
            if (pid == null) toast("Abra pelo detalhe de um projeto")
            else lifecycleScope.launch {
                val now = System.currentTimeMillis()
                repository.salvarSkill(ProjetoSkillEntity(UUID.randomUUID().toString(), pid, "Revisão local", "Revisão segura do workspace", listOf("Ler contexto", "Analisar alterações", "Gerar relatório", "Aguardar aprovação"), true, now, now))
                toast("Skill registrada no Room")
                render()
            }
        }
        button("▶ Executar skill local") {
            if (pid == null) toast("Abra pelo detalhe de um projeto")
            else lifecycleScope.launch {
                val skill = repository.skillsAtivas(pid).first().firstOrNull()
                if (skill == null) toast("Registre uma skill primeiro")
                else {
                    val now = System.currentTimeMillis()
                    repository.salvarTarefa(ProjetoTarefaEntity(UUID.randomUUID().toString(), pid, "Executar skill: ${skill.nome}", skill.descricao, TaskStatus.WAITING_USER.name, TaskPriority.NORMAL.name, now, now))
                    toast("Skill preparada; aprovação continua manual")
                    render()
                }
            }
        }
        section("10 · Memória de projeto", "Memórias ficam persistidas no Room e entram no contexto do Chat.")
        button("🧠 Registrar memória") {
            if (pid == null) toast("Abra pelo detalhe de um projeto")
            else dialog("Título da memória") { title ->
                dialog("Conteúdo") { body ->
                    lifecycleScope.launch {
                        val now = System.currentTimeMillis()
                        repository.salvarMemoria(ProjetoMemoriaEntity(UUID.randomUUID().toString(), pid, MemoryType.NOTE.name, title, body, now, now))
                        render()
                    }
                }
            }
        }
        section("11 · Contexto entre abas", "Metadados de abas são persistidos; conteúdo sensível não é copiado para a memória.")
        button("🌐 Registrar snapshot do navegador") {
            lifecycleScope.launch {
                repository.salvarContexto(BrowserContextoEntity(UUID.randomUUID().toString(), "WorkspaceVNext", null, emptyList(), null, System.currentTimeMillis()))
                toast("Snapshot de contexto registrado")
            }
        }
        lifecycleScope.launch {
            val tarefas = repository.todasTarefas().first()
            val total = tarefas.count { it.projetoId == pid || pid == null }
            val pendentes = tarefas.count { (it.projetoId == pid || pid == null) && it.status == TaskStatus.PENDING.name }
            section("12 · Task Center", "$total tarefa(s) · pendentes $pendentes.")
            button("📋 Abrir Task Center") { startActivity(Intent(this@WorkspaceVNextActivity, TaskCenterActivity::class.java)) }
            button("➕ Nova tarefa") {
                dialog("Nova tarefa") { title ->
                    lifecycleScope.launch {
                        val now = System.currentTimeMillis()
                        repository.salvarTarefa(ProjetoTarefaEntity(UUID.randomUUID().toString(), pid, title, "Criada pelo Workspace 2.0", TaskStatus.PENDING.name, TaskPriority.NORMAL.name, now, now))
                        render()
                    }
                }
            }
        }
    }

    /** Constrói o contexto real do projeto a partir das memórias e do snapshot de abas persistidos no Room. */
    private suspend fun buildProjectPrompt(): String {
        val pid = projectId ?: return "Quero trabalhar em um projeto. Mostre o contexto necessário antes de continuar."
        val memorias = repository.memorias(pid).first()
        val contextoAbas = repository.contextosRecentes().first().firstOrNull()
        val memoriaTexto = memorias
            .sortedByDescending { it.atualizadoEm }
            .take(12)
            .joinToString("\n") { "- [${it.tipo}] ${it.titulo}: ${it.conteudo}" }
            .ifBlank { "- Nenhuma memória de projeto registrada." }
        val abasTexto = contextoAbas?.let { contexto ->
            val abas = contexto.abas.take(12).joinToString(" | ")
            "Origem: ${contexto.origem}; aba selecionada: ${contexto.abaSelecionadaId ?: "nenhuma"}; abas: ${abas.ifBlank { "nenhuma" }}; prompt associado: ${contexto.prompt ?: "nenhum"}."
        } ?: "Nenhum snapshot de abas registrado."
        return """
            Projeto: $pid
            Quero trabalhar neste projeto pelo Chat contextual do IaBrain.

            MEMÓRIAS DO PROJETO:
            $memoriaTexto

            CONTEXTO RECENTE DO NAVEGADOR:
            $abasTexto

            Use essas informações apenas como contexto local para orientar a conversa.
            Não execute código nem envie prompts automaticamente.
        """.trimIndent()
    }

    private fun escolherIAs() {
        if (catalogo.isEmpty()) { toast("Catálogo indisponível"); return }
        val candidatos = catalogo.take(8)
        val nomes = candidatos.map { it.nome }.toTypedArray()
        val marcados = BooleanArray(nomes.size)
        AlertDialog.Builder(this)
            .setTitle("Escolha as IAs")
            .setMultiChoiceItems(nomes, marcados) { _, which, checked -> marcados[which] = checked }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar plano") { _, _ ->
                val selecionadas = candidatos.filterIndexed { index, _ -> marcados[index] }
                if (selecionadas.isEmpty()) { toast("Nenhuma IA selecionada"); return@setPositiveButton }
                val plano = IaBrainWorkspaceOrchestrator.buildMultiAiPlan(
                    "Tarefa do Workspace", selecionadas.mapIndexed { index, ia -> AiCandidate(ia.id, ia.nome, 1.0 - index * 0.01, "selecionada pelo usuário") }
                )
                lifecycleScope.launch {
                    val now = System.currentTimeMillis()
                    plano.candidates.forEach { ai ->
                        repository.salvarTarefa(ProjetoTarefaEntity(UUID.randomUUID().toString(), projectId, "Plano Multi-IA: ${ai.name}", "Aguardando aprovação explícita", TaskStatus.WAITING_USER.name, TaskPriority.NORMAL.name, now, now))
                    }
                    toast("Plano criado: ${plano.candidates.joinToString { it.name }}")
                }
            }
            .show()
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
