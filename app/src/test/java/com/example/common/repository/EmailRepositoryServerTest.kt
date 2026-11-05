package com.example.common.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.common.bridge.HapticType
import com.example.common.bridge.PlatformBridge
import com.example.common.bridge.PlatformInfo
import com.example.common.bridge.SoundType
import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailDatabase
import com.example.common.data.EmailEntity
import com.example.common.model.PlatformType
import com.example.common.service.MailService
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import java.io.File
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeBridge : PlatformBridge {
    val deleted = mutableSetOf<String>()
    val toasts = mutableListOf<String>()
    val opened = mutableListOf<String>()
    override fun playSound(soundType: SoundType) {}
    override fun triggerHaptic(hapticType: HapticType) {}
    override fun showToastNotification(message: String) { toasts.add(message) }
    override fun getPlatformInfo() = PlatformInfo(PlatformType.ANDROID, "", "", "", "")
    override fun openUrl(url: String) { opened.add(url) }
    override fun shareContent(subject: String, body: String) {}
    override fun addDeletedIdentifier(id: String) { deleted.add(id) }
    override fun isIdentifierDeleted(id: String) = id in deleted
    override fun removeDeletedIdentifier(id: String) { deleted.remove(id) }
    override fun attachmentDirectory(emailId: Long) = File("build/tmp/ek/$emailId")
    override fun openFile(file: File, mimeType: String) {}
}

