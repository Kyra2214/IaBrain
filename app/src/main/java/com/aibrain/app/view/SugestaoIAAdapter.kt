package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import com.aibrain.app.databinding.ItemSugestaoIaBinding
import com.aibrain.app.groq.SugestaoIA
import com.aibrain.app.groq.SnippetCatalogoIA
import com.aibrain.app.model.CategoriaDinamica

/**
 * Adapter da lista de sugestões de novas IAs (Fase 18.8), a partir das
 * [SugestaoIA] já parseadas (Fase 18.7) da resposta da Groq (Fase 18.6).
 * Cada item tem um botão "Adicionar ao catálogo" que pré-preenche os dados
 * (Fase 18.8) e, a partir da Fase 26, persiste a IA de verdade no catálogo,
 * criando automaticamente uma aba/chip para categorias novas.
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
        // Fase 26 — categoria exibida legível: categoria fixa do app (com
        // emoji) ou categoria nova capitalizada (ex.: "Saúde Mental").
        holder.binding.txtCategoriaSugestao.text =
            CategoriaDinamica.rotulo(SnippetCatalogoIA.categoriaSugerida(sugestao))
        // Fase 26 — descrição curta, quando a Groq responder no formato estendido.
        if (sugestao.descricao.isNotBlank()) {
            holder.binding.txtDescricaoSugestao.visibility = View.VISIBLE
            holder.binding.txtDescricaoSugestao.text = sugestao.descricao
        } else {
            holder.binding.txtDescricaoSugestao.visibility = View.GONE
        }
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
