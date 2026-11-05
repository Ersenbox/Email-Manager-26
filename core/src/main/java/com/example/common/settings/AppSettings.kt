package com.example.common.settings

import com.example.common.i18n.I18n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val id: String, val titleTr: String) {
    SYSTEM("system", "Sistem"),
    LIGHT("light", "Gündüz"),
    DARK("dark", "Gece");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Platformun ayar deposu: Android'de SharedPreferences, masaüstünde dosya. */
interface SettingsStore {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
}

/** Dil ve tema tercihleri cihazda saklanır. */
object AppSettings {
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME = "theme"
    private const val KEY_SEEDED = "initial_data_seeded"
    private const val KEY_AUTO_LABELS = "auto_labels"
    private const val KEY_KEEP_DELETED = "keep_deleted"
    private const val KEY_SPAM_DAYS = "spam_auto_delete_days"
    const val SPAM_DAYS = 7
    const val AD_DAYS = 30
    private const val KEY_AD_DAYS = "ad_cleanup_days"
    private const val KEY_NOTIFY = "notify_important"
    private const val KEY_BACKGROUND = "background_sync"
    private const val KEY_WEEKLY = "weekly_report"
    private const val KEY_LAST_REPORT = "last_weekly_report"

    /** Arka plan işi, uygulama ekrandayken eşitleme yapmaz. */
    @Volatile
    var appInForeground = false

    private var prefs: SettingsStore? = null

    val isAttached: Boolean
        get() = prefs != null

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Platform başlarken bir kez çağrılır. */
    fun attach(store: SettingsStore) {
        prefs = store
        _themeMode.value = ThemeMode.fromId(store.getString(KEY_THEME, null))
        I18n.selectLanguage(store.getString(KEY_LANGUAGE, I18n.SOURCE_LANGUAGE) ?: I18n.SOURCE_LANGUAGE)
    }

    fun isInitialDataSeeded(): Boolean = prefs?.getBoolean(KEY_SEEDED, false) ?: false

    fun markInitialDataSeeded() {
        prefs?.putBoolean(KEY_SEEDED, true)
    }

    fun autoLabels(): Boolean = prefs?.getBoolean(KEY_AUTO_LABELS, true) ?: false

    fun setAutoLabels(enabled: Boolean) {
        prefs?.putBoolean(KEY_AUTO_LABELS, enabled)
    }

    fun keepDeletedEmails(): Boolean = prefs?.getBoolean(KEY_KEEP_DELETED, true) ?: false

    fun setKeepDeletedEmails(enabled: Boolean) {
        prefs?.putBoolean(KEY_KEEP_DELETED, enabled)
    }

    /** 0 kapalı demektir; kalıcı silme olduğu için varsayılan kapalıdır. */
    fun spamAutoDeleteDays(): Int = prefs?.getInt(KEY_SPAM_DAYS, 0) ?: 0

    fun setSpamAutoDeleteDays(days: Int) {
        prefs?.putInt(KEY_SPAM_DAYS, days)
    }

    /** 0 kapalı demektir. Reklamlar geri alınabilir şekilde Silinecekler'e gider. */
    fun adCleanupDays(): Int = prefs?.getInt(KEY_AD_DAYS, 0) ?: 0

    fun setAdCleanupDays(days: Int) {
        prefs?.putInt(KEY_AD_DAYS, days)
    }

    fun notifyImportant(): Boolean = prefs?.getBoolean(KEY_NOTIFY, true) ?: false

    fun setNotifyImportant(enabled: Boolean) {
        prefs?.putBoolean(KEY_NOTIFY, enabled)
    }

    fun backgroundSync(): Boolean = prefs?.getBoolean(KEY_BACKGROUND, true) ?: false

    fun setBackgroundSync(enabled: Boolean) {
        prefs?.putBoolean(KEY_BACKGROUND, enabled)
    }

    fun weeklyReport(): Boolean = prefs?.getBoolean(KEY_WEEKLY, true) ?: false

    fun setWeeklyReport(enabled: Boolean) {
        prefs?.putBoolean(KEY_WEEKLY, enabled)
    }

    fun lastWeeklyReport(): Long = prefs?.getLong(KEY_LAST_REPORT, 0L) ?: 0L

    fun setLastWeeklyReport(time: Long) {
        prefs?.putLong(KEY_LAST_REPORT, time)
    }

    fun setLanguage(code: String) {
        I18n.selectLanguage(code)
        prefs?.putString(KEY_LANGUAGE, code)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.putString(KEY_THEME, mode.id)
    }
}
