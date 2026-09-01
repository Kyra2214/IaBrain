package com.aibrain.app.view

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import com.aibrain.app.R
import com.aibrain.app.navigation.GlobalNavigation
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import androidx.appcompat.app.AppCompatActivity

class PromptsComandosActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 0)
            setBackgroundColor(getColor(R.color.background))
        }
        root.addView(MaterialTextView(this).apply {
            text = "Prompts / Comandos"
            textSize = 24f
            setTextColor(getColor(R.color.on_background))
        })
        root.addView(MaterialTextView(this).apply {
            text = "Crie, organize e reutilize prompts e comandos do IaBrain."
            textSize = 15f
            setTextColor(getColor(R.color.on_background_muted))
            setPadding(0, 8, 0, 20)
        })
        root.addView(botao("Biblioteca de prompts") { startActivity(Intent(this, BibliotecaActivity::class.java)) })
        root.addView(botao("Criar prompt") { startActivity(Intent(this, CriadorPromptsActivity::class.java)) })
        root.addView(botao("Comandos disponíveis") { startActivity(Intent(this, ComandosActivity::class.java)) })
        setContentView(root)
        GlobalNavigation.attach(this, root, GlobalNavigation.PROMPTS)
    }

    private fun botao(texto: String, click: () -> Unit) = MaterialButton(this).apply {
        text = texto
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    }
}
