package com.example.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.common.i18n.I18n
import com.example.common.settings.AppSettings
import com.example.common.settings.ThemeMode
import com.example.ui.EmailManagerApp
import com.example.ui.viewmodel.EmailManagerViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

private val messages = MutableStateFlow<String?>(null)
private const val MESSAGE_MS = 3_500L

fun main() {
    // Testlerde gerçek veri klasörü yerine ayrı klasör kullanılabilir
    val dataDir = System.getenv("EM26_DATA_DIR")?.let { File(it) } ?: DesktopApp.defaultDataDir()
    val app = runBlocking { DesktopApp.start(dataDir, onMessage = { messages.value = it }) }

    application {
        val languageState = remember { mutableStateOf(I18n.language) }
        remember {
            I18n.onLanguageRead = { languageState.value }
            I18n.onLanguageChanged = { languageState.value = it }
        }
        val viewModel = remember {
            EmailManagerViewModel(
                bridge = app.bridge,
                repository = app.repository,
                seedData = {},
                avatarDir = File(dataDir, "avatars")
            )
        }
        val themeMode by AppSettings.themeMode.collectAsState()
        val dark = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        Window(
            onCloseRequest = {
                app.close()
                exitApplication()
            },
            title = "Email-Manager 26",
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(Alignment.Center))
        ) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmailManagerApp(viewModel)
                        MessageBar(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }
}

/** Android'deki kısa bildirimlerin masaüstü karşılığı. */
@androidx.compose.runtime.Composable
private fun MessageBar(modifier: Modifier) {
    val message by messages.collectAsState()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        shown = current
        delay(MESSAGE_MS)
        shown = null
        messages.value = null
    }
    shown?.let { Snackbar(modifier = modifier.padding(16.dp)) { Text(it) } }
}
