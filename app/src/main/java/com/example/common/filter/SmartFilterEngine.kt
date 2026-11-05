package com.example.common.filter

import com.example.common.data.EmailEntity
import com.example.common.data.RuleEntity
import com.example.common.model.CleanSenderGroup
import com.example.common.model.SmartCategory

/**
 * Smart Filter and Auto-Categorization Engine
 * Inspired by Edison Mail & Trimbox intelligence.
 * Cross-platform compatible pure Kotlin logic.
 */
object SmartFilterEngine {

    /**
     * Determines the smart category for an incoming email based on content heuristics
     */
    fun detectSmartCategory(sender: String, subject: String, body: String): SmartCategory {
        val s = subject.lowercase()
        val b = body.lowercase()
        val from = sender.lowercase()

        return when {
            // Spam detection
            s.contains("tebrikler") && (s.contains("kazandınız") || s.contains("ödül") || s.contains("piyango")) -> SmartCategory.SPAM
            s.contains("şüpheli") || from.contains("sans-merkezi") -> SmartCategory.SPAM

            // Bills & Invoices
            s.contains("fatura") || s.contains("dekont") || s.contains("makbuz") || s.contains("ödeme") ||
                    s.contains("ekstre") || s.contains("invoice") || s.contains("receipt") -> SmartCategory.BILLS

            // Travel & Tickets
            s.contains("bilet") || s.contains("uçuş") || s.contains("rezervasyon") || s.contains("pnr") ||
                    s.contains("flight") || s.contains("hotel") || s.contains("boarding") -> SmartCategory.TRAVEL

            // Packages & Shipments
            s.contains("kargo") || s.contains("sipariş") || s.contains("teslimat") || s.contains("tracking") ||
                    s.contains("kurye") || s.contains("paket") || s.contains("shipped") -> SmartCategory.PACKAGES

            // Subscriptions & Newsletters
            b.contains("unsubscribe") || b.contains("abonelikten çık") || b.contains("bülten") ||
                    s.contains("newsletter") || s.contains("bülten") || b.contains("list-unsubscribe") -> SmartCategory.SUBSCRIPTIONS

            // Work
            s.contains("toplantı") || s.contains("sprint") || s.contains("proje") || s.contains("mimari") ||
                    s.contains("rapor") || from.contains("@sirket.") || from.contains("@company.") -> SmartCategory.WORK

            else -> SmartCategory.PRIMARY
        }
    }

    /**
     * Checks if email contains tracking pixels (common in promotional emails)
     */
    fun hasTrackingPixel(sender: String, body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("pixel") || lower.contains("track") || lower.contains("open.gif") ||
                sender.contains("newsletter") || sender.contains("shop") || sender.contains("bulten")
    }

    /**
     * Executes user-defined smart filtering rules on an email entity
     */
    fun applyRules(email: EmailEntity, rules: List<RuleEntity>): EmailEntity {
        var modified = email
        for (rule in rules.filter { it.isEnabled }) {
            val contentToMatch = when (rule.conditionField.uppercase()) {
                "SENDER" -> modified.senderEmail.lowercase() + " " + modified.senderName.lowercase()
                "SUBJECT" -> modified.subject.lowercase()
                "BODY" -> modified.body.lowercase()
                else -> ""
            }

            if (contentToMatch.contains(rule.keyword.lowercase())) {
                val newCategoryId = rule.targetCategoryId ?: modified.categoryId
                val newLabels = if (!rule.targetLabel.isNullOrBlank()) {
                    val current = modified.labelsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                    current.add(rule.targetLabel)
                    current.joinToString(",")
                } else {
                    modified.labelsCsv
                }
                val newArchive = if (rule.autoArchive) true else modified.isArchived

                modified = modified.copy(
                    categoryId = newCategoryId,
                    labelsCsv = newLabels,
                    isArchived = newArchive
                )
            }
        }
        return modified
    }

