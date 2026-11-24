package com.example.unaparoladelgiorno.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "widget_settings")

/**
 * User-facing widget settings. Currently only the text size multiplier, chosen
 * from the app before (or after) the widget is added; the widget reads it on
 * every redraw, so changing it from the app updates existing widgets too.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_SCALE = floatPreferencesKey("font_scale")
    }

    val fontScaleFlow: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        (prefs[Keys.FONT_SCALE] ?: DEFAULT_FONT_SCALE).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }

    suspend fun getFontScale(): Float = fontScaleFlow.first()

    suspend fun setFontScale(scale: Float) {
        context.settingsDataStore.edit {
            it[Keys.FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
    }

    companion object {
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.8f
        const val DEFAULT_FONT_SCALE = 1.0f
    }
}
