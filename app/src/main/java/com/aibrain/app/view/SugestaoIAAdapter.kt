package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.databinding.ItemSugestaoIaBinding
import com.aibrain.app.groq.SugestaoIA

/**
 * Adapter da lista de sugestões de novas IAs (Fase 18.8), a partir das
 * [SugestaoIA] já parseadas (Fase 18.7) da resposta da Groq (Fase 18.6).
 * Cada item tem um botão "Adicionar ao catálogo" que só pré-preenche/copia
 * os dados (Fase 18.8) — a inserção efetiva no `ia_catalogo.json` continua
 * manual/revisada pelo curador (ver ASSUMINDO da Fase 18 no Roadmap).
 */
class SugestaoIAAdapter(
    private val aoAdicionar: (SugestaoIA) -> Unit
) : ListAdapter<SugestaoIA, SugestaoIAAdapter.SugestaoViewHolder>(DIFF_CALLBACK) {

    inner class SugestaoViewHolder(val binding: ItemSugestaoIaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SugestaoViewHolder {
        val binding = ItemSugestaoIaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SugestaoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SugestaoViewHolder, position: Int) {
        val sugestao = getItem(position)
        holder.binding.txtNomeSugestao.text = sugestao.nome
        holder.binding.txtSiteSugestao.text = sugestao.site
        holder.binding.txtCategoriaSugestao.text = sugestao.categoriaSugerida
        holder.binding.btnAdicionarSugestao.setOnClickListener { aoAdicionar(sugestao) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SugestaoIA>() {
            override fun areItemsTheSame(oldItem: SugestaoIA, newItem: SugestaoIA) =
                oldItem.nome == newItem.nome && oldItem.site == newItem.site
            override fun areContentsTheSame(oldItem: SugestaoIA, newItem: SugestaoIA) = oldItem == newItem
        }
    }
}
