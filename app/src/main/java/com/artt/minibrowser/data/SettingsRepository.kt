package com.artt.minibrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.artt.minibrowser.engine.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Prefs(
    val searchEngine: SearchEngine = SearchEngine.YANDEX,
    val theme: Int = 0,          // 0 система, 1 светлая, 2 тёмная
    val adblockEnabled: Boolean = true,
    val homepage: String = "",
)

class SettingsRepository(private val context: Context) {
    private object K {
        val engine = stringPreferencesKey("search_engine")
        val theme = intPreferencesKey("theme")
        val adblock = booleanPreferencesKey("adblock_enabled")
        val homepage = stringPreferencesKey("homepage")
    }

    val prefs: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            searchEngine = p[K.engine]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() }
                ?: SearchEngine.YANDEX,
            theme = p[K.theme] ?: 0,
            adblockEnabled = p[K.adblock] ?: true,
            homepage = p[K.homepage] ?: "",
        )
    }

    suspend fun setSearchEngine(e: SearchEngine) = context.dataStore.edit { it[K.engine] = e.name }
    suspend fun setTheme(t: Int) = context.dataStore.edit { it[K.theme] = t }
    suspend fun setAdblock(b: Boolean) = context.dataStore.edit { it[K.adblock] = b }
    suspend fun setHomepage(u: String) = context.dataStore.edit { it[K.homepage] = u }
}
