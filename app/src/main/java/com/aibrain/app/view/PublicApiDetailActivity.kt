package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.R
import com.aibrain.app.brain.ApiDiscoveryEngine
import com.aibrain.app.brain.ApiSecurityAnalyzer
import com.aibrain.app.data.PublicApiUserStateRepository
import com.aibrain.app.model.ApiAnalysis
import com.aibrain.app.model.PublicApi
import com.aibrain.app.navigation.GlobalNavigation
import com.aibrain.app.util.abrirUrlNoNavegador
import com.google.android.material.snackbar.Snackbar

class PublicApiDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var userState: PublicApiUserStateRepository
    private lateinit var api: PublicApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = intent.getParcelableExtra(EXTRA_API) ?: run { finish(); return }
        userState = PublicApiUserStateRepository(applicationContext)
        root = FrameLayout(this)
        setContentView(root)
        root.addView(buildContent(), FrameLayout.LayoutParams(-1, -1))
        GlobalNavigation.attach(this, root, GlobalNavigation.PUBLIC_APIS)
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(112))
        }
        content.addView(TextView(this).apply {
            text = api.name
            textSize = 28f
            setTextColor(getColor(R.color.on_surface))
        })
        content.addView(TextView(this).apply {
            text = "${api.category} · ${api.source.label} · ${api.status.label}"
            textSize = 14f
            setTextColor(getColor(R.color.on_surface_variant))
            setPadding(0, dp(4), 0, dp(12))
        })
        content.addView(TextView(this).apply {
            text = api.description.ifBlank { "Descrição não informada pela fonte." }
            textSize = 16f
            setPadding(0, 0, 0, dp(12))
        })
        val actionRow = LinearLayout(this).apply { gravity = Gravity.START }
        actionRow.addView(Button(this).apply {
            text = if (userState.isFavorite(api.id)) "★ Favorita" else "☆ Favoritar"
            setOnClickListener {
                val favorite = userState.toggleFavorite(api.id)
                text = if (favorite) "★ Favorita" else "☆ Favoritar"
                Snackbar.make(root, if (favorite) "API adicionada aos favoritos." else "API removida dos favoritos.", Snackbar.LENGTH_SHORT).show()
            }
        })
        api.documentationUrl?.let { url ->
            actionRow.addView(Button(this).apply {
                text = "Documentação"
                setOnClickListener { openUrl(url) }
            })
        }
        api.baseUrl?.takeIf { it != api.documentationUrl }?.let { url ->
            actionRow.addView(Button(this).apply {
                text = "Base URL"
                setOnClickListener { openUrl(url) }
            })
        }
        content.addView(actionRow)

        val analysis = ApiDiscoveryEngine().analyze(api)
        content.addView(section("Análise técnica", technicalAnalysis(analysis)))
        content.addView(section("Segurança", securityAnalysis(analysis)))
        content.addView(section("Endpoints conhecidos", endpointsText()))
        return scroll.apply { addView(content) }
    }

    private fun technicalAnalysis(analysis: ApiAnalysis): String = buildString {
        append("Revisão: ${analysis.review.label}\n")
        append("Score determinístico: ${analysis.score}/100\n")
        append("Contrato: ${analysis.contract.summary}\n")
        append("Resolução: ${analysis.contract.resolution}\n")
        if (analysis.contract.methods.isNotEmpty()) append("Métodos: ${analysis.contract.methods.joinToString(", ")}\n")
        if (analysis.contract.unknownFields.isNotEmpty()) append("UNKNOWN: ${analysis.contract.unknownFields.joinToString(", ")}")
    }

    private fun securityAnalysis(analysis: ApiAnalysis): String = buildString {
        append("Score de segurança: ${analysis.security.score}/100\n")
        if (analysis.security.findings.isEmpty()) append("Nenhum achado registrado.")
        else analysis.security.findings.forEach { finding -> append("[${finding.severity.label}] ${finding.message}\n") }
        append("Redirecionamentos: não verificados automaticamente.")
    }

    private fun endpointsText(): String = if (api.endpoints.isEmpty()) {
        "UNKNOWN — a fonte não forneceu endpoints conhecidos."
    } else {
        api.endpoints.joinToString("\n") { endpoint ->
            "${endpoint.normalizedMethod} ${endpoint.normalizedPath}" + (endpoint.summary?.let { " — $it" } ?: "")
        }
    }

    private fun section(title: String, body: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(8))
        addView(TextView(this@PublicApiDetailActivity).apply {
            text = title
            textSize = 19f
            setTextColor(getColor(R.color.primary))
        })
        addView(TextView(this@PublicApiDetailActivity).apply {
            text = body
            textSize = 14f
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun openUrl(url: String) {
        val report = ApiSecurityAnalyzer.analyze(api.copy(baseUrl = url, documentationUrl = url))
        if (report.blockers.isNotEmpty()) {
            Snackbar.make(root, "A URL foi bloqueada pela análise de segurança.", Snackbar.LENGTH_LONG).show()
            return
        }
        abrirUrlNoNavegador(this, url)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_API = "extra_public_api"

        fun intent(context: Context, api: PublicApi): Intent = Intent(context, PublicApiDetailActivity::class.java)
            .putExtra(EXTRA_API, api)
    }
}
