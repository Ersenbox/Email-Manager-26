package com.example.common.service

import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailEntity
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import java.io.File
import java.nio.file.Files
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Yerel GreenMail IMAP sunucusuna karşı gerçek MailService çağrıları. */
class MailServiceServerTest {

    private val address = "deneme@test.local"
    private val secret = "deneme-sifresi"
    private lateinit var greenMail: GreenMail
    private lateinit var user: GreenMailUser
    private val session: Session = Session.getInstance(Properties())

    private val account: EmailAccountEntity
        get() = EmailAccountEntity(
            accountName = "Deneme",
            emailAddress = address,
            password = secret,
            imapHost = "127.0.0.1",
            imapPort = ServerSetupTest.IMAPS.port,
            smtpHost = "127.0.0.1",
            smtpPort = ServerSetupTest.SMTPS.port
        )

    @Before
    fun start() {
        // Robolectric Conscrypt'i ekliyor; GreenMail'in deneme sunucusu JDK TLS sağlayıcısı ister
        java.security.Security.removeProvider("Conscrypt")
        greenMail = GreenMail(arrayOf(ServerSetupTest.IMAPS, ServerSetupTest.SMTPS))
        greenMail.start()
        user = greenMail.setUser(address, address, secret)
        MailService.sessionPropertyOverrides = mapOf(
            "mail.imaps.ssl.trust" to "*",
            "mail.imaps.ssl.checkserveridentity" to "false",
            "mail.smtp.ssl.trust" to "*",
            "mail.smtp.ssl.checkserveridentity" to "false"
        )
    }

    @After
    fun stop() {
        MailService.sessionPropertyOverrides = emptyMap()
        greenMail.stop()
    }

