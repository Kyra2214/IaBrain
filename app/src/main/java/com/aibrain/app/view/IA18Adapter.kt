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
            binding.txtNomeIA.text = ia.nome
            binding.txtDescricaoIA.text = ia.descricao
            binding.txtCategoriaIA.text = ia.status
            
            // Ocultar elementos não utilizados no catálogo +18
            binding.btnFavorito.visibility = android.view.View.GONE
            binding.containerNotas.visibility = android.view.View.GONE

            escopo.launch {
                val bitmap = imagemCache.obter(ia.logo)
                if (bitmap != null) {
                    binding.imgLogoIA.setImageBitmap(bitmap)
                } else {
                    binding.imgLogoIA.setImageResource(com.aibrain.app.R.drawable.ic_image_placeholder)
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
