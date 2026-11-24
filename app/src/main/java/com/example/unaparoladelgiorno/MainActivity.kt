package com.example.unaparoladelgiorno

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.example.unaparoladelgiorno.data.SettingsRepository
import com.example.unaparoladelgiorno.data.WordOfDay
import com.example.unaparoladelgiorno.data.WordRepository
import com.example.unaparoladelgiorno.widget.WordWidget
import com.example.unaparoladelgiorno.widget.WordWidgetReceiver
import com.example.unaparoladelgiorno.work.WorkScheduler
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val repository by lazy { WordRepository(applicationContext) }
    private val settings by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkScheduler.scheduleDailyRefresh(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        repository = repository,
                        settings = settings,
                        onAddWidget = { requestPinWidget() }
                    )
                }
            }
        }
    }

    private fun requestPinWidget() {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java) ?: return
        val provider = ComponentName(this, WordWidgetReceiver::class.java)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(provider, null, null)
        }
    }
}

/** Shown in the preview until a real word has been scraped. */
private val SampleWord = WordOfDay(
    word = "Irreprensibile",
    syllabication = "ir-re-pren-sì-bi-le",
    definition = "Che non merita critiche, appunti",
    etymology = "voce dotta recuperata dal tardo irreprehensibilis",
    example = "Ha mostrato un aplomb irreprensibile.",
    publishedDate = null,
    fullText = null,
    sourceUrl = "https://unaparolaalgiorno.it",
    fetchedAtEpochMillis = 0L
)

@Composable
private fun HomeScreen(
    repository: WordRepository,
    settings: SettingsRepository,
    onAddWidget: () -> Unit
) {
    var word by remember { mutableStateOf<WordOfDay?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontScale by remember { mutableFloatStateOf(SettingsRepository.DEFAULT_FONT_SCALE) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        word = repository.getCached()
        fontScale = settings.getFontScale()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Una parola al giorno", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Widget non ufficiale per unaparolaalgiorno.it",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))

        // ---- Step 1: text size --------------------------------------------------
        Text(
            "1. Scegli la dimensione del testo",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        WidgetPreviewCard(word = word ?: SampleWord, fontScale = fontScale)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("A", fontSize = 12.sp)
            Slider(
                value = fontScale,
                onValueChange = { fontScale = it },
                valueRange = SettingsRepository.MIN_FONT_SCALE..SettingsRepository.MAX_FONT_SCALE,
                steps = 9, // 0.1 increments
                onValueChangeFinished = {
                    scope.launch {
                        settings.setFontScale(fontScale)
                        // Existing widgets pick the new size up immediately.
                        WordWidget().updateAll(context)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Text("A", fontSize = 26.sp)
        }
        Text(
            "Testo al ${(fontScale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(24.dp))

        // ---- Step 2: data + add widget -------------------------------------------
        Text(
            "2. Aggiorna e aggiungi il widget",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (word == null && !isLoading) {
            Text("Nessuna parola in cache. Premi \u00abAggiorna adesso\u00bb.")
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(onClick = {
            isLoading = true
            error = null
            scope.launch {
                val result = repository.refresh()
                isLoading = false
                result
                    .onSuccess {
                        word = it
                        WordWidget().updateAll(context)
                    }
                    .onFailure {
                        error = "Aggiornamento non riuscito: ${it.message}"
                    }
            }
        }) {
            Text("Aggiorna adesso")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onAddWidget) {
            Text("Aggiungi il widget alla Home")
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** A live mock-up of the widget card, so the chosen size can be judged before adding it. */
@Composable
private fun WidgetPreviewCard(word: WordOfDay, fontScale: Float) {
    val rose = Color(0xFFF8D7D7)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(Color(0xFFE63946), Color(0xFFD92929), Color(0xFF7A1414))),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            "PAROLA DEL GIORNO",
            color = rose,
            fontSize = (10 * fontScale).sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            word.word,
            color = Color.White,
            fontSize = (30 * fontScale.coerceAtMost(1.25f)).sp,
            fontWeight = FontWeight.Bold
        )
        word.syllabication?.let {
            Text(it, color = rose, fontSize = (12 * fontScale).sp, fontStyle = FontStyle.Italic)
        }
        Spacer(Modifier.height(6.dp))
        Text(word.definition, color = Color.White, fontSize = (14 * fontScale).sp)
        word.example?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "\u00AB$it\u00BB",
                color = Color(0xFFFFE9C7),
                fontSize = (13 * fontScale).sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
