package com.aibrain.app.view

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
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
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,84); setBackgroundColor(getColor(R.color.background)) }
        root.addView(TextView(this).apply { text="🧠 Workspace 2.0"; textSize=25f; setTypeface(null,android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.on_background)); setPadding(0,0,0,12) })
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(ScrollView(this).apply{addView(content)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); GlobalNavigation.attach(this,root,GlobalNavigation.PROJETOS); render()
    }

    private fun render() {
        content.removeAllViews(); val pid=projectId
        section("1 · Integração de contribuições", "Revisão por arquivo, decisão explícita e rollback já ativos.")
        section("2 · Prompt Actions unificado", "Histórico local: ${runtime.promptHistory().size} ações. Copiar/salvar/abrir permanecem separados de envio automático.")
        section("3 · Prefill assistido", "Resultado persistido por IA: somente capacidade CONFIRMED pode produzir PREFILLED; caso contrário fallback seguro.")
        section("4 · Workspace 2.0", "Contexto local ${if(pid==null) "global" else "do projeto $pid"} e validações persistidos no armazenamento privado.")
        section("5 · CI Standard 2.0", "Validação local usa estados explícitos; CI remoto permanece NÃO VERIFICADO até evidência real.")
        section("6 · GitHub Workspace", "Estado remoto rastreado sem fingir sincronização: ${pid?.let{runtime.githubState(it).ciStatus} ?: ValidationStatus.NOT_VERIFIED}.")
        section("7 · Chat contextual", "Contexto de workspace pode ser persistido e reutilizado sem criar um segundo catálogo/router.")
        section("8 · Orquestração Multi-IA", "Plano determinístico ordena candidatos por score e nome e exige aprovação do usuário.")
        section("9 · Skills / Workflows", "Skills e execuções são persistidas localmente e nunca enviam automaticamente.")
        section("10 · Memória de projeto", "${pid?.let{runtime.memories(it).size} ?: runtime.memories("").size} memória(s) registrada(s).")
        section("11 · Contexto entre abas", "Snapshot de abas/prompt é persistido para restauração; o navegador continua responsável pelas WebViews.")
        section("12 · Task Center", "${runtime.tasks(pid).size} tarefa(s) · pendentes ${runtime.tasks(pid).count{it.status==TaskStatus.PENDING}}.")
        button("➕ Nova tarefa") { dialog("Nova tarefa") { title -> runtime.createTask(title,pid); render() } }
        button("🧠 Registrar memória") { if(pid==null) toast("Abra este centro a partir de um projeto para registrar memória de projeto") else dialog("Memória") { title -> dialog("Conteúdo") { body -> runtime.addMemory(ProjectMemoryEntry(UUID.randomUUID().toString(),pid,MemoryType.NOTE,title,body)); render() } } }
        button("⚙️ Registrar skill padrão") { runtime.addSkill(SkillDefinition("review-local","Revisão local",listOf("Ler contexto","Analisar alterações","Gerar relatório","Aguardar aprovação"))); toast("Skill registrada") ; render() }
        button("▶ Executar skill local") { val skill=runtime.skills().firstOrNull(); if(skill==null) toast("Registre uma skill primeiro") else { runtime.runSkill(pid ?: "global",skill,"Workspace VNext"); toast("Execução registrada; nenhum envio automático") ; render() } }
        button("🌐 Salvar contexto do navegador") { runtime.saveBrowserContext(BrowserContextSnapshot(null,emptyList(),"WorkspaceVNext",null)); toast("Snapshot salvo") }
    }
    private fun section(title:String,body:String){content.addView(TextView(this).apply{text="$title\n$body";textSize=16f;setTextColor(getColor(R.color.on_background));setPadding(0,10,0,12)})}
    private fun button(text:String,onClick:()->Unit){content.addView(Button(this).apply{this.text=text;setOnClickListener{onClick()};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=6}})}
    private fun dialog(title:String,onDone:(String)->Unit){val input=EditText(this);input.hint=title;AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar"){_,_->val v=input.text.toString().trim();if(v.isNotEmpty())onDone(v)}.show()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    companion object{private const val EXTRA_PROJECT_ID="project_id";fun criarIntent(context:android.content.Context,projectId:String?=null)=android.content.Intent(context,WorkspaceVNextActivity::class.java).putExtra(EXTRA_PROJECT_ID,projectId)}
}
