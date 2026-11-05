package com.example.platform

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.example.common.bridge.HapticType
import com.example.common.bridge.PlatformBridge
import com.example.common.bridge.PlatformInfo
import com.example.common.bridge.SoundType
import com.example.common.model.PlatformType

/**
 * Android Implementation of PlatformBridge
 * Bridges the shared common logic with native Android system services (Audio, Vibrator, Intents, etc.)
 */
class AndroidPlatformBridge(private val context: Context) : PlatformBridge {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        null
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun playSound(soundType: SoundType) {
        try {
            when (soundType) {
                SoundType.NEW_MAIL -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                SoundType.CLEAN_SUCCESS -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                SoundType.UNSUBSCRIBE_SUCCESS -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                SoundType.ALERT -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            }
        } catch (_: Exception) {}
    }

    override fun triggerHaptic(hapticType: HapticType) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (hapticType) {
                    HapticType.LIGHT_TICK -> VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.ACTION_CONFIRM -> VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.SUCCESS_DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 40), -1)
                    HapticType.DELETE_WARNING -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(30)
            }
        } catch (_: Exception) {}
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val deletedPrefs by lazy {
        context.getSharedPreferences("deleted_mail_identifiers", Context.MODE_PRIVATE)
    }

    override fun showToastNotification(message: String) {
        mainHandler.post {
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    override fun getPlatformInfo(): PlatformInfo {
        return PlatformInfo(
            platformType = PlatformType.ANDROID,
            osName = "Android OS",
            osVersion = "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            screenClass = "Adaptive Compose"
        )
    }

    override fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showToastNotification("Bağlantı açılamadı: $url")
        }
    }

    override fun shareContent(subject: String, body: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val shareIntent = Intent.createChooser(sendIntent, "E-posta Paylaş").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            showToastNotification("Paylaşım başarısız")
        }
    }

    override fun addDeletedIdentifier(id: String) {
        if (id.isBlank()) return
        try {
            val current = deletedPrefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(id.trim())
            deletedPrefs.edit().putStringSet("ids", current).apply()
        } catch (_: Exception) {}
    }

    override fun isIdentifierDeleted(id: String): Boolean {
        if (id.isBlank()) return false
        return try {
            val set = deletedPrefs.getStringSet("ids", null) ?: return false
            set.contains(id.trim())
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * ARCHITECTURE NOTE FOR USER REQUIREMENT 2 & 4:
 * For iOS deployment (Compose Multiplatform / Kotlin Native):
 * class IosPlatformBridge : PlatformBridge {
 *     // uses AudioServicesPlaySystemSound(1007) for sound
 *     // uses UIImpactFeedbackGenerator(style: .medium) for haptics
 *     // uses UIApplication.shared.open(url)
 * }
 *
 * For Desktop deployment (Compose for Desktop / JVM):
 * class DesktopPlatformBridge : PlatformBridge {
 *     // uses java.awt.Toolkit.getDefaultToolkit().beep()
 *     // uses java.awt.Desktop.getDesktop().browse(URI.create(url))
 *     // uses SystemTray for desktop notifications
 * }
 */
