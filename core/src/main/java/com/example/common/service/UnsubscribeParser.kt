package com.example.common.service

import java.net.URLDecoder
import java.util.Locale

data class MailtoUnsubscribe(
    val address: String,
    val subject: String,
    val body: String
)

data class UnsubscribeOptions(
    val httpsUrl: String?,
    val browserUrl: String?,
    val mailto: MailtoUnsubscribe?,
    val oneClick: Boolean
) {
    val isEmpty: Boolean get() = httpsUrl == null && browserUrl == null && mailto == null
}

/**
 * RFC 2369 (List-Unsubscribe) ve RFC 8058 (List-Unsubscribe-Post) başlıklarını çözer.
 */
object UnsubscribeParser {

    private val bracketed = Regex("<([^>]+)>")

    fun parse(
        listUnsubscribe: String?,
        listUnsubscribePost: String?,
        fallbackUrl: String? = null
    ): UnsubscribeOptions {
        val entries = bracketed.findAll(listUnsubscribe.orEmpty())
            .map { entry -> entry.groupValues[1].filterNot { it.isWhitespace() } }
            .toList()

        val https = entries.firstOrNull { it.lowercase(Locale.ROOT).startsWith("https://") }
        val http = entries.firstOrNull { it.lowercase(Locale.ROOT).startsWith("http://") }
        val mailto = entries.firstOrNull { it.lowercase(Locale.ROOT).startsWith("mailto:") }
            ?.let { parseMailto(it) }
        val fallback = fallbackUrl?.trim()?.takeIf {
            val lower = it.lowercase(Locale.ROOT)
            lower.startsWith("https://") || lower.startsWith("http://")
        }
        val oneClick = https != null &&
                listUnsubscribePost.orEmpty().replace(" ", "")
                    .contains("List-Unsubscribe=One-Click", ignoreCase = true)

        return UnsubscribeOptions(
            httpsUrl = https,
            browserUrl = https ?: http ?: fallback,
            mailto = mailto,
            oneClick = oneClick
        )
    }

    private fun parseMailto(value: String): MailtoUnsubscribe? {
        val raw = value.substring("mailto:".length)
        val address = decode(raw.substringBefore('?')).trim()
        if (!address.contains("@")) return null

        val params = raw.substringAfter('?', "")
            .split('&')
            .filter { it.contains('=') }
            .associate { it.substringBefore('=').lowercase(Locale.ROOT) to decode(it.substringAfter('=')) }

        return MailtoUnsubscribe(
            address = address,
            subject = params["subject"]?.takeIf { it.isNotBlank() } ?: "unsubscribe",
            body = params["body"]?.takeIf { it.isNotBlank() } ?: "unsubscribe"
        )
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        value
    }
}
