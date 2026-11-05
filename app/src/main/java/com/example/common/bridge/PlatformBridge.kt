package com.example.common.bridge

import com.example.common.model.PlatformType

/**
 * Sound event types triggered by the common layer
 */
enum class SoundType {
    NEW_MAIL,
    CLEAN_SUCCESS,
    UNSUBSCRIBE_SUCCESS,
    ALERT
}

/**
 * Haptic feedback vibration intensity
 */
enum class HapticType {
    LIGHT_TICK,
    ACTION_CONFIRM,
    SUCCESS_DOUBLE,
    DELETE_WARNING
}

/**
 * Hardware / Platform info returned by the bridge
 */
data class PlatformInfo(
    val platformType: PlatformType,
    val osName: String,
    val osVersion: String,
    val deviceModel: String,
    val screenClass: String
)

/**
 * Platform Bridge Interface (Kullanıcı Şartı 2: Cross-Platform Bridge)
 * This interface decouples all hardware/OS-specific calls (Audio, Haptics, Notifications, Device Sensors)
 * so the common code runs 100% identically across Android, iOS (Kotlin/Native or Flutter bridge),
 * and Desktop (Compose Desktop/AWT).
 */
interface PlatformBridge {
    /**
     * Play platform sound effect for email actions
     */
    fun playSound(soundType: SoundType)

    /**
     * Trigger tactile/haptic vibration on touch gestures
     */
    fun triggerHaptic(hapticType: HapticType)

    /**
     * Display a system toast or popup notification
     */
    fun showToastNotification(message: String)

    /**
     * Platform & hardware information
     */
    fun getPlatformInfo(): PlatformInfo

    /**
     * Open web unsubscribe or tracking link externally
     */
    fun openUrl(url: String)

    /**
     * Share email text or details via platform share sheet
     */
    fun shareContent(subject: String, body: String)

    /**
     * Persist permanently deleted email identifier (messageId or UID)
     */
    fun addDeletedIdentifier(id: String)

    /**
     * Check if email identifier has been marked permanently deleted
     */
    fun isIdentifierDeleted(id: String): Boolean
}
