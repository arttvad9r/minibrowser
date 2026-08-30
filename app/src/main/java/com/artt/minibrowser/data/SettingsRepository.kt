package com.artt.minibrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.normalizeTranslationTarget
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Prefs(
    val searchEngine: SearchEngine = SearchEngine.YANDEX,
    val theme: Int = 0,          // 0 система, 1 светлая, 2 тёмная
    val adblockEnabled: Boolean = true,
    val votEnabled: Boolean = true,
    val translateTarget: String = "ru",
)

class SettingsRepository(context: Context) {
    // SettingsViewModel can survive Activity recreation; never retain an Activity context here.
    private val context = context.applicationContext

    private object K {
        val engine = stringPreferencesKey("search_engine")
        val theme = intPreferencesKey("theme")
        val adblock = booleanPreferencesKey("adblock_enabled")
        val vot = booleanPreferencesKey("vot_enabled")
        val translate = stringPreferencesKey("translate_target")
    }

    val prefs: Flow<Prefs> = this.context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { p ->
            Prefs(
                searchEngine = p[K.engine]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() }
                    ?: SearchEngine.YANDEX,
                theme = normalizeThemePreference(p[K.theme]),
                adblockEnabled = p[K.adblock] ?: true,
                votEnabled = p[K.vot] ?: true,
                translateTarget = normalizeTranslationTarget(p[K.translate]) ?: "ru",
            )
        }

    suspend fun setSearchEngine(e: SearchEngine) = context.dataStore.edit { it[K.engine] = e.name }
    suspend fun setTheme(t: Int) = context.dataStore.edit { it[K.theme] = normalizeThemePreference(t) }
    suspend fun setAdblock(b: Boolean) = context.dataStore.edit { it[K.adblock] = b }
    suspend fun setVot(enabled: Boolean) = context.dataStore.edit { it[K.vot] = enabled }
    suspend fun setTranslateTarget(lang: String) = context.dataStore.edit {
        it[K.translate] = normalizeTranslationTarget(lang) ?: "ru"
    }
}