    private fun deliver(subject: String, from: String = "haber@example.org", edit: (MimeMessage) -> Unit = {}) {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(address))
        message.subject = subject
        message.setText("icerik")
        edit(message)
        message.saveChanges()
        user.deliver(message)
    }

    private fun <T> withStore(block: (Store) -> T): T {
        val props = Properties().apply {
            put("mail.imaps.ssl.trust", "*")
            put("mail.imaps.ssl.checkserveridentity", "false")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect("127.0.0.1", ServerSetupTest.IMAPS.port, address, secret)
        try {
            return block(store)
        } finally {
            store.close()
        }
    }

    private fun createFolder(name: String) = withStore { it.getFolder(name).create(Folder.HOLDS_MESSAGES) }

    private fun count(name: String): Int = withStore { store ->
        val folder = store.getFolder(name)
        if (!folder.exists()) return@withStore -1
        folder.open(Folder.READ_ONLY)
        val total = folder.messages.count { !it.isSet(Flags.Flag.DELETED) }
        folder.close(false)
        total
    }

    private fun subjects(name: String): List<String> = withStore { store ->
        val folder = store.getFolder(name)
        folder.open(Folder.READ_ONLY)
        val result = folder.messages.map { it.subject }
        folder.close(false)
        result
    }

    private fun inbox(): List<EmailEntity> = runBlocking {
        MailService.fetchEmails(account, "INBOX", 60).getOrThrow()
    }

    @Test
    fun `same subject never selects another email`() = runBlocking {
        createFolder("Trash")
        deliver("Ayni konu")
        val ghost = inbox().single().copy(messageId = "<baska@example.org>", serverUid = 99999L)

        val outcome = MailService.deleteOrTrashEmailOnServer(account, ghost, moveToTrash = false).getOrThrow()

        assertFalse(outcome.found)
        assertEquals(1, count("INBOX"))
    }

    @Test
    fun `missing trash folder keeps the email`() = runBlocking {
        deliver("Korunacak")
        val email = inbox().single()

        val result = MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = true)

        assertTrue(result.isFailure)
        assertEquals(1, count("INBOX"))
    }

    @Test
    fun `batch move without trash folder keeps emails`() = runBlocking {
        deliver("Bir")
        deliver("Iki")

        val result = MailService.batchMoveToTrashOnServer(account, inbox())

        assertTrue(result.isFailure)
        assertEquals(2, count("INBOX"))
    }

    @Test
    fun `move to trash keeps the email in trash`() = runBlocking {
        createFolder("Trash")
        deliver("Cope")
        val outcome = MailService.deleteOrTrashEmailOnServer(account, inbox().single(), moveToTrash = true).getOrThrow()

        assertTrue(outcome.found)
        assertEquals("Trash", outcome.targetFolder)
        assertEquals(0, count("INBOX"))
        assertEquals(listOf("Cope"), subjects("Trash"))
    }

    @Test
    fun `folder containing bin is not treated as trash`() = runBlocking {
        createFolder("Trash")
        createFolder("Kabine")
        deliver("Klasorde")
        val email = inbox().single()
        val moved = withStore { store ->
            val source = store.getFolder("INBOX")
            source.open(Folder.READ_WRITE)
            source.copyMessages(source.messages, store.getFolder("Kabine"))
            source.messages.forEach { it.setFlag(Flags.Flag.DELETED, true) }
            source.close(true)
            email.copy(folderName = "Kabine", serverUid = 0L)
        }

        MailService.deleteOrTrashEmailOnServer(account, moved, moveToTrash = true).getOrThrow()

        assertEquals(0, count("Kabine"))
        assertEquals(listOf("Klasorde"), subjects("Trash"))
    }

    @Test
    fun `move to trash on an email already in trash does not delete it`() = runBlocking {
        createFolder("Trash")
        deliver("Zaten copte")
        val email = inbox().single()
        val outcome = MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = true).getOrThrow()
        val inTrash = email.copy(folderName = outcome.targetFolder!!, serverUid = 0L)

        MailService.deleteOrTrashEmailOnServer(account, inTrash, moveToTrash = true).getOrThrow()

        assertEquals(1, count("Trash"))
    }

    @Test
    fun `restore moves the email back to inbox on the server`() = runBlocking {
        createFolder("Trash")
        deliver("Geri gel")
        val email = inbox().single()
        MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = true).getOrThrow()

        val outcome = MailService.moveEmailOnServer(account, email.copy(folderName = "Trash", serverUid = 0L), MoveTarget.INBOX).getOrThrow()

        assertTrue(outcome.found)
        assertEquals(0, count("Trash"))
        assertEquals(listOf("Geri gel"), subjects("INBOX"))
    }

    @Test
    fun `archive moves on the server and unarchive returns it`() = runBlocking {
        deliver("Arsiv")
        val email = inbox().single()

        val archived = MailService.moveEmailOnServer(account, email, MoveTarget.ARCHIVE).getOrThrow()
        assertTrue(archived.found)
        assertEquals("Archive", archived.targetFolder)
        assertEquals(0, count("INBOX"))
        assertEquals(1, count("Archive"))

        MailService.moveEmailOnServer(account, email.copy(folderName = "Archive", serverUid = 0L), MoveTarget.INBOX).getOrThrow()
        assertEquals(1, count("INBOX"))
        assertEquals(0, count("Archive"))
    }

    @Test
    fun `permanent delete removes the email everywhere`() = runBlocking {
        createFolder("Trash")
        deliver("Kalici")

        val outcome = MailService.deleteOrTrashEmailOnServer(account, inbox().single(), moveToTrash = false).getOrThrow()

        assertTrue(outcome.found)
        assertEquals(0, count("INBOX"))
        assertEquals(0, count("Trash"))
    }

    @Test
    fun `sender cleanup only matches the exact address`() = runBlocking {
        createFolder("Trash")
        deliver("Hedef", from = "info@a.com")
        deliver("Benzer", from = "xinfo@a.com.tr")

        val moved = MailService.bulkTrashSenderOnServer(account, "info@a.com").getOrThrow()

        assertEquals(1, moved)
        assertEquals(listOf("Benzer"), subjects("INBOX"))
        assertEquals(listOf("Hedef"), subjects("Trash"))
    }

    @Test
    fun `sync reports emails removed on the server`() = runBlocking {
        createFolder("Trash")
        deliver("Kalacak")
        deliver("Baska yerden silinecek")
        val local = inbox()
        withStore { store ->
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_WRITE)
            folder.messages.first { it.subject.startsWith("Baska") }.setFlag(Flags.Flag.DELETED, true)
            folder.close(true)
        }

        val result = MailService.syncAllFoldersFast(account, local).getOrThrow()

        val removed = local.first { it.subject.startsWith("Baska") }
        assertEquals(setOf(removed.serverUid), result.missingUids["INBOX"])
    }

    @Test
    fun `sync marks an email moved to trash outside the app`() = runBlocking {
        createFolder("Trash")
        deliver("Webden cope")
        val local = inbox().map { it.copy(id = 7L) }
        MailService.deleteOrTrashEmailOnServer(account, local.single(), moveToTrash = true).getOrThrow()

        val result = MailService.syncAllFoldersFast(account, local).getOrThrow()

        val synced = result.emails.single()
        assertTrue(synced.isTrash)
        assertEquals("Trash", synced.folderName)
    }

    @Test
    fun `every message in every folder is synced without a limit`() = runBlocking {
        repeat(70) { deliver("Posta $it") }
        createFolder("Gesendet")
        createFolder("Spam")
        withStore { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val first = inbox.getMessages(1, 3)
            inbox.copyMessages(first, store.getFolder("Spam"))
            inbox.copyMessages(arrayOf(first[0]), store.getFolder("Gesendet"))
            inbox.close(false)
        }

        val result = MailService.syncAllFoldersFast(account).getOrThrow()

        assertEquals(70, result.inboxCount)
        assertEquals(3, result.spamCount)
        assertEquals(70, result.memberships.count { it.folderName == "INBOX" })
        assertEquals(3, result.memberships.count { it.folderName == "Spam" })
        assertEquals(1, result.memberships.count { it.folderName == "Gesendet" })
        // Aynı posta üç klasörde olsa da bir kez saklanır
        assertEquals(70, result.emails.size)
        assertEquals(setOf("INBOX", "Spam", "Gesendet"), result.scannedFolders)
        assertEquals(70, result.folders.first { it.fullName == "INBOX" }.totalCount)
    }

    @Test
    fun `same uid in another folder is not mistaken for a known email`() = runBlocking {
        deliver("Eski")
        val first = MailService.syncAllFoldersFast(account).getOrThrow().emails.single()
        val stored = first.copy(id = 9L, folderName = "Baska", messageId = "<baska@test>")

        val synced = MailService.syncAllFoldersFast(account, listOf(stored)).getOrThrow()

        assertEquals("Eski", synced.emails.single().subject)
        assertEquals(0L, synced.emails.single().id)
    }

    @Test
    fun `reply carries the real message id so gmail keeps the thread`() = runBlocking {
        MailService.sendEmail(account, address, "Re: Konu", "yanit", inReplyToMessageId = "<asil@test>").getOrThrow()

        val sent = greenMail.receivedMessages.single()
        assertEquals("<asil@test>", sent.getHeader("In-Reply-To").single())
        assertEquals("<asil@test>", sent.getHeader("References").single())
    }

    @Test
    fun `new email has no reply headers`() = runBlocking {
        MailService.sendEmail(account, address, "Yeni", "metin").getOrThrow()

        assertEquals(null, greenMail.receivedMessages.single().getHeader("In-Reply-To"))
    }

    @Test
    fun `batch read change uses the real folder and only selected emails`() = runBlocking {
        deliver("Bir")
        deliver("Iki")
        deliver("Uc")
        val emails = inbox()

        val updated = MailService.batchUpdateReadOnServer(
            account, emails.filter { it.subject != "Uc" }, isRead = true
        ).getOrThrow()

        assertEquals(2, updated)
        val seen = withStore { store ->
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val result = folder.messages.associate { it.subject to it.isSet(Flags.Flag.SEEN) }
            folder.close(false)
            result
        }
        assertEquals(mapOf("Bir" to true, "Iki" to true, "Uc" to false), seen)
    }

    @Test
    fun `batch delete only touches the trash folder`() = runBlocking {
        createFolder("Trash")
        deliver("Gelende kalacak")
        deliver("Copte silinecek")
        val emails = inbox()
        val toTrash = emails.single { it.subject == "Copte silinecek" }
        MailService.deleteOrTrashEmailOnServer(account, toTrash).getOrThrow()

        val result = MailService.batchDeleteFromTrashOnServer(account, emails).getOrThrow()

        assertEquals(listOf(toTrash.id), result.movedIds)
        assertEquals(0, count("Trash"))
        assertEquals(listOf("Gelende kalacak"), subjects("INBOX"))
    }

    @Test
    fun `sync stops downloading new mail when the budget is used up`() = runBlocking {
        deliver("Yeni 1")
        deliver("Yeni 2")

        val result = MailService.syncAllFoldersFast(account, emptyList(), budgetMs = 0L).getOrThrow()

        assertTrue(result.isPartial)
        assertTrue(result.emails.isEmpty())
    }

    @Test
    fun `sync with enough time is complete and html preview is clean`() = runBlocking {
        deliver("Html") {
            it.setContent("<style>@media all{.a{color:red}}</style><p>Merhaba &amp; selam</p>", "text/html; charset=utf-8")
        }

        val result = MailService.syncAllFoldersFast(account).getOrThrow()

        assertFalse(result.isPartial)
        assertEquals("Merhaba & selam", result.emails.single().preview)
    }

    @Test
    fun `unsubscribe headers are read from the server`() = runBlocking {
        deliver("Bulten") {
            it.setHeader("List-Unsubscribe", "<https://example.org/u/1>")
            it.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
        }

        val options = MailService.fetchUnsubscribeOptions(account, inbox().single()).getOrThrow()

        assertTrue(options.oneClick)
        assertEquals("https://example.org/u/1", options.httpsUrl)
    }

    @Test
    fun `attachments are downloaded with safe names`() = runBlocking {
        deliver("Ekli") { message ->
            val text = MimeBodyPart().apply { setText("icerik") }
            val file = MimeBodyPart().apply {
                dataHandler = DataHandler(ByteArrayDataSource("PDF".toByteArray(), "application/pdf"))
                fileName = "../rapor.pdf"
                disposition = "attachment"
            }
            message.setContent(MimeMultipart(text, file))
        }
        val dir: File = Files.createTempDirectory("ekler").toFile()

        val saved = MailService.downloadAttachments(account, inbox().single(), dir).getOrThrow().single()

        assertEquals("rapor.pdf", saved.name)
        assertEquals(dir.canonicalPath, saved.file.parentFile!!.canonicalPath)
        assertEquals("PDF", saved.file.readText())
        dir.deleteRecursively()
        Unit
    }

    @Test
    fun `untrusted certificate stops the connection`() = runBlocking {
        MailService.sessionPropertyOverrides = emptyMap()

        val result = MailService.testConnection(account)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("sertifika"))
    }
}
