package com.aibrain.app.view

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.R
import com.aibrain.app.model.PublicApi
import com.google.android.material.card.MaterialCardView

class PublicApiAdapter(
    private val onDetails: (PublicApi) -> Unit,
    private val onAddOrRemove: (PublicApi, Boolean) -> Unit,
    private val onFavorite: (PublicApi) -> Unit
) : RecyclerView.Adapter<PublicApiAdapter.ApiViewHolder>() {
    private var items: List<PublicApi> = emptyList()
    private var localIds: Set<String> = emptySet()
    private var favoriteIds: Set<String> = emptySet()

    fun submit(items: List<PublicApi>, localIds: Set<String>, favoriteIds: Set<String>) {
        this.items = items
        this.localIds = localIds
        this.favoriteIds = favoriteIds
        notifyDataSetChanged()
    }

    class ApiViewHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApiViewHolder {
        val card = MaterialCardView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).also { params ->
                val margin = parent.context.resources.getDimensionPixelSize(R.dimen.space_sm)
                params.setMargins(0, margin, 0, margin)
            }
            radius = parent.context.resources.getDimension(R.dimen.shape_medium)
            cardElevation = parent.context.resources.getDimension(R.dimen.space_xs)
            setContentPadding(
                parent.context.resources.getDimensionPixelSize(R.dimen.space_md),
                parent.context.resources.getDimensionPixelSize(R.dimen.space_md),
                parent.context.resources.getDimensionPixelSize(R.dimen.space_md),
                parent.context.resources.getDimensionPixelSize(R.dimen.space_md)
            )
        }
        return ApiViewHolder(card)
    }

    override fun onBindViewHolder(holder: ApiViewHolder, position: Int) {
        val api = items[position]
        val context = holder.card.context
        val local = api.id in localIds
        val favorite = api.id in favoriteIds
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        content.addView(TextView(context).apply {
            text = api.name
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(context).apply {
            text = "${api.category} · ${api.source.label} · ${api.status.label}"
            textSize = 12f
            setTextColor(context.getColor(R.color.on_surface_variant))
            setPadding(0, 4, 0, 8)
        })
        content.addView(TextView(context).apply {
            text = api.description.ifBlank { "Descrição não informada pela fonte." }
            textSize = 14f
            maxLines = 4
        })
        val metadata = listOfNotNull(
            api.authentication.label.takeIf { it.isNotBlank() },
            api.https?.let { if (it) "HTTPS" else "HTTP" },
            api.endpoints.takeIf { it.isNotEmpty() }?.let { "${it.size} endpoint(s)" },
            api.allSources.takeIf { it.size > 1 }?.let { "${it.size} fontes" }
        )
        content.addView(TextView(context).apply {
            text = metadata.joinToString(" · ")
            textSize = 12f
            setTextColor(context.getColor(R.color.on_surface_variant))
            setPadding(0, 8, 0, 4)
        })
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttons.addView(Button(context).apply {
            text = "Detalhes"
            setOnClickListener { onDetails(api) }
        })
        buttons.addView(Button(context).apply {
            text = if (local) "Remover" else "Adicionar"
            contentDescription = if (local) "Remover ${api.name} do catálogo" else "Adicionar ${api.name} ao catálogo"
            setOnClickListener { onAddOrRemove(api, local) }
        })
        buttons.addView(Button(context).apply {
            text = if (favorite) "★" else "☆"
            contentDescription = if (favorite) "Remover ${api.name} dos favoritos" else "Favoritar ${api.name}"
            setOnClickListener { onFavorite(api) }
        })
        content.addView(buttons)
        holder.card.removeAllViews()
        holder.card.addView(content)
    }

    override fun getItemCount(): Int = items.size
}
