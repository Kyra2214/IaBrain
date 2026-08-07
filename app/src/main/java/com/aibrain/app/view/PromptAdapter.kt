package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.databinding.ItemPromptBinding
import com.aibrain.app.model.Prompt

/**
 * Adapter da listagem de prompts da Biblioteca (Fase 16.11).
 * Mesmo padrão do [IAAdapter] (Fase 6.1): ListAdapter + DiffUtil, sem
 * carregamento de imagem (prompts não têm logo) — cada item mostra
 * título, categoria/subcaso, nível e compatibilidade (`melhor_para`).
 *
 * Fase 16.15 — [aoAlternarFavorito] expõe o toque na estrela do item,
 * mesmo padrão de [IAAdapter.aoAlternarFavorito] (Fase 7.1).
 */
class PromptAdapter(
    private val aoClicar: (Prompt) -> Unit,
    private val aoAlternarFavorito: (Prompt) -> Unit
) : ListAdapter<Prompt, PromptAdapter.PromptViewHolder>(DIFF_CALLBACK) {

    /** Fase 16.15 — IDs atualmente favoritados; usado só para desenhar o ícone da estrela. */
    private var favoritos: Set<String> = emptySet()

    /** Atualiza o conjunto de favoritos exibido e força o redesenho dos ícones. */
    fun atualizarFavoritos(novosFavoritos: Set<String>) {
        favoritos = novosFavoritos
        notifyDataSetChanged()
    }

    inner class PromptViewHolder(val binding: ItemPromptBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val binding = ItemPromptBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PromptViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        val prompt = getItem(position)

        holder.binding.txtTituloItemPrompt.text = prompt.titulo
        holder.binding.txtCategoriaItemPrompt.text =
            "${prompt.categoria.emoji} ${prompt.categoria.rotulo} · ${prompt.subcaso}"

        val contexto = holder.binding.root.context
        val compatibilidade = prompt.melhorPara.joinToString(" · ")
        holder.binding.txtNivelCompatibilidadeItemPrompt.text = if (compatibilidade.isBlank()) {
            contexto.getString(com.aibrain.app.R.string.detalhe_prompt_nivel, prompt.nivel)
        } else {
            contexto.getString(
                com.aibrain.app.R.string.biblioteca_item_nivel_compatibilidade,
                prompt.nivel,
                compatibilidade
            )
        }

        holder.binding.btnFavoritoItemPrompt.setImageResource(
            if (favoritos.contains(prompt.id)) com.aibrain.app.R.drawable.ic_star_filled
            else com.aibrain.app.R.drawable.ic_star_outline
        )
        holder.binding.btnFavoritoItemPrompt.setOnClickListener { aoAlternarFavorito(prompt) }

        holder.binding.root.setOnClickListener { aoClicar(prompt) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Prompt>() {
            override fun areItemsTheSame(oldItem: Prompt, newItem: Prompt) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Prompt, newItem: Prompt) = oldItem == newItem
        }
    }
}
