package com.example.unaparoladelgiorno.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.unaparoladelgiorno.work.WorkScheduler

class WordWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WordWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget instance added to a home screen: schedule the daily
        // background refresh and fetch immediately so it isn't empty.
        WorkScheduler.scheduleDailyRefresh(context)
        WorkScheduler.enqueueImmediateRefresh(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkScheduler.enqueueImmediateRefresh(context)
    }
}
