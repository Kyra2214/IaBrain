package com.aibrain.app.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.databinding.ItemAbaNavegadorBinding
import com.aibrain.app.databinding.ItemAbaNovaBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fase 21.6 — barra/lista de abas do navegador interno.
 * Exibe todas as [AbaNavegador] abertas (título + ícone da IA), destaca a
 * aba ativa, permite tocar para trocar de aba, fechar cada aba (botão "x")
 * e criar uma nova aba (item fixo "Nova Aba" ao final da lista).
 *
 * ListAdapter + DiffUtil, mesmo padrão do [com.aibrain.app.view.IAAdapter]
 * (Fase 6.1). Persistência do estado ao trocar de aba (scroll, histórico,
 * sessão — Fase 21.7) e a integração desta barra dentro da
 * `BrowserActivity`/`activity_browser.xml` (Fase 21.8) ficam fora deste
 * submódulo — o adapter é entregue funcional e autocontido.
 *
 * Fase 21.9 — toque longo no card da aba abre um menu de contexto
 * (`menu_aba_navegador`) com fixar/desafixar (pin), atualizar e página
 * inicial da IA; indicador visual de pin (`imgAbaFixada`) some/aparece
 * conforme `AbaNavegador.fixada`.
 */
class BrowserAdapter(
    private val escopo: CoroutineScope,
    private val imagemCache: ImagemCache,
    private val aoSelecionarAba: (AbaNavegador) -> Unit,
    private val aoFecharAba: (AbaNavegador) -> Unit,
    private val aoNovaAba: () -> Unit,
    private val aoFixarAba: (AbaNavegador) -> Unit = {},
    private val aoAtualizarAba: (AbaNavegador) -> Unit = {},
    private val aoAbrirPaginaInicialAba: (AbaNavegador) -> Unit = {}
) : ListAdapter<BrowserAdapter.ItemBarra, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    /** Item da barra: cada aba aberta, ou o item fixo de criar uma nova aba. */
    sealed class ItemBarra {
        data class Aba(val aba: AbaNavegador, val ativa: Boolean) : ItemBarra()
        object NovaAba : ItemBarra()
    }

    /** Reconstrói a lista a partir das abas atuais e de qual delas está ativa. */
    fun atualizar(abas: List<AbaNavegador>, idAbaAtiva: String?) {
        val itens = abas.map { ItemBarra.Aba(it, it.id == idAbaAtiva) } + ItemBarra.NovaAba
        submitList(itens)
    }

    inner class AbaViewHolder(val binding: ItemAbaNavegadorBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class NovaAbaViewHolder(val binding: ItemAbaNovaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ItemBarra.Aba -> TIPO_ABA
        is ItemBarra.NovaAba -> TIPO_NOVA_ABA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TIPO_ABA) {
            val binding = ItemAbaNavegadorBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            AbaViewHolder(binding)
        } else {
            val binding = ItemAbaNovaBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            NovaAbaViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ItemBarra.Aba -> bindAba(holder as AbaViewHolder, item)
            is ItemBarra.NovaAba -> bindNovaAba(holder as NovaAbaViewHolder)
        }
    }

    private fun bindAba(holder: AbaViewHolder, item: ItemBarra.Aba) {
        val aba = item.aba
        holder.binding.txtAbaItem.text = aba.nomeIA
        holder.binding.imgAbaFixada.visibility = if (aba.fixada) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.cardAbaItem.setCardBackgroundColor(
            ContextCompat.getColor(
                holder.binding.root.context,
                if (item.ativa) com.aibrain.app.R.color.secondary
                else com.aibrain.app.R.color.primary_variant
            )
        )
        holder.binding.root.setOnClickListener { aoSelecionarAba(aba) }
        holder.binding.root.setOnLongClickListener {
            mostrarMenuContexto(holder.binding.root, aba)
            true
        }
        holder.binding.btnFecharAbaItem.setOnClickListener { aoFecharAba(aba) }
        carregarIcone(aba, holder)
    }

    /** Fase 21.9 — menu de contexto da aba: fixar/desafixar (pin), atualizar, página inicial da IA. */
    private fun mostrarMenuContexto(ancora: android.view.View, aba: AbaNavegador) {
        val popup = androidx.appcompat.widget.PopupMenu(ancora.context, ancora)
        popup.menuInflater.inflate(com.aibrain.app.R.menu.menu_aba_navegador, popup.menu)
        popup.menu.findItem(com.aibrain.app.R.id.menuFixarAba).setTitle(
            if (aba.fixada) com.aibrain.app.R.string.browser_desafixar_aba
            else com.aibrain.app.R.string.browser_fixar_aba
        )
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.aibrain.app.R.id.menuFixarAba -> aoFixarAba(aba)
                com.aibrain.app.R.id.menuAtualizarAba -> aoAtualizarAba(aba)
                com.aibrain.app.R.id.menuPaginaInicialAba -> aoAbrirPaginaInicialAba(aba)
            }
            true
        }
        popup.show()
    }

    private fun bindNovaAba(holder: NovaAbaViewHolder) {
        holder.binding.btnNovaAbaItem.setOnClickListener { aoNovaAba() }
    }

    /** Fase 10.1 — reaproveita o [ImagemCache] já usado pela lista principal de IAs. */
    private fun carregarIcone(aba: AbaNavegador, holder: AbaViewHolder) {
        val imageView = holder.binding.imgAbaItem
        imageView.tag = aba.iconeIA
        imageView.setImageResource(com.aibrain.app.R.drawable.ic_image_placeholder)
        escopo.launch {
            val bitmap = imagemCache.carregar(aba.iconeIA, TAMANHO_ICONE_ABA_DP)
            if (bitmap != null && imageView.tag == aba.iconeIA) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    companion object {
        private const val TIPO_ABA = 0
        private const val TIPO_NOVA_ABA = 1
        private const val TAMANHO_ICONE_ABA_DP = 20

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ItemBarra>() {
            override fun areItemsTheSame(oldItem: ItemBarra, newItem: ItemBarra): Boolean {
                if (oldItem is ItemBarra.NovaAba && newItem is ItemBarra.NovaAba) return true
                if (oldItem is ItemBarra.Aba && newItem is ItemBarra.Aba) {
                    return oldItem.aba.id == newItem.aba.id
                }
                return false
            }

            override fun areContentsTheSame(oldItem: ItemBarra, newItem: ItemBarra): Boolean =
                oldItem == newItem
        }
    }
}
