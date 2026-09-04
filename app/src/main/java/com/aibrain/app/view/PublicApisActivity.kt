package com.aibrain.app.view

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.R
import com.aibrain.app.data.PublicApiUserStateRepository
import com.aibrain.app.model.PublicApi
import com.aibrain.app.navigation.GlobalNavigation
import com.aibrain.app.brain.ApiDiscoveryEngine
import com.aibrain.app.brain.ApiDiscoveryQuery
import com.aibrain.app.repository.PublicApiCatalogRepository
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Offline-first catalog UI. Network is used only when the user requests discovery/refresh. */
class PublicApisActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var search: EditText
    private lateinit var category: Spinner
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: PublicApiAdapter
    private lateinit var catalogRepository: PublicApiCatalogRepository
    private lateinit var userState: PublicApiUserStateRepository
    private val engine = ApiDiscoveryEngine()
    private var localCatalog: List<PublicApi> = emptyList()
    private var displayed: List<PublicApi> = emptyList()
    private var browsingDiscovery = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalogRepository = PublicApiCatalogRepository(applicationContext)
        userState = PublicApiUserStateRepository(applicationContext)
        root = FrameLayout(this)
        setContentView(root)
        root.addView(buildContent(), FrameLayout.LayoutParams(-1, -1))
        GlobalNavigation.attach(this, root, GlobalNavigation.PUBLIC_APIS)
        loadLocalCatalog()
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(112))
        }
        content.addView(TextView(this).apply {
            id = R.id.txtPublicApisTitle
            text = "Public APIs"
            textSize = 28f
            setTextColor(getColor(R.color.on_surface))
        })
        content.addView(TextView(this).apply {
            text = "Descubra, analise e mantenha um catálogo local de APIs públicas."
            textSize = 15f
            setTextColor(getColor(R.color.on_surface_variant))
            setPadding(0, dp(4), 0, dp(12))
        })
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        search = EditText(this).apply {
            hint = "Pesquisar APIs"
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> searchRemote(); true }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(Button(this).apply {
            text = "Pesquisar"
            setOnClickListener { searchRemote() }
        })
        content.addView(searchRow)

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        category = Spinner(this)
        filterRow.addView(category, LinearLayout.LayoutParams(0, -2, 1f))
        filterRow.addView(Button(this).apply {
            text = "Catálogo local"
            setOnClickListener {
                browsingDiscovery = false
                render(localCatalog)
            }
        })
        content.addView(filterRow)

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actionRow.addView(Button(this).apply {
            text = "Atualizar catálogo"
            contentDescription = "Descobrir e atualizar o catálogo de APIs"
            setOnClickListener { updateCatalog() }
        })
        progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        actionRow.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(8) })
        content.addView(actionRow)

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.on_surface_variant))
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(status)
        adapter = PublicApiAdapter(
            onDetails = { api ->
                userState.registerAccess(api.id)
                startActivity(PublicApiDetailActivity.intent(this, api))
            },
            onAddOrRemove = ::addOrRemove,
            onFavorite = ::toggleFavorite
        )
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PublicApisActivity)
            adapter = this@PublicApisActivity.adapter
            isNestedScrollingEnabled = false
        }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        return content
    }

    private fun loadLocalCatalog() {
        lifecycleScope.launch {
            localCatalog = catalogRepository.load()
            updateCategories()
            render(localCatalog)
        }
    }

    private fun searchRemote() {
        val query = currentQuery()
        browsingDiscovery = true
        setBusy(true)
        lifecycleScope.launch {
            val snapshot = engine.discover(query)
            setBusy(false)
            displayed = snapshot.candidates
            render(displayed)
            if (snapshot.sourceErrors.isNotEmpty()) {
                showMessage("Fonte indisponível; o catálogo local continua disponível.")
            }
            status.text = "${displayed.size} resultado(s) descoberto(s) · ${ApiDiscoveryQuerySummary(query)}"
        }
    }

    private fun updateCatalog() {
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching { engine.updateCatalog(catalogRepository) }
            setBusy(false)
            result.onSuccess { update ->
                localCatalog = catalogRepository.load()
                browsingDiscovery = false
                updateCategories()
                render(localCatalog)
                status.text = "Catálogo atualizado: ${update.merge.added} nova(s), ${update.merge.updated} atualizada(s), ${update.reviewRequired} em revisão."
                if (update.sourceErrors.isNotEmpty()) showMessage("Atualização parcial: a fonte não respondeu.")
            }.onFailure { error -> showMessage(error.message ?: "Não foi possível atualizar o catálogo.") }
        }
    }

    private fun currentQuery(): ApiDiscoveryQuery = ApiDiscoveryQuery(
        text = search.text?.toString().orEmpty(),
        category = category.selectedItem?.toString()?.takeIf { it != "Todas as categorias" }
    )

    private fun render(apis: List<PublicApi>) {
        displayed = if (browsingDiscovery) apis else engine.suggest(currentQuery(), localCatalog).map { it.api }
        adapter.submit(displayed, localCatalog.map { it.id }.toSet(), userState.favorites())
        if (!browsingDiscovery) status.text = "${displayed.size} API(s) no catálogo local · funciona offline"
        if (displayed.isEmpty()) status.text = "Nenhuma API encontrada. Pesquise uma fonte ou adicione APIs ao catálogo."
    }

    private fun updateCategories() {
        val categories = listOf("Todas as categorias") + (DEFAULT_CATEGORIES + localCatalog.map { it.category })
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        category.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
    }

    private fun addOrRemove(api: PublicApi, local: Boolean) {
        lifecycleScope.launch {
            if (local) {
                catalogRepository.remove(api.id)
                userState.removeUserState(api.id)
                localCatalog = catalogRepository.load()
                updateCategories()
                render(if (browsingDiscovery) displayed else localCatalog)
                showMessage("API removida do catálogo local.")
            } else {
                val added = catalogRepository.add(api)
                localCatalog = catalogRepository.load()
                updateCategories()
                adapter.submit(displayed, localCatalog.map { it.id }.toSet(), userState.favorites())
                showMessage(if (added) "API adicionada ao catálogo local." else "A API já está no catálogo.")
            }
        }
    }

    private fun toggleFavorite(api: PublicApi) {
        val favorite = userState.toggleFavorite(api.id)
        adapter.submit(displayed, localCatalog.map { it.id }.toSet(), userState.favorites())
        showMessage(if (favorite) "API adicionada aos favoritos." else "API removida dos favoritos.")
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun showMessage(message: String) = Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val DEFAULT_CATEGORIES = listOf(
            "Development", "Data Access", "Finance", "Cryptocurrency", "Games & Comics", "Geocoding",
            "Open Data", "Transportation", "Music", "Media", "Social", "Sports & Fitness", "Weather",
            "Shopping", "Food & Drink", "Health", "Calendar", "Government", "Video", "Science", "Jobs",
            "Animals", "Machine Learning", "Documents & Productivity", "Security", "Analytics", "News",
            "Art & Design", "IoT", "Environment", "Business", "Books", "Vehicle", "Utilities"
        )

        private fun ApiDiscoveryQuerySummary(query: ApiDiscoveryQuery): String = listOfNotNull(
            query.text.takeIf { it.isNotBlank() }?.let { "busca: $it" },
            query.category?.let { "categoria: $it" }
        ).ifEmpty { listOf("todas") }.joinToString(" · ")
    }
}
