package com.aibrain.app.data

import android.content.Context

/** Favorites and history are user data, never serialized into the public API catalog. */
class PublicApiUserStateRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun favorites(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    fun isFavorite(id: String): Boolean = id in favorites()

    fun toggleFavorite(id: String): Boolean {
        val next = favorites().toMutableSet()
        val favorite = next.add(id)
        if (!favorite) next.remove(id)
        preferences.edit().putStringSet(KEY_FAVORITES, next).apply()
        return favorite
    }

    fun registerAccess(id: String) {
        val current = history().toMutableList()
        current.remove(id)
        current.add(0, id)
        preferences.edit().putString(KEY_HISTORY, current.take(MAX_HISTORY).joinToString("\n")).apply()
    }

    fun history(): List<String> = preferences.getString(KEY_HISTORY, "")
        .orEmpty()
        .split('\n')
        .map(String::trim)
        .filter(String::isNotBlank)

    fun removeUserState(id: String) {
        val nextFavorites = favorites().toMutableSet().also { it.remove(id) }
        val nextHistory = history().filterNot { it == id }
        preferences.edit()
            .putStringSet(KEY_FAVORITES, nextFavorites)
            .putString(KEY_HISTORY, nextHistory.joinToString("\n"))
            .apply()
    }

    companion object {
        private const val FILE_NAME = "public_api_user_state"
        private const val KEY_FAVORITES = "favorite_api_ids"
        private const val KEY_HISTORY = "api_history_ids"
        private const val MAX_HISTORY = 50
    }
}
