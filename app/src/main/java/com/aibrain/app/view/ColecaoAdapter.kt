package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.databinding.ItemColecaoBinding
import com.aibrain.app.model.Colecao
import com.aibrain.app.model.Guia

class ColecaoAdapter(
    private val aoClicarColecao: (Colecao) -> Unit = {},
    private val aoClicarGuia: (Guia) -> Unit = {}
) : RecyclerView.Adapter<ColecaoAdapter.ViewHolder>() {
    private var colecoes: List<Colecao> = emptyList()
    private var guias: List<Guia> = emptyList()

    fun mostrarColecoes(items: List<Colecao>) { colecoes = items; guias = emptyList(); notifyDataSetChanged() }
    fun mostrarGuias(items: List<Guia>) { guias = items; colecoes = emptyList(); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemColecaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = colecoes.size + guias.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (colecoes.isNotEmpty()) {
            val item = colecoes[position]
            holder.binding.txtTituloColecao.text = item.titulo
            holder.binding.txtDescricaoColecao.text = item.descricao
            holder.binding.txtMetaColecao.text = "${item.tipo} · ${item.itens.size} temas"
            holder.binding.root.setOnClickListener { aoClicarColecao(item) }
        } else {
            val item = guias[position]
            holder.binding.txtTituloColecao.text = item.titulo
            holder.binding.txtDescricaoColecao.text = item.descricao
            holder.binding.txtMetaColecao.text = "${item.passos.size} passos · ${item.ferramentas.size} ferramentas"
            holder.binding.root.setOnClickListener { aoClicarGuia(item) }
        }
    }

    class ViewHolder(val binding: ItemColecaoBinding) : RecyclerView.ViewHolder(binding.root)
}
