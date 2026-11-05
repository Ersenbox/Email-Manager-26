package com.example.common.service

import com.example.common.data.EmailEntity
import com.example.common.data.RuleEntity
import com.example.common.filter.SmartFilterEngine
import com.example.common.model.MailFolderCategory
import com.example.common.model.SmartCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderMergeTest {

    private val categories = mapOf(
        "INBOX" to MailFolderCategory.INBOX,
        "[Gmail]/Wichtig" to MailFolderCategory.IMPORTANT,
        "[Gmail]/Alle Nachrichten" to MailFolderCategory.ALL_MAIL,
        "[Gmail]/Gesendet" to MailFolderCategory.SENT,
        "Projekte" to MailFolderCategory.CUSTOM
    )

    private fun email(folder: String, uid: Long, id: Long = 0L, subject: String = "Konu", body: String = "metin") = EmailEntity(
        id = id,
        senderName = "A",
        senderEmail = "a@example.org",
        subject = subject,
        preview = "",
        body = body,
        timestamp = 1L,
        folderName = folder,
        serverUid = uid,
        messageId = "<1@test>"
    )

    private fun merge(vararg emails: EmailEntity) =
        FolderMerge.merge(emails.toList()) { categories[it] ?: MailFolderCategory.CUSTOM }

    @Test
    fun `inbox wins over important and all mail`() {
        val merged = merge(email("[Gmail]/Alle Nachrichten", 7), email("[Gmail]/Wichtig", 3), email("INBOX", 5))

        assertEquals("INBOX", merged.folderName)
        assertEquals(5L, merged.serverUid)
        assertFalse(merged.isArchived)
    }

    @Test
    fun `mail only in all mail is archived`() {
        val merged = merge(email("[Gmail]/Wichtig", 3), email("[Gmail]/Alle Nachrichten", 7))

        assertEquals("[Gmail]/Alle Nachrichten", merged.folderName)
        assertTrue(merged.isArchived)
    }

    @Test
    fun `sent mail is not archived`() {
        val merged = merge(email("[Gmail]/Alle Nachrichten", 7), email("[Gmail]/Gesendet", 2))

        assertEquals("[Gmail]/Gesendet", merged.folderName)
        assertFalse(merged.isArchived)
    }

    @Test
    fun `stored id is kept whichever folder wins`() {
        assertEquals(42L, merge(email("[Gmail]/Alle Nachrichten", 7, id = 42L), email("INBOX", 5)).id)
    }

    @Test
    fun `mail without message id is keyed by folder and uid`() {
        assertEquals("INBOX#5", FolderMerge.messageKey(email("INBOX", 5).copy(messageId = null)))
    }

    @Test
    fun `german folder names and server attributes are recognised`() {
        assertEquals(MailFolderCategory.ALL_MAIL, MailService.categorizeFolder("[Gmail]/Alle Nachrichten", "Alle Nachrichten").first)
        assertEquals(
            MailFolderCategory.ALL_MAIL,
            MailService.categorizeFolder("[Gmail]/Herhangi", "Herhangi", arrayOf("\\HasNoChildren", "\\All")).first
        )
        assertEquals(
            MailFolderCategory.IMPORTANT,
            MailService.categorizeFolder("[Gmail]/Herhangi", "Herhangi", arrayOf("\\Important")).first
        )
    }

    @Test
    fun `rule with comma keywords matches any of them`() {
        val rule = RuleEntity(
            name = "Fatura",
            conditionField = "SUBJECT",
            keyword = "fatura, rechnung , invoice",
            targetCategoryId = SmartCategory.BILLS.id,
            targetLabel = "Finans"
        )

        val hit = SmartFilterEngine.applyRules(email("INBOX", 1, subject = "Ihre Rechnung Nr. 5"), listOf(rule))
        val miss = SmartFilterEngine.applyRules(email("INBOX", 1, subject = "Merhaba"), listOf(rule))

        assertEquals(SmartCategory.BILLS.id, hit.categoryId)
        assertEquals("Finans", hit.labelsCsv)
        assertEquals(SmartCategory.PRIMARY.id, miss.categoryId)
    }

    @Test
    fun `empty keyword never matches`() {
        val rule = RuleEntity(name = "Bos", conditionField = "SUBJECT", keyword = " , ", targetLabel = "X")

        assertEquals("", SmartFilterEngine.applyRules(email("INBOX", 1), listOf(rule)).labelsCsv)
    }

    @Test
    fun `german bills and mailing lists get a category`() {
        val bill = email("INBOX", 1, subject = "Ihre Rechnung")
        val list = email("INBOX", 2, subject = "Merhaba").copy(isMailingList = true)

        assertEquals(SmartCategory.BILLS.id, SmartFilterEngine.refreshCategory(bill).categoryId)
        assertEquals(SmartCategory.SUBSCRIPTIONS.id, SmartFilterEngine.refreshCategory(list).categoryId)
    }

    @Test
    fun `category chosen by a rule is not replaced`() {
        val chosen = email("INBOX", 1, subject = "Ihre Rechnung").copy(categoryId = SmartCategory.WORK.id)

        assertEquals(SmartCategory.WORK.id, SmartFilterEngine.refreshCategory(chosen).categoryId)
    }
}
