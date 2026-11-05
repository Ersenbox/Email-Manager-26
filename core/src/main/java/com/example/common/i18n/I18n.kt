package com.example.common.i18n

import java.io.InputStream
import java.util.Locale

data class AppLanguage(val code: String, val title: String)

/**
 * Uygulama içi çeviri. Türkçe metin anahtardır; diğer diller assets/i18n.tsv dosyasındadır.
 * Arayüz [onLanguageRead] ve [onLanguageChanged] ile dili kendi durumuna bağlar; böylece
 * dil değişince ekranlar yeniden çizilir, bu modül arayüz kütüphanesine bağlı kalmaz.
 */
object I18n {
    const val SOURCE_LANGUAGE = "tr"
    const val CATALOG_FILE = "i18n.tsv"

    val languages = listOf(
        AppLanguage("tr", "Türkçe"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("en", "English"),
        AppLanguage("fr", "Français")
    )

    private val columns = listOf("de", "en", "fr")
    private var catalog: Map<String, Map<String, String>> = emptyMap()

    var language: String = SOURCE_LANGUAGE
        private set

    var onLanguageRead: () -> Unit = {}
    var onLanguageChanged: (String) -> Unit = {}

    val locale: Locale
        get() {
            onLanguageRead()
            return Locale.forLanguageTag(language)
        }

    fun selectLanguage(code: String) {
        language = if (languages.any { it.code == code }) code else SOURCE_LANGUAGE
        onLanguageChanged(language)
    }

    fun load(stream: InputStream) {
        catalog = parse(stream.bufferedReader(Charsets.UTF_8).readText())
    }

    fun parse(text: String): Map<String, Map<String, String>> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val cells = line.split('\t').map { unescape(it) }
                cells[0] to columns.withIndex().associate { (index, code) ->
                    code to cells.getOrElse(index + 1) { "" }
                }
            }

    private fun unescape(cell: String): String = cell.replace("\\n", "\n")

    fun translate(source: String, args: Array<out Any?>): String {
        onLanguageRead()
        val translated = catalog[source]?.get(language)?.takeIf { it.isNotEmpty() } ?: source
        return args.foldIndexed(translated) { index, text, arg -> text.replace("{$index}", arg.toString()) }
    }
}

/** Türkçe metni seçili dile çevirir; {0}, {1} yer tutucularına değerleri yerleştirir. */
fun t(source: String, vararg args: Any?): String = I18n.translate(source, args)
