package com.example.common.service

import com.example.common.data.EmailEntity
import com.example.common.model.MailFolderCategory

/** Bir postanın sunucudaki bir klasörde bulunduğu kayıt. */
data class FolderMembership(
    val messageKey: String,
    val folderName: String,
    val serverUid: Long
)

/**
 * Gmail'de aynı posta birden çok klasörde görünür (Gelen, Wichtig, Alle E-Mails).
 * Uygulama her postayı bir kez saklar; işlemlerde kullanılacak ana konum burada seçilir.
 */
object FolderMerge {

    private val locationPriority = listOf(
        MailFolderCategory.INBOX,
        MailFolderCategory.DRAFTS,
        MailFolderCategory.SENT,
        MailFolderCategory.SPAM,
        MailFolderCategory.HOLD,
        MailFolderCategory.TRASH,
        MailFolderCategory.ALL_MAIL,
        MailFolderCategory.CUSTOM,
        MailFolderCategory.SUBSCRIPTIONS,
        MailFolderCategory.PURCHASES,
        MailFolderCategory.OUTBOX,
        MailFolderCategory.SCHEDULED,
        MailFolderCategory.SNOOZED,
        MailFolderCategory.IMPORTANT,
        MailFolderCategory.STARRED
    )

    /** Message-ID yoksa posta yalnızca kendi klasöründeki UID ile tanınır. */
    fun messageKey(email: EmailEntity): String =
        email.messageId?.takeIf { it.isNotBlank() } ?: "${email.folderName}#${email.serverUid}"

    fun merge(
        versions: List<EmailEntity>,
        categoryOf: (String) -> MailFolderCategory
    ): EmailEntity {
        val primary = versions.minBy { rank(categoryOf(it.folderName)) }
        val categories = versions.map { categoryOf(it.folderName) }
        val primaryCategory = categoryOf(primary.folderName)
        val archived = when {
            MailFolderCategory.INBOX in categories -> false
            primaryCategory == MailFolderCategory.ALL_MAIL -> true
            MailFolderNames.isArchive(primary.folderName) -> true
            else -> primary.isArchived
        }
        return primary.copy(
            id = versions.firstOrNull { it.id != 0L }?.id ?: primary.id,
            isArchived = archived
        )
    }

    private fun rank(category: MailFolderCategory): Int {
        val index = locationPriority.indexOf(category)
        return if (index >= 0) index else locationPriority.size
    }
}
