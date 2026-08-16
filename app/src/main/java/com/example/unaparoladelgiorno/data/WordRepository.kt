package com.example.unaparoladelgiorno.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.wordDataStore by preferencesDataStore(name = "word_of_day_store")

/**
 * Single source of truth for the widget: keeps the most recently scraped
 * [WordOfDay] cached on disk so the widget can render instantly without
 * hitting the network on every redraw, and only re-scrapes when explicitly
 * asked to (manual refresh button, daily background worker).
 */
class WordRepository(private val context: Context) {

    private object Keys {
        val WORD_JSON = stringPreferencesKey("word_json")
    }

    val wordFlow: Flow<WordOfDay?> = context.wordDataStore.data.map { prefs ->
        prefs[Keys.WORD_JSON]?.let { json ->
            runCatching { WordOfDay.fromJson(json) }.getOrNull()
        }
    }

    suspend fun getCached(): WordOfDay? = wordFlow.first()

    /** Scrapes the site now and overwrites the cache on success. */
    suspend fun refresh(): Result<WordOfDay> = runCatching {
        val fresh = WordScraper.fetchTodayWord()
        context.wordDataStore.edit { it[Keys.WORD_JSON] = fresh.toJson() }
        fresh
    }

    /**
     * Refreshes only if there is nothing cached yet or the cached entry is
     * from a previous calendar day, then returns whatever is best available
     * (fresh data, or the stale cache if the network call failed).
     */
    suspend fun refreshIfStale(): WordOfDay? {
        val cached = getCached()
        val stale = cached == null || isDifferentDay(cached.fetchedAtEpochMillis)
        if (!stale) return cached
        return refresh().getOrNull() ?: cached
    }

    private fun isDifferentDay(epochMillis: Long): Boolean {
        val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val now = Calendar.getInstance()
        return then.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ||
            then.get(Calendar.YEAR) != now.get(Calendar.YEAR)
    }
}
