package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.common.data.EmailAccountEntity
import com.example.common.security.CredentialCipher
import com.example.common.service.MailService
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android Keystore ve TLS kontrolü gerçek Android çalışma ortamında denenir. Hesap gerekmez. */
@RunWith(AndroidJUnit4::class)
class SecurityOnDeviceTest {

    private fun account(host: String) = EmailAccountEntity(
        accountName = "Deneme",
        emailAddress = "olmayan.hesap.deneme@gmail.com",
        password = "yanlis-sifre",
        imapHost = host
    )

    @Test
    fun passwordIsEncryptedWithKeystore() {
        val encrypted = CredentialCipher.encrypt("abcdefghijklmnop")

        assertTrue(CredentialCipher.isEncrypted(encrypted))
        assertFalse(encrypted.contains("abcdefghijklmnop"))
        assertNotEquals(encrypted, CredentialCipher.encrypt("abcdefghijklmnop"))
        assertEquals("abcdefghijklmnop", CredentialCipher.decryptOrPlain(encrypted))
    }

    @Test
    fun oldPlainPasswordStillReads() {
        assertEquals("eski-sifre", CredentialCipher.decryptOrPlain("eski-sifre"))
    }

    @Test
    fun corruptedPasswordReturnsEmpty() {
        assertEquals("", CredentialCipher.decryptOrPlain("enc1:bozuk:veri"))
    }

    @Test
    fun validGmailCertificateIsAccepted() = runBlocking {
        val message = MailService.testConnection(account("imap.gmail.com")).exceptionOrNull()?.message.orEmpty()

        assertFalse(message, message.contains("sertifika", ignoreCase = true))
        assertTrue(message, message.contains("Gmail") || message.contains("Google"))
    }

    @Test
    fun certificateForAnotherHostIsRejected() = runBlocking {
        val ip = withContext(Dispatchers.IO) { InetAddress.getByName("imap.gmail.com").hostAddress!! }

        val message = MailService.testConnection(account(ip)).exceptionOrNull()?.message.orEmpty()

        assertTrue(message, message.contains("sertifika", ignoreCase = true))
    }
}
