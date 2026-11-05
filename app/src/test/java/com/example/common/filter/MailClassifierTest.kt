package com.example.common.filter

import com.example.common.data.EmailEntity
import com.example.common.model.SmartCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MailClassifierTest {

    private fun email(
        sender: String = "a@example.org",
        subject: String = "Konu",
        category: SmartCategory = SmartCategory.PRIMARY,
        list: Boolean = false
    ) = EmailEntity(
        senderName = "A",
        senderEmail = sender,
        subject = subject,
        preview = "",
        body = "",
        timestamp = 1L,
        categoryId = category.id,
        isMailingList = list
    )

    @Test
    fun `social networks are recognised by domain only`() {
        assertEquals(AutoFolder.SOCIAL, MailClassifier.classify(email("x@mail.instagram.com")))
        assertEquals(AutoFolder.SOCIAL, MailClassifier.classify(email("notification@facebookmail.com")))
        assertNull(MailClassifier.classify(email("instagram.com@evil.example")))
        assertNull(MailClassifier.classify(email("x@notinstagram.com")))
    }

    @Test
    fun `lists are split into ads and newsletters`() {
        assertEquals(AutoFolder.ADS, MailClassifier.classify(email(subject = "20% Rabatt heute", list = true)))
        assertEquals(AutoFolder.ADS, MailClassifier.classify(email(subject = "Büyük indirim", list = true)))
        assertEquals(AutoFolder.NEWSLETTER, MailClassifier.classify(email(subject = "Haftalık haberler", list = true)))
    }

    @Test
    fun `personal email gets no folder even with an ad word`() {
        assertNull(MailClassifier.classify(email(subject = "Rabatt Frage")))
    }

    @Test
    fun `bills and parcels use their category`() {
        assertEquals(AutoFolder.BILLS, MailClassifier.classify(email(category = SmartCategory.BILLS)))
        assertEquals(AutoFolder.PACKAGES, MailClassifier.classify(email(category = SmartCategory.PACKAGES)))
    }

    @Test
    fun `user choice wins over detection`() {
        val social = email("x@mail.instagram.com")
        assertEquals(AutoFolder.BILLS, MailClassifier.classify(social, AutoFolder.BILLS.serverName))
        assertNull(MailClassifier.classify(social, MailClassifier.NO_FOLDER))
    }
}
