package com.example.common.i18n

import com.example.common.model.MailFolderCategory
import com.example.common.model.SmartCategory
import com.example.common.settings.ThemeMode
import com.example.ui.viewmodel.AutoSplitCategory
import com.example.ui.viewmodel.MainNavTab
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nCatalogTest {

    private val moduleDir = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
    private val catalog = I18n.parse(File(moduleDir, "assets/i18n.tsv").readText())

    private val literal = Regex("(?<![A-Za-z0-9_.])t\\(\"((?:[^\"\\\\]|\\\\.)*)\"")

    private fun sourceKeys(): Set<String> =
        listOf(File(moduleDir, "java"), File(moduleDir, "../../../core/src/main/java"), File(moduleDir, "../../../shared-ui/src"))
            .flatMap { it.walkTopDown().toList() }
            .asSequence()
            .filter { it.extension == "kt" }
            .flatMap { file -> literal.findAll(file.readText()).map { it.groupValues[1] } }
            .map { it.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\") }
            .toSet()

    private fun enumKeys(): Set<String> = buildSet {
        addAll(SmartCategory.entries.map { it.titleTr })
        addAll(MailFolderCategory.entries.map { it.displayNameTr })
        addAll(MainNavTab.entries.map { it.titleTr })
        addAll(AutoSplitCategory.entries.flatMap { listOf(it.titleTr, it.subtitleTr) })
        addAll(ThemeMode.entries.map { it.titleTr })
        addAll(com.example.common.filter.AutoFolder.entries.map { it.titleTr })
    }

    @After
    fun reset() {
        I18n.selectLanguage(I18n.SOURCE_LANGUAGE)
    }

    @Test
    fun `every visible text has german english and french`() {
        val missing = (sourceKeys() + enumKeys()).filter { key ->
            val row = catalog[key]
            row == null || row.values.size != 3 || row.values.any { it.isBlank() }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `placeholders survive translation`() {
        val placeholder = Regex("\\{\\d+\\}")
        val broken = catalog.flatMap { (key, row) ->
            val expected = placeholder.findAll(key).map { it.value }.toSet()
            row.filter { (_, text) -> placeholder.findAll(text).map { it.value }.toSet() != expected }
                .map { (language, _) -> "$language: $key" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `language switch changes the text and unknown text stays`() {
        I18n.load(File(moduleDir, "assets/i18n.tsv").inputStream())
        assertEquals("Eşitle", t("Eşitle"))
        I18n.selectLanguage("de")
        assertEquals("Synchronisieren", t("Eşitle"))
        assertEquals("Hata: x".replace("Hata", "Fehler"), t("Hata: {0}", "x"))
        assertEquals("yok", t("yok"))
        I18n.selectLanguage("xx")
        assertTrue(I18n.language == I18n.SOURCE_LANGUAGE)
    }
}
