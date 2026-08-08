package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.databinding.ItemIaBinding
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.CategoriaDinamica
import com.aibrain.app.model.IA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adapter da listagem de IAs (Fase 6.1).
 * Usa ListAdapter + DiffUtil para atualizar a lista de forma eficiente
 * quando o catálogo mudar (importante para a Fase 8 - atualização automática).
 *
 * Fase 10.1 — [imagemCache] carrega o logo de cada IA (memória/disco/rede)
 * de forma assíncrona em [escopo] (o lifecycleScope da Activity dona da lista).
 */
class IAAdapter(
    private val escopo: CoroutineScope,
    private val imagemCache: ImagemCache,
    private val aoClicar: (IA) -> Unit,
    private val aoAlternarFavorito: (IA) -> Unit
) : ListAdapter<IA, IAAdapter.IAViewHolder>(DIFF_CALLBACK) {

    /** Fase 7.1 — IDs atualmente favoritados; usado só para desenhar o ícone da estrela. */
    private var favoritos: Set<String> = emptySet()

    /** Atualiza o conjunto de favoritos exibido e força o redesenho dos ícones. */
    fun atualizarFavoritos(novosFavoritos: Set<String>) {
        favoritos = novosFavoritos
        notifyDataSetChanged()
    }

    inner class IAViewHolder(val binding: ItemIaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IAViewHolder {
        val binding = ItemIaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IAViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IAViewHolder, position: Int) {
        val ia = getItem(position)

        holder.binding.txtNomeItem.text = ia.nome
        holder.binding.txtDescricaoItem.text = ia.descricao
        // Fase 26 — categorias dinâmicas (novas, fora do enum fixo) também são
        // exibidas com rótulo legível via [CategoriaDinamica.rotulo].
        holder.binding.txtCategoriasItem.text = ia.categorias
            .take(3)
            .joinToString(" · ") { CategoriaDinamica.rotulo(it) }

        holder.binding.btnFavoritoItem.setImageResource(
            if (favoritos.contains(ia.id)) com.aibrain.app.R.drawable.ic_star_filled
            else com.aibrain.app.R.drawable.ic_star_outline
        )
        holder.binding.btnFavoritoItem.setOnClickListener { aoAlternarFavorito(ia) }

        holder.binding.root.setOnClickListener { aoClicar(ia) }

        carregarLogo(ia, holder.binding.imgLogoItem)
    }

    /** Fase 10.1 — carrega o logo via cache; protege contra recycling com a tag do ImageView. */
    private fun carregarLogo(ia: IA, imageView: ImageView) {
        imageView.tag = ia.logo
        imageView.setImageResource(com.aibrain.app.R.drawable.ic_image_placeholder)
        escopo.launch {
            val bitmap = imagemCache.carregar(ia.logo, ImagemCache.TAMANHO_ITEM_DP)
            if (bitmap != null && imageView.tag == ia.logo) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<IA>() {
            override fun areItemsTheSame(oldItem: IA, newItem: IA) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: IA, newItem: IA) = oldItem == newItem
        }
    }
}
