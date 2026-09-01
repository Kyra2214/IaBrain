package com.aibrain.app.navigation

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.aibrain.app.MainActivity
import com.aibrain.app.R
import com.aibrain.app.browser.BrowserActivity
import com.aibrain.app.view.AIBrainActivity
import com.aibrain.app.view.PromptsComandosActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object GlobalNavigation {
    const val CHAT = 0
    const val NAVEGADOR = 1
    const val BRAIN = 2
    const val PROMPTS = 3

    fun attach(activity: Activity, container: ViewGroup, selected: Int) {
        val navigation = BottomNavigationView(activity).apply {
            id = R.id.globalNavigation
            inflateMenu(R.menu.menu_navegacao_global)
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            itemIconTintList = null
            setOnItemSelectedListener { item ->
                val destination = when (item.itemId) {
                    R.id.nav_chat -> CHAT
                    R.id.nav_navegador -> NAVEGADOR
                    R.id.nav_brain -> BRAIN
                    R.id.nav_prompts -> PROMPTS
                    else -> selected
                }
                if (destination != selected) open(activity, destination)
                true
            }
            menu.getItem(selected).isChecked = true
        }
        val params = when (container) {
            is ConstraintLayout -> ConstraintLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            is FrameLayout -> FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
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
            else -> PromptsComandosActivity::class.java
        }
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
