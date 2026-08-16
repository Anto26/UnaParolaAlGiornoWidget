package com.example.unaparoladelgiorno.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import com.example.unaparoladelgiorno.R
import com.example.unaparoladelgiorno.data.WordOfDay
import com.example.unaparoladelgiorno.data.WordRepository

/**
 * The widget the user actually adds to their home screen. It never loads
 * unaparolaalgiorno.it in a WebView - it only ever renders the [WordOfDay]
 * that [WordRepository] has cached from the last scrape.
 */
class WordWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Three breakpoints: a small square, a wide "row", and a large card.
    // Glance picks whichever fits and re-composes when the user resizes it.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 120.dp),
            DpSize(250.dp, 250.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Rendering only ever reads the cache - it is fast and offline-safe.
        // Actual network refreshes happen in RefreshAction and the worker.
        val word = WordRepository(context).getCached()
        provideContent {
            WordWidgetContent(word = word)
        }
    }
}

@Composable
private fun WordWidgetContent(word: WordOfDay?) {
    val size = LocalSize.current
    val isCompact = size.height < 160.dp
    val isNarrow = size.width < 160.dp
    val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
    val isExpanded = prefs[ToggleExpandAction.IsExpandedKey] ?: false

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_gradient_background),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize()
        )
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(if (isNarrow) 10.dp else 16.dp)
        ) {
            if (word == null) {
                EmptyState()
            } else if (isExpanded && word.fullText != null) {
                ExpandedWordContent(word)
            } else {
                CompactWordContent(word, isCompact, isNarrow)
            }
        }
    }
}

@Composable
private fun CompactWordContent(word: WordOfDay, isCompact: Boolean, isNarrow: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "PAROLA DEL GIORNO",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFF8D7D7)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openWordIntent(word.sourceUrl)))
            )
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Aggiorna la parola del giorno",
                modifier = GlanceModifier
                    .size(18.dp)
                    .clickable(actionRunCallback<RefreshAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = word.word,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = if (isNarrow) 22.sp else 30.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.clickable(
                actionStartActivity(openWordIntent(word.sourceUrl))
            )
        )

        if (!isCompact) {
            word.syllabication?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF8D7D7)),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Text(
            text = word.definition,
            maxLines = if (isCompact) 2 else 3,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 14.sp
            )
        )

        if (!isCompact) {
            word.example?.let { example ->
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "\u00AB$example\u00BB",
                    maxLines = 2,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFFE9C7)),
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                word.etymology?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFEFC6C6)),
                            fontSize = 11.sp
                        )
                    )
                }
                if (word.fullText != null) {
                    Text(
                        text = "Mostra tutto",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<ToggleExpandAction>())
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedWordContent(word: WordOfDay) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = word.word.uppercase(),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFF8D7D7)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "Mostra meno",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleExpandAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            item {
                AndroidRemoteViews(
                    remoteViews = RemoteViews(
                        "com.example.unaparoladelgiorno",
                        R.layout.widget_html_text
                    ).apply {
                        val spannedText = Html.fromHtml(
                            word.fullText ?: "",
                            Html.FROM_HTML_MODE_COMPACT
                        )
                        setTextViewText(R.id.html_text_view, spannedText)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "Parola del giorno",
            style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Tocca per caricare",
            style = TextStyle(color = ColorProvider(Color(0xFFF8D7D7)), fontSize = 12.sp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Carica la parola del giorno",
            modifier = GlanceModifier
                .size(28.dp)
                .clickable(actionRunCallback<RefreshAction>())
        )
    }
}

private fun openWordIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
