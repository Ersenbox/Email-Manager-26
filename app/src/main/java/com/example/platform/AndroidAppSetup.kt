package com.example.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.example.common.i18n.I18n
import com.example.common.security.CredentialCipher
import com.example.common.security.Credentials
import com.example.common.settings.AppSettings
import com.example.common.settings.SettingsStore

private class AndroidSettingsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String, default: String?) = prefs.getString(key, default)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
}

/** Ortak modülü Android'e bağlar. Etkinlik ve arka plan işi başlarken çağrılır. */
object AndroidAppSetup {
    private const val PREFS = "app_settings"

    fun init(context: Context) {
        if (AppSettings.isAttached) return
        val languageState = mutableStateOf(I18n.language)
        I18n.onLanguageRead = { languageState.value }
        I18n.onLanguageChanged = { languageState.value = it }
        Credentials.codec = CredentialCipher
        context.assets.open(I18n.CATALOG_FILE).use { I18n.load(it) }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AppSettings.attach(AndroidSettingsStore(prefs))
    }
}