/** Repository + gerçek Room + yerel GreenMail sunucusu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmailRepositoryServerTest {

    private val address = "deneme@test.local"
    private val secret = "deneme-sifresi"
    private lateinit var greenMail: GreenMail
    private lateinit var user: GreenMailUser
    private lateinit var db: EmailDatabase
    private lateinit var repo: EmailRepository
    private val bridge = FakeBridge()
    private val session: Session = Session.getInstance(Properties())

    private val account = EmailAccountEntity(
        accountName = "Deneme",
        emailAddress = address,
        password = secret,
        imapHost = "127.0.0.1",
        imapPort = ServerSetupTest.IMAPS.port
    )

    @Before
    fun start() {
        java.security.Security.removeProvider("Conscrypt")
        greenMail = GreenMail(ServerSetupTest.IMAPS)
        greenMail.start()
        user = greenMail.setUser(address, address, secret)
        MailService.sessionPropertyOverrides = mapOf(
            "mail.imaps.ssl.trust" to "*",
            "mail.imaps.ssl.checkserveridentity" to "false"
        )
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EmailDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = EmailRepository(db.emailDao(), db.accountDao(), bridge)
        runBlocking { db.accountDao().insertAccount(account) }
    }

    @After
    fun stop() {
        MailService.sessionPropertyOverrides = emptyMap()
        db.close()
        greenMail.stop()
    }

    private fun deliver(subject: String, edit: (MimeMessage) -> Unit = {}) {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("haber@example.org"))
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

    private fun subjects(name: String): List<String> = withStore { store ->
        val folder = store.getFolder(name)
        folder.open(Folder.READ_ONLY)
        val result = folder.messages.map { it.subject }
        folder.close(false)
        result
    }

    private fun createFolder(name: String) = withStore { it.getFolder(name).create(Folder.HOLDS_MESSAGES) }

    private fun count(name: String): Int = withStore { store ->
        val folder = store.getFolder(name)
        folder.open(Folder.READ_ONLY)
        val total = folder.messages.size
        folder.close(false)
        total
    }

    private suspend fun sync() {
        val active = db.accountDao().getActiveAccount()!!
        repo.syncAllAccountData(active, isManual = false).getOrThrow()
    }

    private suspend fun local(subject: String): EmailEntity? =
        db.emailDao().getEmailsByAccount(address).firstOrNull { it.subject == subject }

    @Test
    fun `failed move keeps the email out of trash locally`() = runBlocking {
        deliver("Cop yok")
        sync()
        val email = local("Cop yok")!!

        repo.moveToTrash(email.id)

        assertFalse(local("Cop yok")!!.isTrash)
        assertEquals(1, count("INBOX"))
        assertTrue(bridge.toasts.last(), bridge.toasts.last().contains("yerinde"))
    }

    @Test
    fun `trash then restore follows the server and survives sync`() = runBlocking {
        createFolder("Trash")
        deliver("Gidip gelen")
        sync()
        val id = local("Gidip gelen")!!.id

        repo.moveToTrash(id)
        assertTrue(local("Gidip gelen")!!.isTrash)
        assertEquals(1, count("Trash"))
        sync()
        assertTrue(local("Gidip gelen")!!.isTrash)

        repo.restoreFromTrash(id)
        assertFalse(local("Gidip gelen")!!.isTrash)
        assertEquals(1, count("INBOX"))
        assertEquals(0, count("Trash"))
        sync()
        assertFalse(local("Gidip gelen")!!.isTrash)
        assertEquals("INBOX", local("Gidip gelen")!!.folderName)
    }

    @Test
    fun `archived email is listed and unarchive returns it`() = runBlocking {
        deliver("Arsivlik")
        sync()
        val id = local("Arsivlik")!!.id

        repo.archiveEmail(id)
        assertTrue(local("Arsivlik")!!.isArchived)
        assertEquals(0, count("INBOX"))
        assertEquals(1, count("Archive"))
        sync()
        assertNotNull(local("Arsivlik"))

        repo.unarchiveEmail(id)
        assertFalse(local("Arsivlik")!!.isArchived)
        assertEquals(1, count("INBOX"))
    }

    @Test
    fun `email removed elsewhere disappears locally after sync`() = runBlocking {
        deliver("Kalan")
        deliver("Silinen")
        sync()
        withStore { store ->
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_WRITE)
            folder.messages.first { it.subject == "Silinen" }.setFlag(Flags.Flag.DELETED, true)
            folder.close(true)
        }

        sync()

        assertNull(local("Silinen"))
        assertNotNull(local("Kalan"))
    }

    @Test
    fun `deleted uid does not hide a mail from another folder`() = runBlocking {
        createFolder("Trash")
        createFolder("Spam")
        deliver("Silinecek")
        sync()
        val deleted = local("Silinecek")!!
        repo.deletePermanently(deleted.id)
        assertNull(local("Silinecek"))

        // Spam klasöründe aynı UID numarasıyla yeni posta
        deliver("Spam postasi")
        withStore { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.copyMessages(inbox.messages, store.getFolder("Spam"))
            inbox.messages.forEach { it.setFlag(Flags.Flag.DELETED, true) }
            inbox.close(true)
        }
        sync()

        val spam = local("Spam postasi")
        assertNotNull(spam)
        assertEquals(deleted.serverUid, spam!!.serverUid)
    }

    @Test
    fun `sender cleanup stays restorable`() = runBlocking {
        createFolder("Trash")
        deliver("Bulten 1")
        sync()
        val email = local("Bulten 1")!!

        assertEquals(1, repo.bulkTrashSender(email.senderEmail, email.senderName))
        assertEquals(1, count("Trash"))
        assertTrue(bridge.deleted.isEmpty())

        repo.restoreFromTrash(email.id)
        sync()
        assertFalse(local("Bulten 1")!!.isTrash)
    }

    @Test
    fun `empty trash only touches the active account`() = runBlocking {
        createFolder("Trash")
        val otherTrash = EmailEntity(
            senderName = "x", senderEmail = "x@y.z", subject = "Diger hesap", preview = "", body = "",
            timestamp = 1L, isTrash = true, accountEmail = "diger@test.local"
        )
        db.emailDao().insertEmail(otherTrash)
        deliver("Aktif cop")
        sync()
        repo.moveToTrash(local("Aktif cop")!!.id)

        repo.emptyTrash()

        assertNull(local("Aktif cop"))
        assertEquals(0, count("Trash"))
        assertEquals(1, db.emailDao().getEmailsByAccount("diger@test.local").size)
    }

    @Test
    fun `operations on another account mail never use the active account`() = runBlocking {
        deliver("Aktif")
        sync()
        val foreign = EmailEntity(
            senderName = "x", senderEmail = "x@y.z", subject = "Aktif", preview = "", body = "",
            timestamp = 1L, accountEmail = "diger@test.local", serverUid = 1L, messageId = local("Aktif")!!.messageId
        )
        val id = db.emailDao().insertEmail(foreign)

        repo.moveToTrash(id)

        assertEquals(1, count("INBOX"))
    }

    @Test
    fun `unsubscribe without a link is not reported as done`() = runBlocking {
        deliver("Linksiz")
        sync()
        val email = local("Linksiz")!!

        assertFalse(repo.unsubscribeSender(email.senderEmail))
        assertFalse(local("Linksiz")!!.isUnsubscribed)
    }

    @Test
    fun `plain unsubscribe link opens the browser and is not marked done`() = runBlocking {
        deliver("Linkli") { it.setHeader("List-Unsubscribe", "<https://example.org/cik>") }
        sync()
        val email = local("Linkli")!!

        assertFalse(repo.unsubscribeSender(email.senderEmail))
        assertEquals(listOf("https://example.org/cik"), bridge.opened)
        assertFalse(local("Linkli")!!.isUnsubscribed)
    }

    @Test
    fun `sync stores folder links and keeps one row per email`() = runBlocking {
        deliver("Klasor testi")
        withStore { store ->
            store.getFolder("Etiket").create(Folder.HOLDS_MESSAGES)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            inbox.copyMessages(inbox.messages, store.getFolder("Etiket"))
            inbox.close(false)
        }

        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        val rows = db.emailDao().getAllEmailsList()
        val links = db.emailDao().getFolderLinksByAccount(account.emailAddress)
        assertEquals(1, rows.size)
        assertEquals("INBOX", rows.single().folderName)
        assertEquals(setOf("INBOX", "Etiket"), links.map { it.folderName }.toSet())
        assertTrue(links.all { it.emailId == rows.single().id })
    }

    @Test
    fun `new rule is applied to stored emails`() = runBlocking {
        deliver("Ihre Sonderaktion")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        repo.addRule(
            com.example.common.data.RuleEntity(
                name = "Aktion",
                conditionField = "SUBJECT",
                keyword = "sonderaktion",
                targetLabel = "Etiketli"
            )
        )

        assertTrue(db.emailDao().getAllEmailsList().single().labelsCsv.contains("Etiketli"))
    }

    @Test
    fun `unchanged server is not scanned again and new mail is`() = runBlocking {
        MailService.allowFingerprintWithoutModSeq = true
        try {
            deliver("Ilk")
            val first = repo.syncAllAccountData(account, isManual = false).getOrThrow()
            val linksBefore = db.emailDao().getFolderLinksByAccount(account.emailAddress)

            val second = repo.syncAllAccountData(account, isManual = false).getOrThrow()

            assertEquals(first.folders, second.folders)
            assertEquals(1, second.inboxCount)
            assertEquals(linksBefore, db.emailDao().getFolderLinksByAccount(account.emailAddress))

            deliver("Ikinci")
            val third = repo.syncAllAccountData(account, isManual = false).getOrThrow()

            assertEquals(2, third.inboxCount)
            assertEquals(2, db.emailDao().getAllEmailsList().size)
        } finally {
            MailService.allowFingerprintWithoutModSeq = false
        }
    }

    @Test
    fun `email moved out of inbox loses only its inbox link`() = runBlocking {
        deliver("Tasinacak")
        createFolder("Klasor")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        withStore { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            val message = inbox.messages
            inbox.copyMessages(message, store.getFolder("Klasor"))
            inbox.setFlags(message, Flags(Flags.Flag.DELETED), true)
            inbox.close(true)
        }

        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        val rows = db.emailDao().getAllEmailsList()
        assertEquals(1, rows.size)
        assertEquals("Klasor", rows.single().folderName)
        assertEquals(listOf("Klasor"), db.emailDao().getFolderLinksByAccount(account.emailAddress).map { it.folderName })
    }

    @Test
    fun `restored email is in the inbox list before the next sync`() = runBlocking {
        createFolder("Trash")
        deliver("Geri gelecek")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val id = db.emailDao().getAllEmailsList().single().id
        repo.moveToTrash(id)
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        assertFalse(db.emailDao().getFolderLinksByAccount(account.emailAddress).any { it.folderName == "INBOX" })

        repo.restoreFromTrash(id)

        val links = db.emailDao().getFolderLinksByAccount(account.emailAddress)
        assertTrue(links.any { it.folderName == "INBOX" && it.emailId == id })
        assertFalse(db.emailDao().getEmailById(id)!!.isTrash)
    }

    @Test
    fun `unarchived email is in the inbox list before the next sync`() = runBlocking {
        createFolder("Archive")
        deliver("Arsivden donecek")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val id = db.emailDao().getAllEmailsList().single().id
        repo.archiveEmail(id)
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val archivedId = db.emailDao().getAllEmailsList().single().id

        repo.unarchiveEmail(archivedId)

        assertTrue(db.emailDao().getFolderLinksByAccount(account.emailAddress).any { it.folderName == "INBOX" && it.emailId == archivedId })
        assertFalse(db.emailDao().getEmailById(archivedId)!!.isArchived)
    }

    @Test
    fun `deleted email waits in the hold folder and can be restored`() = runBlocking {
        repo.holdFolderFor = { com.example.common.service.MailFolderNames.HOLD_FOLDER }
        createFolder("Trash")
        deliver("Bekleyecek")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val id = db.emailDao().getAllEmailsList().single().id

        repo.moveToTrash(id)
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        val held = db.emailDao().getAllEmailsList().single()
        assertTrue(held.isTrash)
        assertEquals("EM26/Silinecekler", held.folderName)
        assertEquals(1, count("EM26/Silinecekler"))
        assertEquals(0, count("Trash"))
        assertEquals(0, count("INBOX"))

        repo.restoreFromTrash(held.id)
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        assertEquals(1, count("INBOX"))
        assertEquals(0, count("EM26/Silinecekler"))
        assertFalse(db.emailDao().getAllEmailsList().single().isTrash)
    }

    @Test
    fun `permanent delete from hold folder removes it everywhere`() = runBlocking {
        repo.holdFolderFor = { com.example.common.service.MailFolderNames.HOLD_FOLDER }
        createFolder("Trash")
        deliver("Kalici silinecek")
        deliver("Kalacak")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val id = db.emailDao().getAllEmailsList().single { it.subject == "Kalici silinecek" }.id
        repo.moveToTrash(id)
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val held = db.emailDao().getAllEmailsList().single { it.isTrash }

        repo.deletePermanentlyBatch(listOf(held.id))

        assertEquals(0, count("EM26/Silinecekler"))
        assertEquals(0, count("Trash"))
        assertEquals(1, count("INBOX"))
        assertEquals("Kalacak", db.emailDao().getAllEmailsList().single().subject)
    }

    @Test
    fun `newsletter gets a label and stays in the inbox and is labelled once`() = runBlocking {
        repo.autoLabelsEnabled = { true }
        deliver("Rabatt nur heute") { it.setHeader("List-Unsubscribe", "<mailto:off@example.org>") }
        deliver("Normal posta")

        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        assertEquals(1, count("EM26/Reklam"))
        assertEquals(2, count("INBOX"))
        val rows = db.emailDao().getAllEmailsList()
        assertEquals(2, rows.size)
        assertEquals("EM26/Reklam", rows.single { it.subject.startsWith("Rabatt") }.autoFolder)
        assertNull(rows.single { it.subject == "Normal posta" }.autoFolder)
    }

    @Test
    fun `changing the folder of a sender moves the label and is remembered`() = runBlocking {
        repo.autoLabelsEnabled = { true }
        deliver("Rabatt nur heute") { it.setHeader("List-Unsubscribe", "<mailto:off@example.org>") }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val sender = db.emailDao().getAllEmailsList().single().senderEmail

        repo.setSenderFolder(sender, com.example.common.filter.AutoFolder.BILLS)

        assertEquals(0, count("EM26/Reklam"))
        assertEquals(1, count("EM26/Faturalar"))
        assertEquals(1, count("INBOX"))
    }

    @Test
    fun `old spam is only deleted when the setting is on`() = runBlocking {
        createFolder("Spam")
        createFolder("Trash")
        val old = java.util.Date(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000)
        deliver("Eski spam") { it.sentDate = old }
        deliver("Yeni spam")
        withStore { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.copyMessages(inbox.messages, store.getFolder("Spam"))
            inbox.setFlags(inbox.messages, Flags(Flags.Flag.DELETED), true)
            inbox.close(true)
        }

        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        assertEquals(2, count("Spam"))

        repo.spamDeleteDays = { 7 }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        assertEquals(1, count("Spam"))
        assertEquals(0, count("Trash"))
        assertEquals(listOf("Yeni spam"), db.emailDao().getAllEmailsList().map { it.subject })
    }

    @Test
    fun `blocked sender is removed now and on the next sync`() = runBlocking {
        createFolder("Trash")
        deliver("Ilk posta")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val sender = db.emailDao().getAllEmailsList().single().senderEmail

        repo.blockSender(sender, "Gonderen")
        assertEquals(0, count("INBOX"))

        deliver("Sonraki posta")
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        assertEquals(0, count("INBOX"))
        assertEquals(2, count("Trash"))
    }

    @Test
    fun `old ads move only when enabled and starred ones stay`() = runBlocking {
        repo.autoLabelsEnabled = { true }
        createFolder("Trash")
        val old = java.util.Date(System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000)
        deliver("Rabatt eski") { it.sentDate = old; it.setHeader("List-Unsubscribe", "<mailto:off@example.org>") }
        deliver("Rabatt yildizli") { it.sentDate = old; it.setHeader("List-Unsubscribe", "<mailto:off@example.org>") }
        deliver("Rabatt yeni") { it.setHeader("List-Unsubscribe", "<mailto:off@example.org>") }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        assertEquals(3, count("INBOX"))

        val starred = db.emailDao().getAllEmailsList().single { it.subject == "Rabatt yildizli" }
        repo.toggleStar(starred.id)
        repo.adCleanupDays = { 30 }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        assertEquals(listOf("Rabatt yeni", "Rabatt yildizli").sorted(), subjects("INBOX").sorted())
        assertEquals(1, count("Trash"))
    }

    @Test
    fun `only new important inbox mail is reported and never on first sync`() = runBlocking {
        createFolder("Wichtig")
        val reported = mutableListOf<String>()
        repo.onNewImportantMail = { list -> reported.addAll(list.map { it.subject }) }
        deliver("Eski onemli")
        withStore { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            inbox.copyMessages(inbox.messages, store.getFolder("Wichtig"))
            inbox.close(false)
        }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        assertTrue(reported.isEmpty())

        deliver("Yeni onemli")
        deliver("Yeni siradan")
        withStore { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val message = inbox.messages.first { it.subject == "Yeni onemli" }
            inbox.copyMessages(arrayOf(message), store.getFolder("Wichtig"))
            inbox.close(false)
        }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        assertEquals(listOf("Yeni onemli"), reported)
    }

    @Test
    fun `bulk unsubscribe skips senders that need a web page`() = runBlocking {
        deliver("Bulten") { it.setHeader("List-Unsubscribe", "<https://example.org/cik>") }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()
        val sender = db.emailDao().getAllEmailsList().single().senderEmail

        val result = repo.unsubscribeSenders(listOf(sender))

        assertEquals(0, result.done)
        assertEquals(1, result.needsPage)
        assertTrue(bridge.opened.isEmpty())
    }

    @Test
    fun `weekly report counts this week only`() = runBlocking {
        repo.autoLabelsEnabled = { true }
        val old = java.util.Date(System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000)
        deliver("Rabatt yeni") { it.setHeader("List-Unsubscribe", "<mailto:off@example.org>") }
        deliver("Normal") { }
        deliver("Eski") { it.sentDate = old }
        repo.syncAllAccountData(account, isManual = false).getOrThrow()

        val report = repo.weeklyReport()

        assertEquals(2, report.received)
        assertEquals(1, report.ads)
    }
}
