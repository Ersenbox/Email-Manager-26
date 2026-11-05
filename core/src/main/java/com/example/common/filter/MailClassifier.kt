package com.example.common.filter

import com.example.common.data.EmailEntity
import com.example.common.model.SmartCategory

/** Gmail'de gerçek etiket olarak oluşturulan otomatik klasörler. */
enum class AutoFolder(val serverName: String, val titleTr: String) {
    ADS("EM26/Reklam", "Reklam"),
    SOCIAL("EM26/Sosyal Medya", "Sosyal Medya"),
    NEWSLETTER("EM26/Bültenler", "Bültenler"),
    BILLS("EM26/Faturalar", "Faturalar"),
    PACKAGES("EM26/Kargo", "Kargo");

    companion object {
        fun fromServerName(name: String?): AutoFolder? = entries.firstOrNull { it.serverName == name }
    }
}

/** Postayı gönderenine ve içeriğine göre otomatik klasöre ayırır. */
object MailClassifier {
    /** Kullanıcı bu gönderen için otomatik klasör istemiyor. */
    const val NO_FOLDER = "NONE"

    /** Gönderen engellendi; postaları otomatik silinir. */
    const val BLOCKED = "BLOCKED"

    private val socialDomains = listOf(
        "instagram.com", "facebook.com", "facebookmail.com", "tiktok.com", "twitter.com", "x.com",
        "linkedin.com", "pinterest.com", "snapchat.com", "reddit.com", "threads.net", "discord.com"
    )

    private val adWords = listOf(
        "rabatt", "gutschein", "angebot", "sale", "reduziert", "aktion", "sparen",
        "indirim", "kampanya", "fırsat", "kupon",
        "discount", "offer", "deal", "promo", "% off", "coupon",
        "soldes", "promotion", "réduction"
    )

    private fun isSocial(senderEmail: String): Boolean {
        val domain = senderEmail.substringAfterLast('@').lowercase()
        return socialDomains.any { domain == it || domain.endsWith(".$it") }
    }

    /** override: gönderen için kullanıcının seçtiği klasör adı veya [NO_FOLDER]. */
    fun classify(email: EmailEntity, override: String? = null): AutoFolder? {
        if (override == NO_FOLDER || override == BLOCKED) return null
        AutoFolder.fromServerName(override)?.let { return it }

        if (isSocial(email.senderEmail)) return AutoFolder.SOCIAL
        return when (email.categoryId) {
            SmartCategory.BILLS.id -> AutoFolder.BILLS
            SmartCategory.PACKAGES.id -> AutoFolder.PACKAGES
            else -> {
                val isList = email.isMailingList || email.categoryId == SmartCategory.SUBSCRIPTIONS.id
                when {
                    !isList -> null
                    adWords.any { email.subject.contains(it, ignoreCase = true) } -> AutoFolder.ADS
                    else -> AutoFolder.NEWSLETTER
                }
            }
        }
    }
}
