package com.example.unaparoladelgiorno.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.unaparoladelgiorno.R
import com.example.unaparoladelgiorno.data.SettingsRepository
import com.example.unaparoladelgiorno.data.WordOfDay
import com.example.unaparoladelgiorno.data.WordRepository
import org.jsoup.Jsoup

private val Rose = Color(0xFFF8D7D7)
private val RoseSoft = Color(0xFFEFC6C6)
private val Cream = Color(0xFFFFE9C7)
private val CardFill = Color(0x26FFFFFF)
private val DividerFill = Color(0x40FFFFFF)

/** Base text size (sp) multiplied by the user's font-scale setting. */
private fun scaled(base: Float, scale: Float): TextUnit = (base * scale).sp

/**
 * The widget the user actually adds to their home screen. It never loads
 * unaparolaalgiorno.it in a WebView - it only ever renders the [WordOfDay]
 * that [WordRepository] has cached from the last scrape, at the text size
 * chosen in the app ([SettingsRepository]).
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
        // Rendering only ever reads local data - it is fast and offline-safe.
        // Actual network refreshes happen in RefreshAction and the worker.
        val word = WordRepository(context).getCached()
        val fontScale = SettingsRepository(context).getFontScale()
        provideContent {
            WordWidgetContent(word = word, fontScale = fontScale)
        }
    }
}

@Composable
private fun WordWidgetContent(word: WordOfDay?, fontScale: Float) {
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
                EmptyState(fontScale)
            } else if (isExpanded && word.fullText != null) {
                ExpandedWordContent(word, fontScale)
            } else {
                CompactWordContent(word, isCompact, isNarrow, fontScale)
            }
        }
    }
}

@Composable
private fun CompactWordContent(
    word: WordOfDay,
    isCompact: Boolean,
    isNarrow: Boolean,
    fontScale: Float
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "PAROLA DEL GIORNO",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Rose),
                    fontSize = scaled(10f, fontScale),
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
                // The title is a single line, so cap its growth to avoid clipping long words.
                fontSize = scaled(if (isNarrow) 22f else 30f, fontScale.coerceAtMost(1.25f)),
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
                        color = ColorProvider(Rose),
                        fontSize = scaled(12f, fontScale),
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
                fontSize = scaled(14f, fontScale)
            )
        )

        if (!isCompact) {
            word.example?.let { example ->
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "\u00AB$example\u00BB",
                    maxLines = 2,
                    style = TextStyle(
                        color = ColorProvider(Cream),
                        fontSize = scaled(13f, fontScale),
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
                            color = ColorProvider(RoseSoft),
                            fontSize = scaled(11f, fontScale)
                        )
                    )
                }
                if (word.fullText != null) {
                    Text(
                        text = "Mostra tutto",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = scaled(12f, fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<ToggleExpandAction>())
                    )
                }
            }
        }
    }
}

/**
 * The "Mostra tutto" reading view: a scrollable page with clearly separated
 * sections (title, significato, etimologia, example) followed by the article
 * as individual paragraphs, then a footer linking to the full page.
 */
@Composable
private fun ExpandedWordContent(word: WordOfDay, fontScale: Float) {
    val packageName = LocalContext.current.packageName
    val paragraphs = remember(word.fullText) { splitParagraphs(word.fullText) }

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "PAROLA DEL GIORNO",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Rose),
                    fontSize = scaled(10f, fontScale),
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "Mostra meno",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = scaled(12f, fontScale),
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleExpandAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            // Title + syllabication
            item {
                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        text = word.word,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = scaled(28f, fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.clickable(
                            actionStartActivity(openWordIntent(word.sourceUrl))
                        )
                    )
                    word.syllabication?.let {
                        Text(
                            text = it,
                            style = TextStyle(
                                color = ColorProvider(Rose),
                                fontSize = scaled(13f, fontScale),
                                fontStyle = FontStyle.Italic
                            )
                        )
                    }
                }
            }

            // Significato
            if (word.definition.isNotBlank()) {
                item {
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        SectionLabel("SIGNIFICATO", fontScale)
                        Text(
                            text = word.definition,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = scaled(17f, fontScale),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Etimologia, as a soft card
            word.etymology?.let { etymology ->
                item {
                    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(CardFill))
                                .cornerRadius(14.dp)
                                .padding(12.dp)
                        ) {
                            SectionLabel("ETIMOLOGIA", fontScale)
                            Text(
                                text = etymology,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = scaled(13f, fontScale)
                                )
                            )
                        }
                    }
                }
            }

            // Example sentence, highlighted
            word.example?.let { example ->
                item {
                    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(CardFill))
                                .cornerRadius(14.dp)
                                .padding(12.dp)
                        ) {
                            SectionLabel("ESEMPIO", fontScale)
                            Text(
                                text = "\u00AB$example\u00BB",
                                style = TextStyle(
                                    color = ColorProvider(Cream),
                                    fontSize = scaled(15f, fontScale),
                                    fontStyle = FontStyle.Italic
                                )
                            )
                        }
                    }
                }
            }

            // Divider between the "card" sections and the article
            item {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(DividerFill))
                ) {}
            }
            item { Spacer(modifier = GlanceModifier.height(12.dp)) }

            // Article: one list item per paragraph for smooth scrolling and
            // proper paragraph spacing.
            items(paragraphs.size) { index ->
                ArticleParagraph(packageName, paragraphs[index], fontScale)
            }

            // Footer
            item {
                Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    word.publishedDate?.let {
                        Text(
                            text = "Parola pubblicata il $it",
                            style = TextStyle(
                                color = ColorProvider(RoseSoft),
                                fontSize = scaled(11f, fontScale),
                                fontStyle = FontStyle.Italic
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                    Text(
                        text = "Leggi sul sito \u2192",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = scaled(13f, fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.clickable(
                            actionStartActivity(openWordIntent(word.sourceUrl))
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, fontScale: Float) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(Rose),
            fontSize = scaled(10f, fontScale),
            fontWeight = FontWeight.Bold
        ),
        modifier = GlanceModifier.padding(bottom = 3.dp)
    )
}

/** One article paragraph, rendered from HTML so bold/italic/links survive. */
@Composable
private fun ArticleParagraph(packageName: String, html: String, fontScale: Float) {
    AndroidRemoteViews(
        remoteViews = RemoteViews(packageName, R.layout.widget_html_text).apply {
            setTextViewText(
                R.id.html_text_view,
                Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            )
            setTextViewTextSize(R.id.html_text_view, TypedValue.COMPLEX_UNIT_SP, 15f * fontScale)
        }
    )
}

/** Splits the stored article HTML into one HTML snippet per paragraph. */
private fun splitParagraphs(html: String?): List<String> {
    if (html.isNullOrBlank()) return emptyList()
    val doc = Jsoup.parseBodyFragment(html)
    doc.outputSettings().prettyPrint(false)
    val paragraphs = doc.select("p").map { it.html().trim() }.filter { it.isNotBlank() }
    return paragraphs.ifEmpty { listOf(doc.body().html().trim()).filter { it.isNotBlank() } }
}

@Composable
private fun EmptyState(fontScale: Float) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "Parola del giorno",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = scaled(14f, fontScale),
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Tocca per caricare",
            style = TextStyle(color = ColorProvider(Rose), fontSize = scaled(12f, fontScale))
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
