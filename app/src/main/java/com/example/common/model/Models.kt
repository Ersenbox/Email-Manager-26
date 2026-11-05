package com.example.common.model

import androidx.compose.ui.graphics.Color

/**
 * Platform Types supported by the shared architecture
 */
enum class PlatformType(val displayName: String) {
    ANDROID("Android"),
    IOS("Apple iOS"),
    DESKTOP("Masaüstü / Web")
}

/**
 * Smart Categories inspired by Edison Mail & Trimbox
 */
enum class SmartCategory(
    val id: String,
    val titleTr: String,
    val iconName: String,
    val colorHex: Long
) {
    ALL("all", "Tüm Postalar", "inbox", 0xFF0284C7),
    PRIMARY("primary", "Birincil", "mail", 0xFF2563EB),
    SUBSCRIPTIONS("subscriptions", "Bülten & Abonelik", "subscriptions", 0xFF8B5CF6),
    BILLS("bills", "Faturalar & Makbuzlar", "receipt", 0xFF10B981),
    PACKAGES("packages", "Kargolar & Siparişler", "local_shipping", 0xFFF59E0B),
    TRAVEL("travel", "Seyahat & Biletler", "flight", 0xFF06B6D4),
    WORK("work", "İş & Projeler", "work", 0xFF3B82F6),
    SPAM("spam", "Gereksiz / Spam", "report", 0xFFEF4444),
    SNOOZED("snoozed", "Ertelenen", "schedule", 0xFF6366F1),
    TRASH("trash", "Çöp Kutusu", "delete", 0xFF64748B);

    companion object {
        fun fromId(id: String): SmartCategory {
            return entries.find { it.id == id } ?: PRIMARY
        }
    }
}

/**
 * Custom tag / label definition
 */
data class CustomLabel(
    val id: String,
    val name: String,
    val colorHex: Long
)

/**
 * Filter Rule for automated processing
 */
enum class RuleConditionField(val displayName: String) {
    SENDER("Gönderen Adresi"),
    SUBJECT("Konu Başlığı"),
    BODY("İçerik Metni")
}

data class FilterRule(
    val id: Long = 0,
    val name: String,
    val conditionField: RuleConditionField,
    val keyword: String,
    val targetCategory: SmartCategory? = null,
    val targetLabel: String? = null,
    val autoArchive: Boolean = false,
    val isEnabled: Boolean = true
)

/**
 * Sender summary for Trimbox-style bulk cleaning
 */
data class CleanSenderGroup(
    val senderEmail: String,
    val senderName: String,
    val count: Int,
    val totalSizeKb: Long,
    val isMailingList: Boolean,
    val isUnsubscribed: Boolean
) {
    val formattedSize: String
        get() = when {
            totalSizeKb >= 1024 -> String.format("%.1f MB", totalSizeKb / 1024.0)
            else -> "$totalSizeKb KB"
        }
}

enum class MailFolderCategory(val displayNameTr: String, val iconName: String) {
    INBOX("Gelen Kutusu (Posteingang)", "inbox"),
    STARRED("Yıldızlı (Markiert)", "star"),
    SNOOZED("Ertelenen (Zurückgestellt)", "schedule"),
    IMPORTANT("Önemli (Wichtig)", "label_important"),
    PURCHASES("Alışverişler (Käufe)", "shopping_bag"),
    SENT("Gönderilenler (Gesendet)", "send"),
    SCHEDULED("Zamanlanan (Geplant)", "access_time"),
    OUTBOX("Giden Kutusu (Postausgang)", "outbox"),
    DRAFTS("Taslaklar (Entwürfe)", "drafts"),
    ALL_MAIL("Tüm Postalar (Alle E-Mails)", "all_inbox"),
    SPAM("Spam / İstenmeyen", "report"),
    TRASH("Çöp Kutusu (Papierkorb)", "delete"),
    SUBSCRIPTIONS("Abonelikleri Yönet (Abos verwalten)", "unsubscribe"),
    CUSTOM("Özel Klasör / Etiket", "folder")
}

data class MailFolderInfo(
    val fullName: String, // e.g. "INBOX", "[Gmail]/Sent Mail", "[Gmail]/Trash"
    val displayName: String, // e.g. "Gelen Kutusu", "Gönderilenler", "Çöp Kutusu"
    val category: MailFolderCategory,
    val totalCount: Int,
    val unreadCount: Int
)

