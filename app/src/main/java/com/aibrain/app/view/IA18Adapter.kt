package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.databinding.ItemIaBinding
import com.aibrain.app.repository.IA18Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class IA18Adapter(
    private val escopo: CoroutineScope,
    private val imagemCache: ImagemCache,
    private val aoClicar: (IA18Repository.IA18) -> Unit
) : ListAdapter<IA18Repository.IA18, IA18Adapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemIaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ia: IA18Repository.IA18) {
            // Item do catálogo +18 reaproveita o layout padrão `item_ia.xml` do
            // catálogo principal (Fase 6), adaptando os campos existentes ao
            // schema `IA18Repository.IA18` (Fase 22).
            binding.txtNomeItem.text = ia.nome
            binding.txtDescricaoItem.text = ia.descricao
            binding.txtCategoriasItem.text = ia.status

            // Ocultar elementos não utilizados no catálogo +18
            binding.btnFavoritoItem.visibility = android.view.View.GONE

            escopo.launch {
                val bitmap = imagemCache.carregar(ia.logo)
                if (bitmap != null) {
                    binding.imgLogoItem.setImageBitmap(bitmap)
                } else {
                    binding.imgLogoItem.setImageResource(com.aibrain.app.R.drawable.ic_image_placeholder)
                }
            }

            binding.root.setOnClickListener { aoClicar(ia) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<IA18Repository.IA18>() {
        override fun areItemsTheSame(oldItem: IA18Repository.IA18, newItem: IA18Repository.IA18) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IA18Repository.IA18, newItem: IA18Repository.IA18) = oldItem == newItem
    }
}
