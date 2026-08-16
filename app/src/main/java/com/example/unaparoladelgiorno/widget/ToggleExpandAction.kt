package com.example.unaparoladelgiorno.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val current = this[IsExpandedKey] ?: false
                this[IsExpandedKey] = !current
            }
        }
        WordWidget().update(context, glanceId)
    }

    companion object {
        val IsExpandedKey = booleanPreferencesKey("is_expanded")
    }
}
