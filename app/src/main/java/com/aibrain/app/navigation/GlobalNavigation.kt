package com.aibrain.app.navigation

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.aibrain.app.MainActivity
import com.aibrain.app.R
import com.aibrain.app.browser.BrowserActivity
import com.aibrain.app.view.AIBrainActivity
import com.aibrain.app.view.PromptsComandosActivity
import com.aibrain.app.view.ProjetosActivity
import com.aibrain.app.view.PublicApisActivity

/** Shared six-area navigation; a custom bar avoids Material's five-item limit. */
object GlobalNavigation {
    const val CHAT = 0
    const val NAVEGADOR = 1
    const val BRAIN = 2
    const val PROMPTS = 3
    const val PROJETOS = 4
    const val PUBLIC_APIS = 5

    private data class Destination(val id: Int, val title: String, val icon: Int, val code: Int)

    fun attach(activity: Activity, container: ViewGroup, selected: Int) {
        val destinations = listOf(
            Destination(R.id.nav_chat, "Chat", R.drawable.ic_assistente_ia, CHAT),
            Destination(R.id.nav_navegador, "Navegador", R.drawable.ic_open_external, NAVEGADOR),
            Destination(R.id.nav_brain, "Brain", R.drawable.ic_ai_brain, BRAIN),
            Destination(R.id.nav_prompts, "Prompts", R.drawable.ic_criador_prompts, PROMPTS),
            Destination(R.id.nav_projects, "Projetos", R.drawable.ic_projects, PROJETOS),
            Destination(R.id.nav_public_apis, "Public APIs", R.drawable.ic_public_apis, PUBLIC_APIS)
        )
        val navigation = LinearLayout(activity).apply {
            id = R.id.globalNavigation
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = destinations.size.toFloat()
            background = activity.getDrawable(R.drawable.bg_bottom_navigation)
            elevation = activity.resources.getDimension(R.dimen.space_sm)
            minimumHeight = activity.resources.getDimensionPixelSize(R.dimen.global_navigation_height)
            setPadding(0, activity.resources.getDimensionPixelSize(R.dimen.space_xs), 0, activity.resources.getDimensionPixelSize(R.dimen.space_xs))
        }
        destinations.forEach { destination ->
            navigation.addView(TextView(activity).apply {
                id = destination.id
                text = destination.title
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 2
                isClickable = true
                isFocusable = true
                isSelected = destination.code == selected
                setTextColor(activity.getColorStateList(R.color.nav_item_colors))
                setCompoundDrawablesWithIntrinsicBounds(0, destination.icon, 0, 0)
                compoundDrawablePadding = activity.resources.getDimensionPixelSize(R.dimen.space_xs)
                setPadding(
                    activity.resources.getDimensionPixelSize(R.dimen.space_xs),
                    activity.resources.getDimensionPixelSize(R.dimen.space_xs),
                    activity.resources.getDimensionPixelSize(R.dimen.space_xs),
                    activity.resources.getDimensionPixelSize(R.dimen.space_xs)
                )
                contentDescription = destination.title
                setOnClickListener {
                    if (destination.code != selected) open(activity, destination.code)
                }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val params = when (container) {
            is ConstraintLayout -> ConstraintLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            is FrameLayout -> FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
            else -> ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        container.addView(navigation, params)
    }

    private fun open(activity: Activity, destination: Int) {
        val target = when (destination) {
            CHAT -> AIBrainActivity::class.java
            NAVEGADOR -> BrowserActivity::class.java
            BRAIN -> MainActivity::class.java
            PROJETOS -> ProjetosActivity::class.java
            PUBLIC_APIS -> PublicApisActivity::class.java
            else -> PromptsComandosActivity::class.java
        }
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
