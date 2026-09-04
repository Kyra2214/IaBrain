package com.aibrain.app.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aibrain.app.data.PublicApiUserStateRepository
import com.aibrain.app.model.ApiAuthentication
import com.aibrain.app.model.ApiEndpoint
import com.aibrain.app.model.ApiSource
import com.aibrain.app.model.PublicApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicApiCatalogRepositoryTest {
    private lateinit var context: Context
    private lateinit var catalog: PublicApiCatalogRepository
    private lateinit var userState: PublicApiUserStateRepository

    @Before
    fun resetState() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteFile(PublicApiCatalogRepository.FILE_NAME)
        context.getSharedPreferences("public_api_user_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        catalog = PublicApiCatalogRepository(context)
        userState = PublicApiUserStateRepository(context)
    }

    @Test
    fun incrementalMergeDeduplicatesAndPreservesUserState() = runBlocking {
        val official = api(
            id = "official",
            name = "Canonical Books",
            description = "Official description",
            baseUrl = "https://api.example.com",
            documentationUrl = "https://docs.example.com",
            source = ApiSource.OFFICIAL_DOCS,
            authentication = ApiAuthentication.OAUTH,
            endpoints = listOf(ApiEndpoint("GET", "/books"))
        )
        catalog.merge(listOf(official))
        userState.toggleFavorite(official.id)
        userState.registerAccess(official.id)

        val directory = api(
            id = "directory",
            name = "Stale Books",
            description = "Directory description",
            baseUrl = "https://api.example.com/",
            documentationUrl = "https://directory.example.com",
            source = ApiSource.PUBLIC_APIS_IO,
            authentication = ApiAuthentication.UNKNOWN,
            endpoints = listOf(ApiEndpoint("POST", "/books"))
        )
        val added = api(
            id = "weather",
            name = "Weather API",
            baseUrl = "https://weather.example.com",
            source = ApiSource.PUBLIC_APIS_IO
        )

        val result = catalog.merge(listOf(directory, added))
        val loaded = catalog.load()
        val merged = loaded.single { it.id == official.id }

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(2, loaded.size)
        assertEquals("Canonical Books", merged.name)
        assertEquals("Official description", merged.description)
        assertEquals(ApiAuthentication.OAUTH, merged.authentication)
        assertEquals(listOf("GET", "POST"), merged.endpoints.map { it.normalizedMethod }.sorted())
        assertTrue(userState.isFavorite(official.id))
        assertEquals(listOf(official.id), userState.history())
    }

    private fun api(
        id: String,
        name: String,
        description: String = "Description",
        baseUrl: String,
        documentationUrl: String? = null,
        source: ApiSource,
        authentication: ApiAuthentication = ApiAuthentication.NONE,
        endpoints: List<ApiEndpoint> = emptyList()
    ) = PublicApi(
        id = id,
        name = name,
        description = description,
        category = "Development",
        baseUrl = baseUrl,
        documentationUrl = documentationUrl,
        source = source,
        authentication = authentication,
        endpoints = endpoints
    )
}
