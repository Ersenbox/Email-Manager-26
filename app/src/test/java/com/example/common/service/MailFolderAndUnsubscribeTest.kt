package com.example.common.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailFolderAndUnsubscribeTest {

    @Test
    fun `connection failure is shown in turkish`() {
        val error = Exception("Couldn't connect to host, port: imap.gmail.com, 993; timeout 8000")

        assertEquals(
            "Sunucuya bağlanılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.",
            MailService.parseFriendlyMailError(error, "test@gmail.com")
        )
    }

    @Test
    fun `folders that only contain bin are not trash`() {
        listOf("Kabine", "Robin", "Binance", "Arbeit/Bindung", "Combined").forEach {
            assertFalse(it, MailFolderNames.isTrash(it))
        }
    }

    @Test
    fun `real trash folders are recognised`() {
        listOf("Trash", "[Gmail]/Papierkorb", "[Gmail]/Bin", "INBOX.Trash", "[Gmail]/Çöp Kutusu", "Deleted Items").forEach {
            assertTrue(it, MailFolderNames.isTrash(it))
        }
    }

    @Test
    fun `one click needs https url and post header`() {
        val options = UnsubscribeParser.parse(
            "<mailto:off@example.org?subject=stop>, <https://example.org/u/1>",
            "List-Unsubscribe=One-Click"
        )
        assertTrue(options.oneClick)
        assertEquals("https://example.org/u/1", options.httpsUrl)
        assertEquals("off@example.org", options.mailto?.address)
        assertEquals("stop", options.mailto?.subject)
    }

    @Test
    fun `http url is not used for one click`() {
        val options = UnsubscribeParser.parse("<http://example.org/u>", "List-Unsubscribe=One-Click")
        assertFalse(options.oneClick)
        assertNull(options.httpsUrl)
        assertEquals("http://example.org/u", options.browserUrl)
    }

    @Test
    fun `unsafe fallback scheme is rejected`() {
        assertTrue(UnsubscribeParser.parse(null, null, "javascript:alert(1)").isEmpty)
    }

    @Test
    fun `attachment names cannot leave the target folder`() {
        assertEquals("passwd", MailService.safeAttachmentName("../../etc/passwd", 0))
        assertEquals("a.pdf", MailService.safeAttachmentName("C:\\temp\\a.pdf", 0))
        assertEquals("ek_1", MailService.safeAttachmentName("..", 0))
    }
}