    /**
     * Filter email list with multiple active filters
     */
    fun filterEmails(
        emails: List<EmailEntity>,
        query: String,
        selectedCategory: SmartCategory,
        selectedLabel: String?,
        onlyUnread: Boolean,
        onlyStarred: Boolean,
        onlyWithAttachments: Boolean
    ): List<EmailEntity> {
        return emails.filter { email ->
            // Category filter
            val matchesCategory = when (selectedCategory) {
                SmartCategory.ALL -> !email.isTrash && !email.isArchived
                SmartCategory.TRASH -> email.isTrash
                SmartCategory.SPAM -> !email.isTrash && (email.categoryId == SmartCategory.SPAM.id || email.folderName.contains("spam", ignoreCase = true))
                SmartCategory.SNOOZED -> !email.isTrash && (email.categoryId == SmartCategory.SNOOZED.id || email.labelsCsv.contains("Ertele", ignoreCase = true) || email.labelsCsv.contains("snooze", ignoreCase = true))
                else -> !email.isTrash && !email.isArchived && email.categoryId == selectedCategory.id
            }

            // Search query
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                val q = query.trim().lowercase()
                email.subject.lowercase().contains(q) ||
                        email.senderName.lowercase().contains(q) ||
                        email.senderEmail.lowercase().contains(q) ||
                        email.preview.lowercase().contains(q) ||
                        email.labelsCsv.lowercase().contains(q)
            }

            // Label filter (supporting multi-language Gmail labels: Gönderilen, Taslak, etc.)
            val matchesLabel = if (selectedLabel.isNullOrBlank()) {
                true
            } else {
                val sLabel = selectedLabel.lowercase()
                val labelsList = email.labelsCsv.split(",").map { it.trim().lowercase() }
                when (sLabel) {
                    "gönderilen", "gesendet", "sent" -> {
                        labelsList.contains("gönderilen") ||
                                email.folderName.contains("sent", ignoreCase = true) ||
                                email.folderName.contains("gesendet", ignoreCase = true) ||
                                email.folderName.contains("gönderil", ignoreCase = true)
                    }
                    "taslak", "entwürfe", "draft", "drafts" -> {
                        labelsList.contains("taslak") ||
                                email.folderName.contains("draft", ignoreCase = true) ||
                                email.folderName.contains("entwürfe", ignoreCase = true) ||
                                email.folderName.contains("entwuerfe", ignoreCase = true)
                    }
                    else -> labelsList.contains(sLabel) || email.folderName.contains(sLabel, ignoreCase = true)
                }
            }

            // Quick toggles
            val matchesUnread = !onlyUnread || !email.isRead
            val matchesStarred = !onlyStarred || email.isStarred
            val matchesAttachment = !onlyWithAttachments || email.hasAttachment

            matchesCategory && matchesQuery && matchesLabel && matchesUnread && matchesStarred && matchesAttachment
        }
    }

    /**
     * Groups emails by sender for Trimbox-style bulk cleaning and newsletter unsubscribe
     */
    fun generateCleanSenderGroups(emails: List<EmailEntity>): List<CleanSenderGroup> {
        val nonTrash = emails.filter { !it.isTrash }
        return nonTrash.groupBy { it.senderEmail }
            .map { (email, list) ->
                val first = list.first()
                val isNewsletter = list.any { it.isMailingList || it.categoryId == SmartCategory.SUBSCRIPTIONS.id }
                val isUnsub = list.all { it.isUnsubscribed }
                val totalSize = list.sumOf { it.storageSizeKb }
                CleanSenderGroup(
                    senderEmail = email,
                    senderName = first.senderName,
                    count = list.size,
                    totalSizeKb = totalSize,
                    isMailingList = isNewsletter,
                    isUnsubscribed = isUnsub
                )
            }
            .sortedWith(compareByDescending<CleanSenderGroup> { it.isMailingList }.thenByDescending { it.count })
    }
}
