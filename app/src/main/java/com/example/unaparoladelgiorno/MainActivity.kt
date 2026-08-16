package com.example.unaparoladelgiorno

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.updateAll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.unaparoladelgiorno.data.WordOfDay
import com.example.unaparoladelgiorno.data.WordRepository
import com.example.unaparoladelgiorno.widget.WordWidget
import com.example.unaparoladelgiorno.widget.WordWidgetReceiver
import com.example.unaparoladelgiorno.work.WorkScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository by lazy { WordRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkScheduler.scheduleDailyRefresh(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        repository = repository,
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

@Composable
private fun HomeScreen(repository: WordRepository, onAddWidget: () -> Unit) {
    var word by remember { mutableStateOf<WordOfDay?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        word = repository.getCached()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        when {
            word != null -> WordPreview(word!!)
            !isLoading -> Text("Nessuna parola in cache. Premi \u00abAggiorna adesso\u00bb.")
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

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
    }
}

@Composable
private fun WordPreview(word: WordOfDay) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word.word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        word.syllabication?.let {
            Text(it, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(word.definition, style = MaterialTheme.typography.bodyLarge)
        word.example?.let {
            Spacer(Modifier.height(8.dp))
            Text("\u00AB$it\u00BB", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
        }
        word.etymology?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
