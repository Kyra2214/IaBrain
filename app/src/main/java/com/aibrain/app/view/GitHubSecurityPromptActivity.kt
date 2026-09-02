package com.aibrain.app.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.R
import com.aibrain.app.brain.GitHubSecurityPromptBuilder
import com.aibrain.app.navigation.GlobalNavigation

/** Tela local para gerar e copiar o prompt de revisão de segurança do GitHub. */
class GitHubSecurityPromptActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 84)
            setBackgroundColor(getColor(R.color.background))
        }
        root.addView(TextView(this).apply {
            text = "🔐 Prompt de Segurança · GitHub"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.on_background))
        })
        root.addView(TextView(this).apply {
            text = "Gera apenas texto para copiar. O IaBrain não envia, executa, faz merge ou altera o GitHub nesta tela."
            setTextColor(getColor(R.color.on_background_muted))
            setPadding(0, 8, 0, 16)
        })

        val objective = field("Objetivo da mudança")
        val issue = field("Número da Issue (opcional)")
        val pr = field("Número do PR (opcional)")
        val areas = field("Áreas/arquivos alterados (opcional)")
        val output = EditText(this).apply {
            minLines = 12
            gravity = android.view.Gravity.TOP
            setTextColor(getColor(R.color.on_background))
            hint = "O prompt aparecerá aqui..."
            setBackgroundResource(R.drawable.bg_card)
            setPadding(16, 16, 16, 16)
        }

        root.addView(objective)
        root.addView(issue)
        root.addView(pr)
        root.addView(areas)
        root.addView(Button(this).apply {
            text = "🛡️ Gerar prompt profissional"
            setOnClickListener {
                val value = objective.text.toString().trim()
                if (value.isEmpty()) {
                    toast("Informe o objetivo")
                    return@setOnClickListener
                }
                output.setText(
                    GitHubSecurityPromptBuilder.build(
                        objective = value,
                        issueNumber = issue.text.toString(),
                        pullRequest = pr.text.toString(),
                        changedAreas = areas.text.toString()
                    )
                )
            }
        })
        root.addView(Button(this).apply {
            text = "📋 Copiar prompt"
            setOnClickListener {
                val text = output.text.toString().trim()
                if (text.isEmpty()) {
                    toast("Gere o prompt primeiro")
                    return@setOnClickListener
                }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("IaBrain GitHub Security", text))
                toast("Prompt copiado — envio ao GitHub continua manual")
            }
        })
        root.addView(output, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        GlobalNavigation.attach(this, root, GlobalNavigation.PROJETOS)
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setSingleLine(false)
        setTextColor(getColor(R.color.on_background))
        setHintTextColor(getColor(R.color.on_background_muted))
        setPadding(12, 10, 12, 10)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
