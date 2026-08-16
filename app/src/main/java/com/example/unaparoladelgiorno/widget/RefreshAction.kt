package com.example.unaparoladelgiorno.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.unaparoladelgiorno.data.WordRepository

/** Runs when the user taps the small refresh icon inside the widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WordRepository(context).refresh()
        WordWidget().update(context, glanceId)
    }
}
