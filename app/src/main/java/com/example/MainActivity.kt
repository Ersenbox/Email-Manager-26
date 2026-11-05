package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.common.data.EmailDatabase
import com.example.common.repository.EmailRepository
import com.example.common.settings.AppSettings
import com.example.common.settings.ThemeMode
import com.example.platform.AndroidAppSetup
import com.example.platform.AndroidPlatformBridge
import com.example.platform.BackgroundSync
import com.example.ui.EmailManagerApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.EmailManagerViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppSetup.init(this)
        BackgroundSync.apply(this)
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Sistem çubuğundaki saat ve simgeler uygulamanın temasına uyar
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current.applicationContext
                    val viewModel: EmailManagerViewModel = viewModel(initializer = { createViewModel(context) })
                    EmailManagerApp(viewModel)
                }
            }
        }
    }
}

private fun createViewModel(context: Context): EmailManagerViewModel {
    val bridge = AndroidPlatformBridge(context)
    val database = EmailDatabase.getInstance(context)
    return EmailManagerViewModel(
        bridge = bridge,
        repository = EmailRepository(database.emailDao(), database.accountDao(), bridge),
        seedData = { database.seedInitialData() },
        avatarDir = File(context.filesDir, "avatars")
    )
}

private const val NOTIFICATION_PERMISSION_REQUEST = 41

private fun ComponentActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && AppSettings.notifyImportant() &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }
}

// Maintained for screenshot unit testing compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Email-Manager 26") }
}
