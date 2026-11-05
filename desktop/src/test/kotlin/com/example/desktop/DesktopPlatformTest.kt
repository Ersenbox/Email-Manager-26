package com.example.desktop

import com.example.common.data.EmailAccountEntity
import com.example.common.i18n.I18n
import com.example.common.i18n.t
import com.example.common.service.MailService
import com.example.common.settings.AppSettings
import com.example.common.settings.ThemeMode
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import java.io.File
import java.nio.file.Files
import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class DesktopPlatformTest {
    private val address = "deneme@test.local"
    private val secret = "deneme-sifresi"
    private lateinit var dir: File
    private lateinit var greenMail: GreenMail

    private val account
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
        dir = Files.createTempDirectory("em26").toFile()
        greenMail = GreenMail(ServerSetupTest.IMAPS)
        greenMail.start()
        greenMail.setUser(address, address, secret)
        MailService.sessionPropertyOverrides = mapOf(
            "mail.imaps.ssl.trust" to "*",
            "mail.imaps.ssl.checkserveridentity" to "false"
        )
    }

    @After
    fun stop() {
        MailService.sessionPropertyOverrides = emptyMap()
        greenMail.stop()
        I18n.selectLanguage(I18n.SOURCE_LANGUAGE)
        dir.deleteRecursively()
    }

    private fun deliver(subject: String) {
        val session = Session.getInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("haber@example.org"))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(address))
        message.subject = subject
        message.setText("icerik")
        greenMail.getManagers().userManager.getUser(address).deliver(message)
    }

    @Test
    fun `app starts with default data and syncs through the shared core`() = runBlocking {
        val app = DesktopApp.start(dir)
        deliver("Masaustu posta")

        val rules = app.repository.rules.first()
        val id = app.repository.addOrUpdateAccount(account)
        val stored = app.repository.allAccounts().single { it.id == id }
        val summary = app.repository.syncAllAccountData(stored, isManual = false).getOrThrow()

        assertEquals(3, rules.size)
        assertEquals(1, summary.inboxCount)
        assertEquals("Masaustu posta", app.repository.activeEmails.first().single().subject)
        assertTrue(File(dir, "email_manager_26.db").exists())
        app.close()
    }

    @Test
    fun `data survives a restart and deleted default rules stay deleted`() = runBlocking {
        val first = DesktopApp.start(dir)
        first.repository.addOrUpdateAccount(account)
        first.repository.rules.first().forEach { first.repository.deleteRule(it.id) }
        first.close()

        val second = DesktopApp.start(dir)

        assertEquals(1, second.repository.allAccounts().size)
        assertEquals(0, second.repository.rules.first().size)
        second.close()
    }

    @Test
    fun `password is stored encrypted and still works`() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val app = DesktopApp.start(dir)

        val id = app.repository.addOrUpdateAccount(account)
        val stored = app.repository.allAccounts().single { it.id == id }

        assertTrue(stored.password.startsWith("dpapi1:"))
        assertNotEquals(secret, stored.password)
        assertFalse(stored.password.contains(secret))
        assertEquals(secret, WindowsCredentialCodec.decryptOrPlain(stored.password))
        assertTrue(app.repository.testAccountConnection(stored).isSuccess)
        app.close()
    }

    @Test
    fun `settings and language are kept in a file and translations load`() = runBlocking {
        val first = DesktopApp.start(dir)
        AppSettings.setLanguage("de")
        AppSettings.setThemeMode(ThemeMode.DARK)
        assertEquals("Synchronisieren", t("Eşitle"))
        first.close()

        I18n.selectLanguage("tr")
        val second = DesktopApp.start(dir)

        assertEquals("de", I18n.language)
        assertEquals(ThemeMode.DARK, AppSettings.themeMode.value)
        second.close()
    }
}
