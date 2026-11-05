package com.example.common.service

/**
 * HTML e-postayı okunabilir düz metne çevirir. Stil, betik ve başlık bloklarının
 * içeriği metne karışmaz.
 */
object HtmlText {

    private val dotAll = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val removedBlocks = listOf(
        Regex("<!--.*?-->", dotAll),
        Regex("<(head|style|script|noscript|title|svg)\\b[^>]*>.*?</\\1\\s*>", dotAll),
        // Kapanmamış stil veya betik bloğu metnin sonuna kadar atılır
        Regex("<(style|script)\\b.*$", dotAll)
    )
    private val lineBreaks = Regex("<(br|hr|/p|/div|/tr|/li|/table|/h[1-6])\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]*>")
    private val entities = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val invisible = Regex("[\\u00AD\\u034F\\u200B-\\u200F\\u2060\\uFEFF]")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val blankLines = Regex("\\n{3,}")
    private val anyWhitespace = Regex("\\s+")

    private val namedEntities = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "zwnj" to "", "zwj" to "", "shy" to "",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü",
        "szlig" to "ß", "ccedil" to "ç", "Ccedil" to "Ç",
        "euro" to "€", "pound" to "£", "copy" to "©", "reg" to "®", "trade" to "™",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "middot" to "·", "bull" to "•",
        "laquo" to "«", "raquo" to "»", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”"
    )

    fun toPlainText(html: String): String {
        var text = html
        for (block in removedBlocks) {
            text = block.replace(text, " ")
        }
        text = lineBreaks.replace(text, "\n")
        text = tags.replace(text, " ")
        text = entities.replace(text) { decodeEntity(it.groupValues[1]) ?: it.value }
        text = invisible.replace(text, "")
        return text.replace("\r", "")
            .split('\n')
            .joinToString("\n") { spaces.replace(it, " ").trim() }
            .let { blankLines.replace(it, "\n\n") }
            .trim()
    }

    /** Liste önizlemesi için tek satırlık özet. */
    fun preview(body: String, maxLength: Int = 160): String =
        anyWhitespace.replace(body, " ").trim().take(maxLength)

    private fun decodeEntity(name: String): String? {
        if (!name.startsWith("#")) return namedEntities[name]
        val code = if (name.length > 1 && (name[1] == 'x' || name[1] == 'X')) {
            name.substring(2).toIntOrNull(16)
        } else {
            name.substring(1).toIntOrNull()
        } ?: return null
        return if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else null
    }

    private val imageTag = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val tinySize = Regex("(width|height)\\s*[=:]\\s*[\"']?\\s*[01](px)?\\b", RegexOption.IGNORE_CASE)
    private val hiddenStyle = Regex("display\\s*:\\s*none|visibility\\s*:\\s*hidden", RegexOption.IGNORE_CASE)
    private val trackerWords = listOf("open.track", "track/open", "/open.gif", "/open?", "pixel.gif", "/pixel", "beacon", "email-tracker")

    /** Görünmez veya izleme adresli görsel varsa true döner. */
    fun hasTrackingPixel(html: String): Boolean = imageTag.findAll(html).any { match ->
        val tag = match.value
        val tiny = tinySize.findAll(tag).map { it.groupValues[1].lowercase() }.toSet().size == 2
        tiny || hiddenStyle.containsMatchIn(tag) || trackerWords.any { tag.contains(it, ignoreCase = true) }
    }
}
