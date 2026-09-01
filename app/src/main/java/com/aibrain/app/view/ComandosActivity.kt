package com.aibrain.app.view

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aibrain.app.data.local.ComandoRepository
import kotlinx.coroutines.launch

class ComandosActivity : AppCompatActivity() {
    private lateinit var repo: ComandoRepository
    private lateinit var lista: LinearLayout
    private lateinit var busca: EditText
    private var categoria = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); repo = ComandoRepository(applicationContext)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,20,24,24) }
        raiz.addView(TextView(this).apply { text = "Comandos de IA"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD })
        busca = EditText(this).apply { hint = "Pesquisar comando, slash, alias ou categoria"; maxLines = 1 }
        raiz.addView(busca)
        val categorias = Spinner(this); raiz.addView(categorias)
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(-1,0,1f)); setContentView(raiz)
        lifecycleScope.launch {
            repo.ensureSeed()
            val cats = listOf("Todas") + repo.categorias(); categorias.adapter = ArrayAdapter(this@ComandosActivity, android.R.layout.simple_spinner_dropdown_item, cats)
            categorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(p: AdapterView<*>?) {} ; override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { categoria = if (pos == 0) "" else cats[pos]; carregar() } }
            carregar()
        }
        busca.setOnEditorActionListener { _,_,_ -> carregar(); true }
        busca.setOnKeyListener { _,_,_ -> busca.postDelayed({ carregar() }, 180); false }
    }
    private fun carregar() { lifecycleScope.launch { val itens = repo.pesquisar(busca.text.toString(), categoria); lista.removeAllViews(); itens.forEach { item ->
        lista.addView(TextView(this@ComandosActivity).apply { text = "${item.comando}  ${item.nome}\n${item.descricaoCurta}\n${if (item.favorito) "⭐" else ""} ${item.usoCount} uso(s)"; textSize = 16f; setPadding(0,16,0,16); setOnClickListener { startActivity(Intent(this@ComandosActivity, ComandoDetalheActivity::class.java).putExtra(ComandoDetalheActivity.EXTRA_ID,item.id)) } }, ViewGroup.LayoutParams(-1,-2))
    } } }
}
