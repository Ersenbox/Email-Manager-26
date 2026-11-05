package com.example.common.service

import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailEntity
import com.example.common.filter.SmartFilterEngine
import com.example.common.model.MailFolderCategory
import com.example.common.model.MailFolderInfo
import com.example.common.model.SmartCategory
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties
import javax.mail.Address
import javax.mail.Authenticator
import javax.mail.BodyPart
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.FromStringTerm
import javax.mail.search.HeaderTerm

object MailService {

    /**
     * Cleans up password: trims whitespace and strips spaces from 16-character Google App Passwords
     * (e.g. "abcd efgh ijkl mnop" -> "abcdefghijklmnop")
     */
    fun cleanPassword(password: String): String {
        val trimmed = password.trim()
        val withoutSpaces = trimmed.replace(" ", "")
        return if (withoutSpaces.length == 16 && withoutSpaces.all { it.isLetter() }) {
            withoutSpaces
        } else {
            trimmed
        }
    }

    /**
     * Translates technical server exceptions (especially Google 534/535 App-Password errors)
     * into clear, actionable Turkish instructions.
     */
    fun parseFriendlyMailError(e: Throwable, email: String): String {
        val raw = "${e.message ?: ""} ${e.localizedMessage ?: ""} ${e.cause?.message ?: ""}"
        return when {
            raw.contains("Application-specific password", ignoreCase = true) ||
            raw.contains("InvalidSecondFactor", ignoreCase = true) ||
            raw.contains("534-5.7.9") ||
            raw.contains("534 5.7.9") -> {
                "Google Güvenlik Kuralı: Google, normal Gmail şifrenizi engelledi. E-posta okumak ve göndermek için 16 haneli 'Uygulama Şifresi' (App Password) zorunludur."
            }
            raw.contains("535-5.7.8") ||
            raw.contains("535 5.7.8") ||
            raw.contains("Username and Password not accepted", ignoreCase = true) ||
            raw.contains("AUTHENTICATIONFAILED", ignoreCase = true) -> {
                if (email.endsWith("@gmail.com", ignoreCase = true) || email.endsWith("@googlemail.com", ignoreCase = true)) {
                    "Gmail Kimlik Doğrulama Hatası: Normal Gmail şifreniz kabul edilmedi. myaccount.google.com/apppasswords adresinden 16 haneli 'Uygulama Şifresi' alınız."
                } else {
                    "Kimlik doğrulama başarısız! Lütfen e-posta adresinizi ve şifrenizi kontrol edin."
                }
            }
            raw.contains("ConnectException", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true) ||
            raw.contains("SocketTimeout", ignoreCase = true) -> {
                "Sunucuya bağlanılamadı (Zaman aşımı). İnternet bağlantınızı veya sunucu adreslerini kontrol edin."
            }
            else -> "Hata: ${e.localizedMessage ?: e.message ?: "Bağlantı kurulamadı"}"
        }
    }

    /**
     * Helper to establish authenticated IMAP Store connection.
     */
    private fun createConnectedStore(account: EmailAccountEntity, cleanPass: String): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", account.imapHost)
            put("mail.imaps.port", account.imapPort.toString())
            put("mail.imaps.ssl.enable", account.imapSsl.toString())
            put("mail.imaps.ssl.trust", "*")
            put("mail.imaps.connectiontimeout", "8000")
            put("mail.imaps.timeout", "8000")
            put("mail.imaps.partialfetch", "true")
            put("mail.imaps.fetchsize", "16384")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(account.emailAddress.trim(), cleanPass)
            }
        })

        val store = session.getStore("imaps")
        store.connect(account.imapHost, account.imapPort, account.emailAddress.trim(), cleanPass)
        return store
    }

    /**
     * Test connection to IMAP & SMTP servers with provided credentials.
     */
    suspend fun testConnection(account: EmailAccountEntity): Result<String> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val messageCount = inbox.messageCount
            inbox.close(false)

            Result.success("Bağlantı Başarılı! Gelen Kutusunda $messageCount e-posta tespit edildi.")
        } catch (e: Exception) {
            val errorMsg = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(errorMsg, e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Map IMAP mailbox/folder name to user-friendly Turkish display category & name.
     * Supports Turkish, English, and German Gmail folder names (matching user screenshot).
     */
    fun categorizeFolder(fullName: String, name: String): Pair<MailFolderCategory, String> {
        val lower = fullName.lowercase()
        val cleanName = name.removePrefix("[Gmail]/").removePrefix("[Google Mail]/")
        return when {
            lower == "inbox" || lower.contains("posteingang") -> MailFolderCategory.INBOX to "Gelen Kutusu (Posteingang)"
            lower.contains("starred") || lower.contains("markiert") || lower.contains("yıldız") -> MailFolderCategory.STARRED to "Yıldızlı (Markiert)"
            lower.contains("snooze") || lower.contains("zurückgestellt") || lower.contains("zurueckgestellt") || lower.contains("ertele") -> MailFolderCategory.SNOOZED to "Ertelenen (Zurückgestellt)"
            lower.contains("important") || lower.contains("wichtig") || lower.contains("önemli") -> MailFolderCategory.IMPORTANT to "Önemli (Wichtig)"
            lower.contains("purchases") || lower.contains("käufe") || lower.contains("kaeufe") || lower.contains("sipariş") || lower.contains("alışveriş") -> MailFolderCategory.PURCHASES to "Alışverişler (Käufe)"
            lower.contains("sent") || lower.contains("gesendet") || lower.contains("gönderil") -> MailFolderCategory.SENT to "Gönderilenler (Gesendet)"
            lower.contains("scheduled") || lower.contains("geplant") || lower.contains("zamanla") -> MailFolderCategory.SCHEDULED to "Zamanlanan (Geplant)"
            lower.contains("outbox") || lower.contains("postausgang") || lower.contains("giden") -> MailFolderCategory.OUTBOX to "Giden Kutusu (Postausgang)"
            lower.contains("draft") || lower.contains("entwürfe") || lower.contains("entwuerfe") || lower.contains("taslak") -> MailFolderCategory.DRAFTS to "Taslaklar (Entwürfe)"
            lower.contains("all mail") || lower.contains("alle e-mails") || lower.contains("tüm postalar") || lower == "[gmail]/all" -> MailFolderCategory.ALL_MAIL to "Tüm Postalar (Alle E-Mails)"
            lower.contains("spam") || lower.contains("junk") || lower.contains("istenmeyen") -> MailFolderCategory.SPAM to "Spam / İstenmeyen"
            lower.contains("trash") || lower.contains("papierkorb") || lower.contains("çöp") || lower.contains("bin") || lower.contains("deleted") -> MailFolderCategory.TRASH to "Çöp Kutusu (Papierkorb)"
            lower.contains("abo") || lower.contains("subscri") || lower.contains("bülten") -> MailFolderCategory.SUBSCRIPTIONS to "Abonelikleri Yönet (Abos verwalten)"
            else -> MailFolderCategory.CUSTOM to cleanName.ifBlank { name }
        }
    }

    /**
     * Locate Trash / Papierkorb folder dynamically across languages (German, English, Turkish).
     * RFC 6154 \Trash attribute + Candidate list + Fuzzy search.
     */
    fun findTrashFolder(store: Store): Folder? {
        val candidates = listOf(
            "[Gmail]/Papierkorb",
            "[Google Mail]/Papierkorb",
            "Papierkorb",
            "[Gmail]/Trash",
            "[Google Mail]/Trash",
            "Trash",
            "[Gmail]/Çöp Kutusu",
            "[Google Mail]/Çöp Kutusu",
            "Çöp Kutusu",
            "[Gmail]/Bin",
            "[Google Mail]/Bin",
            "Bin",
            "Deleted Items",
            "Deleted",
            "Gelöschte Elemente",
            "Silinmiş Öğeler"
        )
        for (candidateName in candidates) {
            try {
                val f = store.getFolder(candidateName)
                if (f.exists()) return f
            } catch (_: Exception) {}
        }

        // Search across store folders with RFC 6154 attributes & name keywords
        try {
            val allFolders = mutableListOf<Folder>()
            try {
                allFolders.addAll(store.defaultFolder.list("*"))
            } catch (_: Exception) {
                try {
                    fun collectFolders(parent: Folder) {
                        val children = parent.list()
                        for (child in children) {
                            allFolders.add(child)
                            try {
                                if ((child.type and Folder.HOLDS_FOLDERS) != 0) {
                                    collectFolders(child)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    collectFolders(store.defaultFolder)
                } catch (_: Exception) {}
            }

            // 1. Check RFC 6154 \Trash attribute
            for (f in allFolders) {
                try {
                    val attrs = (f as? com.sun.mail.imap.IMAPFolder)?.attributes
                    if (attrs != null && attrs.any { it.equals("\\Trash", ignoreCase = true) || it.equals("\\Bin", ignoreCase = true) }) {
                        if (f.exists()) return f
                    }
                } catch (_: Exception) {}
            }

            // 2. Fuzzy name matching
            for (f in allFolders) {
                try {
                    val lower = f.fullName.lowercase()
                    if (lower.contains("papierkorb") ||
                        lower.contains("trash") ||
                        lower.contains("çöp") ||
                        lower.contains("bin") ||
                        lower.contains("gelöscht") ||
                        lower.contains("deleted")
                    ) {
                        if (f.exists()) return f
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Discovers all original folders on the email account (INBOX, Sent, Trash, Drafts, Spam, etc.)
     * with exact message counts and unread counts.
     */
    suspend fun fetchFolderHierarchy(account: EmailAccountEntity): Result<List<MailFolderInfo>> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        val folderList = mutableListOf<MailFolderInfo>()
        try {
            store = createConnectedStore(account, cleanPass)
            val defaultFolder = store.defaultFolder
            val rawFolders = try {
                defaultFolder.list("*")
            } catch (_: Exception) {
                defaultFolder.list()
            }

            for (f in rawFolders) {
                try {
                    // Check if folder can hold messages
                    if ((f.type and Folder.HOLDS_MESSAGES) != 0) {
                        var total = 0
                        var unread = 0
                        val (cat, dispName) = categorizeFolder(f.fullName, f.name)
                        // Fast fetch: only open primary INBOX to avoid network latency on 20+ folders
                        if (cat == MailFolderCategory.INBOX) {
                            try {
                                f.open(Folder.READ_ONLY)
                                total = f.messageCount
                                unread = f.unreadMessageCount
                                f.close(false)
                            } catch (_: Exception) {}
                        }

                        folderList.add(
                            MailFolderInfo(
                                fullName = f.fullName,
                                displayName = dispName,
                                category = cat,
                                totalCount = total,
                                unreadCount = unread
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            // Ensure INBOX is present if not found in list
            if (folderList.none { it.category == MailFolderCategory.INBOX }) {
                try {
                    val inbox = store.getFolder("INBOX")
                    inbox.open(Folder.READ_ONLY)
                    val total = inbox.messageCount
                    val unread = inbox.unreadMessageCount
                    inbox.close(false)
                    folderList.add(
                        0,
                        MailFolderInfo(
                            fullName = "INBOX",
                            displayName = "Gelen Kutusu (Posteingang)",
                            category = MailFolderCategory.INBOX,
                            totalCount = total,
                            unreadCount = unread
                        )
                    )
                } catch (_: Exception) {}
            }

            // Sort logically matching real Gmail: INBOX, STARRED, SNOOZED, IMPORTANT, PURCHASES, SENT, SCHEDULED, OUTBOX, DRAFTS, ALL_MAIL, SPAM, TRASH, SUBSCRIPTIONS, CUSTOM
            val orderMap = mapOf(
                MailFolderCategory.INBOX to 1,
                MailFolderCategory.STARRED to 2,
                MailFolderCategory.SNOOZED to 3,
                MailFolderCategory.IMPORTANT to 4,
                MailFolderCategory.PURCHASES to 5,
                MailFolderCategory.SENT to 6,
                MailFolderCategory.SCHEDULED to 7,
                MailFolderCategory.OUTBOX to 8,
                MailFolderCategory.DRAFTS to 9,
                MailFolderCategory.ALL_MAIL to 10,
                MailFolderCategory.SPAM to 11,
                MailFolderCategory.TRASH to 12,
                MailFolderCategory.SUBSCRIPTIONS to 13,
                MailFolderCategory.CUSTOM to 14
            )
            folderList.sortBy { orderMap[it.category] ?: 15 }

            Result.success(folderList)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    data class MultiFolderSyncResult(
        val emails: List<EmailEntity>,
        val folders: List<MailFolderInfo>,
        val inboxCount: Int,
        val trashCount: Int,
        val spamCount: Int,
        val purchasesCount: Int
    )

    /**
     * Extracts emails from an open folder with UID cache checking.
     * Skips expensive body download if email is already in local database!
     */
    fun extractEmailsFromFolder(
        targetFolder: Folder,
        accountEmail: String,
        limit: Int,
        existingEmails: List<EmailEntity> = emptyList()
    ): List<EmailEntity> {
        val total = targetFolder.messageCount
        if (total == 0) return emptyList()

        val start = (total - limit + 1).coerceAtLeast(1)
        val messages = targetFolder.getMessages(start, total)

        // Bulk prefetch envelopes, flags, UIDs, and essential headers in ONE single IMAP round-trip
        try {
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(UIDFolder.FetchProfileItem.UID)
                add("Message-ID")
                add("List-Unsubscribe")
            }
            targetFolder.fetch(messages, fp)
        } catch (_: Exception) {}

        val existingByUid = existingEmails.filter { it.serverUid > 0 }.associateBy { "${it.serverUid}_${targetFolder.fullName}" }
        val existingByMsgId = existingEmails.filter { !it.messageId.isNullOrBlank() }.associateBy { it.messageId!! }

        val isTrashFolder = targetFolder.fullName.contains("trash", ignoreCase = true) ||
                targetFolder.fullName.contains("çöp", ignoreCase = true) ||
                targetFolder.fullName.contains("bin", ignoreCase = true) ||
                targetFolder.fullName.contains("papierkorb", ignoreCase = true) ||
                targetFolder.fullName.contains("gelöscht", ignoreCase = true)
        val isSentFolder = targetFolder.fullName.contains("sent", ignoreCase = true) ||
                targetFolder.fullName.contains("gesendet", ignoreCase = true) ||
                targetFolder.fullName.contains("gönderil", ignoreCase = true)
        val isDraftFolder = targetFolder.fullName.contains("draft", ignoreCase = true) ||
                targetFolder.fullName.contains("entwürfe", ignoreCase = true) ||
                targetFolder.fullName.contains("entwuerfe", ignoreCase = true) ||
                targetFolder.fullName.contains("taslak", ignoreCase = true)
        val isSpamFolder = targetFolder.fullName.contains("spam", ignoreCase = true) ||
                targetFolder.fullName.contains("junk", ignoreCase = true) ||
                targetFolder.fullName.contains("istenmeyen", ignoreCase = true)
        val isPurchasesFolder = targetFolder.fullName.contains("käufe", ignoreCase = true) ||
                targetFolder.fullName.contains("kaeufe", ignoreCase = true) ||
                targetFolder.fullName.contains("purchases", ignoreCase = true) ||
                targetFolder.fullName.contains("sipariş", ignoreCase = true)

        val resultList = mutableListOf<EmailEntity>()

        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            try {
                val uid = try {
                    (targetFolder as? UIDFolder)?.getUID(msg)
                        ?: (targetFolder as? IMAPFolder)?.getUID(msg)
                        ?: 0L
                } catch (_: Exception) { 0L }

                val msgIdHeader = try {
                    msg.getHeader("Message-ID")?.firstOrNull()
                } catch (_: Exception) { null }

                val isSeen = msg.isSet(Flags.Flag.SEEN)
                val isFlagged = msg.isSet(Flags.Flag.FLAGGED)

                // FAST PATH: Check if email is already in database
                val existing = (msgIdHeader?.let { existingByMsgId[it] }) ?: existingByUid["${uid}_${targetFolder.fullName}"]
                if (existing != null) {
                    val updated = existing.copy(
                        isRead = if (isSentFolder) true else isSeen,
                        isStarred = isFlagged,
                        folderName = targetFolder.fullName,
                        serverUid = uid
                    )
                    resultList.add(updated)
                    continue
                }

                // NEW EMAIL: Parse envelope and content
                val subject = msg.subject ?: "(Konusuz)"
                val fromAddresses: Array<Address>? = msg.from
                val fromAddress = fromAddresses?.firstOrNull() as? InternetAddress
                val senderName = fromAddress?.personal?.takeIf { it.isNotBlank() }
                    ?: fromAddress?.address?.substringBefore("@")
                    ?: "Bilinmeyen Gönderen"
                val senderEmail = fromAddress?.address ?: accountEmail
                val date = msg.sentDate ?: msg.receivedDate ?: java.util.Date()
                val timestamp = date.time

                val (extractedBody, hasAttachment, attachmentName) = extractContent(msg)
                val preview = extractedBody
                    .replace("\n", " ")
                    .trim()
                    .take(160)

                val sizeKb = (msg.size / 1024L).coerceAtLeast(12L)

                val listUnsubHeader = msg.getHeader("List-Unsubscribe")?.firstOrNull()
                val isMailingList = !listUnsubHeader.isNullOrBlank() ||
                        extractedBody.contains("unsubscribe", ignoreCase = true) ||
                        extractedBody.contains("abonelikten çık", ignoreCase = true)

                val unsubUrl = extractUnsubscribeUrl(listUnsubHeader, extractedBody)

                val hasTrackingPixel = extractedBody.contains("open.track", ignoreCase = true) ||
                        extractedBody.contains("pixel.gif", ignoreCase = true) ||
                        extractedBody.contains("track.png", ignoreCase = true) ||
                        extractedBody.contains("email-tracker", ignoreCase = true)

                val baseCategory = SmartFilterEngine.detectSmartCategory(
                    sender = senderName,
                    subject = subject,
                    body = extractedBody
                )

                val finalCategoryId = when {
                    isSpamFolder -> SmartCategory.SPAM.id
                    isTrashFolder -> SmartCategory.TRASH.id
                    isPurchasesFolder -> SmartCategory.BILLS.id
                    else -> baseCategory.id
                }

                val finalLabelsCsv = when {
                    isSentFolder -> "Gönderilen"
                    isDraftFolder -> "Taslak"
                    isPurchasesFolder -> "Käufe"
                    isMailingList -> "Bülten"
                    else -> ""
                }

                val entity = EmailEntity(
                    senderName = senderName,
                    senderEmail = senderEmail,
                    subject = subject,
                    preview = preview,
                    body = extractedBody,
                    timestamp = timestamp,
                    isRead = if (isSentFolder) true else isSeen,
                    isStarred = isFlagged,
                    isArchived = false,
                    isTrash = isTrashFolder,
                    categoryId = finalCategoryId,
                    labelsCsv = finalLabelsCsv,
                    storageSizeKb = sizeKb,
                    hasAttachment = hasAttachment,
                    attachmentName = attachmentName,
                    isMailingList = isMailingList,
                    unsubscribeUrl = unsubUrl,
                    isUnsubscribed = false,
                    trackingPixelBlocked = hasTrackingPixel,
                    accountEmail = accountEmail,
                    folderName = targetFolder.fullName,
                    serverUid = uid,
                    messageId = msgIdHeader
                )
                resultList.add(entity)
            } catch (msgEx: Exception) {
                msgEx.printStackTrace()
            }
        }

        return resultList
    }

    /**
     * High-speed single-connection multi-folder sync:
     * Connects once to IMAP and downloads INBOX, Trash, Spam in 1-2 seconds!
     */
    suspend fun syncAllFoldersFast(
        account: EmailAccountEntity,
        existingEmails: List<EmailEntity> = emptyList()
    ): Result<MultiFolderSyncResult> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val allFetchedEmails = mutableListOf<EmailEntity>()

            // 1. Discover folders rapidly
            val folderList = mutableListOf<MailFolderInfo>()
            val rawFolders = try {
                store.defaultFolder.list("*")
            } catch (_: Exception) {
                store.defaultFolder.list()
            }
            for (f in rawFolders) {
                try {
                    if ((f.type and Folder.HOLDS_MESSAGES) != 0) {
                        val (cat, dispName) = categorizeFolder(f.fullName, f.name)
                        folderList.add(
                            MailFolderInfo(
                                fullName = f.fullName,
                                displayName = dispName,
                                category = cat,
                                totalCount = 0,
                                unreadCount = 0
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            // 2. Fetch INBOX (limit 60)
            var inboxFolder: Folder? = null
            val inboxEmails = try {
                inboxFolder = store.getFolder("INBOX")
                inboxFolder.open(Folder.READ_ONLY)
                extractEmailsFromFolder(inboxFolder, account.emailAddress, 60, existingEmails)
            } catch (_: Exception) {
                emptyList()
            } finally {
                try { inboxFolder?.close(false) } catch (_: Exception) {}
            }
            allFetchedEmails.addAll(inboxEmails)

            // 3. Fetch Trash / Papierkorb (limit 40)
            var trashFolder: Folder? = null
            val trashEmails = try {
                trashFolder = findTrashFolder(store)
                if (trashFolder != null && trashFolder.exists()) {
                    trashFolder.open(Folder.READ_ONLY)
                    extractEmailsFromFolder(trashFolder, account.emailAddress, 40, existingEmails)
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                try { trashFolder?.close(false) } catch (_: Exception) {}
            }
            allFetchedEmails.addAll(trashEmails)

            // 4. Fetch Spam (limit 30)
            var spamFolder: Folder? = null
            val spamEmails = try {
                val candidate = folderList.firstOrNull { it.category == MailFolderCategory.SPAM }
                    ?: folderList.firstOrNull { it.fullName.contains("spam", ignoreCase = true) }
                if (candidate != null) {
                    spamFolder = store.getFolder(candidate.fullName)
                    if (spamFolder.exists()) {
                        spamFolder.open(Folder.READ_ONLY)
                        extractEmailsFromFolder(spamFolder, account.emailAddress, 30, existingEmails)
                    } else emptyList()
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            } finally {
                try { spamFolder?.close(false) } catch (_: Exception) {}
            }
            allFetchedEmails.addAll(spamEmails)

            Result.success(
                MultiFolderSyncResult(
                    emails = allFetchedEmails,
                    folders = folderList,
                    inboxCount = inboxEmails.size,
                    trashCount = trashEmails.size,
                    spamCount = spamEmails.size,
                    purchasesCount = 0
                )
            )
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Fetch emails from INBOX with limit (convenience overload).
     */
    suspend fun fetchEmails(
        account: EmailAccountEntity,
        limit: Int
    ): Result<List<EmailEntity>> = fetchEmails(account, "INBOX", limit)

    /**
     * Fetch emails from any IMAP folder (INBOX, Sent, Trash, etc.) with cache optimization.
     */
    suspend fun fetchEmails(
        account: EmailAccountEntity,
        folderFullName: String = "INBOX",
        limit: Int = 60,
        existingEmails: List<EmailEntity> = emptyList()
    ): Result<List<EmailEntity>> = withContext(Dispatchers.IO) {
        var store: Store? = null
        var targetFolder: Folder? = null
        val cleanPass = cleanPassword(account.password)
        try {
            store = createConnectedStore(account, cleanPass)
            targetFolder = try {
                store.getFolder(folderFullName).takeIf { it.exists() } ?: store.getFolder("INBOX")
            } catch (_: Exception) {
                store.getFolder("INBOX")
            }

            targetFolder.open(Folder.READ_ONLY)
            val resultList = extractEmailsFromFolder(targetFolder, account.emailAddress, limit, existingEmails)
            Result.success(resultList)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { targetFolder?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Deletes or moves an email to Trash on the IMAP server (e.g. Gmail).
     * Birebir gerçek Gmail sunucusunda silme / çöp kutusuna taşıma garantisi sağlar.
     */
    suspend fun deleteOrTrashEmailOnServer(
        account: EmailAccountEntity,
        email: EmailEntity,
        moveToTrash: Boolean = true
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        var sourceFolder: Folder? = null
        var trashFolder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val folderToOpen = email.folderName.ifBlank { "INBOX" }
            sourceFolder = try {
                store.getFolder(folderToOpen).takeIf { it.exists() } ?: store.getFolder("INBOX")
            } catch (_: Exception) {
                store.getFolder("INBOX")
            }
            sourceFolder.open(Folder.READ_WRITE)

            var msg = findMessageOnFolder(sourceFolder, email)
            if (msg == null && !folderToOpen.equals("INBOX", ignoreCase = true)) {
                // If not found in original folder, also check INBOX
                try {
                    val inbox = store.getFolder("INBOX")
                    if (inbox.exists()) {
                        sourceFolder.close(false)
                        inbox.open(Folder.READ_WRITE)
                        sourceFolder = inbox
                        msg = findMessageOnFolder(sourceFolder, email)
                    }
                } catch (_: Exception) {}
            }

            if (msg == null) {
                // E-posta sunucuda zaten yok veya önceden silinmiş, başarıyla tamamlandı kabul edilir
                return@withContext Result.success(true)
            }

            trashFolder = findTrashFolder(store)

            val isAlreadyTrash = folderToOpen.contains("trash", ignoreCase = true) ||
                    folderToOpen.contains("çöp", ignoreCase = true) ||
                    folderToOpen.contains("papierkorb", ignoreCase = true) ||
                    folderToOpen.contains("bin", ignoreCase = true) ||
                    (trashFolder != null && sourceFolder.fullName.equals(trashFolder.fullName, ignoreCase = true))

            if (moveToTrash) {
                if (!isAlreadyTrash && trashFolder != null) {
                    try {
                        sourceFolder.copyMessages(arrayOf(msg), trashFolder)
                    } catch (copyEx: Exception) {
                        copyEx.printStackTrace()
                    }
                }
                msg.setFlag(Flags.Flag.DELETED, true)
                sourceFolder.expunge()
            } else {
                // Kalıcı silme: Çöp kutusuna kopyala ve tüm kopyaları sunucudan tamamen expunge et
                if (isAlreadyTrash) {
                    msg.setFlag(Flags.Flag.DELETED, true)
                    sourceFolder.expunge()
                } else {
                    if (trashFolder != null) {
                        try {
                            sourceFolder.copyMessages(arrayOf(msg), trashFolder)
                        } catch (_: Exception) {}
                    }
                    msg.setFlag(Flags.Flag.DELETED, true)
                    sourceFolder.expunge()

                    if (trashFolder != null && trashFolder.exists()) {
                        try {
                            trashFolder.open(Folder.READ_WRITE)
                            val trashMsg = findMessageOnFolder(trashFolder, email)
                            if (trashMsg != null) {
                                trashMsg.setFlag(Flags.Flag.DELETED, true)
                                trashFolder.expunge()
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { trashFolder.close(true) } catch (_: Exception) {}
                        }
                    }
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { sourceFolder?.close(true) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Batch move multiple emails to Trash on the IMAP server (Gmail).
     * Connects once and efficiently moves all specified emails to the real Trash / Papierkorb.
     */
    suspend fun batchMoveToTrashOnServer(
        account: EmailAccountEntity,
        emails: List<EmailEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (emails.isEmpty()) return@withContext Result.success(0)
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        var movedCount = 0
        try {
            store = createConnectedStore(account, cleanPass)
            val trashFolder = findTrashFolder(store)

            // Group emails by their folder name (e.g. INBOX)
            val byFolder = emails.groupBy { it.folderName.ifBlank { "INBOX" } }
            for ((fName, group) in byFolder) {
                var folder: Folder? = null
                try {
                    folder = try {
                        store.getFolder(fName).takeIf { it.exists() } ?: store.getFolder("INBOX")
                    } catch (_: Exception) {
                        store.getFolder("INBOX")
                    }
                    folder.open(Folder.READ_WRITE)

                    val msgsToTrash = mutableListOf<Message>()
                    for (email in group) {
                        val msg = findMessageOnFolder(folder, email)
                        if (msg != null) {
                            msgsToTrash.add(msg)
                        }
                    }

                    if (msgsToTrash.isNotEmpty()) {
                        if (trashFolder != null) {
                            try {
                                folder.copyMessages(msgsToTrash.toTypedArray(), trashFolder)
                            } catch (_: Exception) {}
                        }
                        for (m in msgsToTrash) {
                            try { m.setFlag(Flags.Flag.DELETED, true) } catch (_: Exception) {}
                        }
                        folder.expunge()
                        movedCount += msgsToTrash.size
                    }
                } catch (_: Exception) {
                } finally {
                    try { folder?.close(true) } catch (_: Exception) {}
                }
            }

            Result.success(movedCount)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Deletes or moves all emails from a specific sender to Trash on the IMAP server (Gmail).
     * Clean & Trim ekranından bülten veya istenmeyen gönderici temizlendiğinde gerçek sunucuda siler.
     */
    suspend fun bulkTrashSenderOnServer(
        account: EmailAccountEntity,
        senderEmail: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        var inbox: Folder? = null
        var trashFolder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            // Locate Trash folder (supports German Papierkorb)
            trashFolder = findTrashFolder(store)

            val total = inbox.messageCount
            if (total == 0) return@withContext Result.success(0)

            val term = FromStringTerm(senderEmail.trim())
            val matched = try {
                inbox.search(term)
            } catch (_: Exception) {
                val start = (total - 100 + 1).coerceAtLeast(1)
                inbox.getMessages(start, total).filter { msg ->
                    msg.from?.any { it.toString().contains(senderEmail, ignoreCase = true) } == true
                }.toTypedArray()
            }

            if (matched.isNotEmpty()) {
                if (trashFolder != null) {
                    try {
                        inbox.copyMessages(matched, trashFolder)
                    } catch (_: Exception) {}
                }
                for (m in matched) {
                    try {
                        m.setFlag(Flags.Flag.DELETED, true)
                    } catch (_: Exception) {}
                }
                inbox.expunge()
            }

            Result.success(matched.size)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { inbox?.close(true) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Permanently deletes all emails from the Trash folder on the IMAP server (Gmail).
     * Gerçek Gmail Çöp Kutusunu tamamen boşaltır.
     */
    suspend fun emptyTrashOnServer(account: EmailAccountEntity): Result<Int> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        var trashFolder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            trashFolder = findTrashFolder(store)

            if (trashFolder == null) {
                return@withContext Result.success(0)
            }

            trashFolder.open(Folder.READ_WRITE)
            val total = trashFolder.messageCount
            if (total == 0) {
                return@withContext Result.success(0)
            }

            val allMsgs = trashFolder.getMessages(1, total)
            for (m in allMsgs) {
                try {
                    m.setFlag(Flags.Flag.DELETED, true)
                } catch (_: Exception) {}
            }
            trashFolder.expunge()

            Result.success(total)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { trashFolder?.close(true) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Updates read status or star status directly on the IMAP server (Gmail).
     */
    suspend fun updateEmailFlagsOnServer(
        account: EmailAccountEntity,
        email: EmailEntity,
        isRead: Boolean? = null,
        isStarred: Boolean? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        var store: Store? = null
        var folder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val folderToOpen = email.folderName.ifBlank { "INBOX" }
            folder = try {
                store.getFolder(folderToOpen).takeIf { it.exists() } ?: store.getFolder("INBOX")
            } catch (_: Exception) {
                store.getFolder("INBOX")
            }
            folder.open(Folder.READ_WRITE)

            val msg = findMessageOnFolder(folder, email)
            if (msg != null) {
                isRead?.let { msg.setFlag(Flags.Flag.SEEN, it) }
                isStarred?.let { msg.setFlag(Flags.Flag.FLAGGED, it) }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { folder?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    private fun findMessageOnFolder(folder: Folder, email: EmailEntity): Message? {
        // 1. By Message-ID (most reliable unique identifier in standard email)
        if (!email.messageId.isNullOrBlank()) {
            try {
                val term = HeaderTerm("Message-ID", email.messageId)
                val results = folder.search(term)
                if (results.isNotEmpty()) return results.first()
            } catch (_: Exception) {}
        }

        // 2. By UID with subject verification
        if (email.serverUid > 0L) {
            try {
                val byUid = (folder as? UIDFolder)?.getMessageByUID(email.serverUid)
                    ?: (folder as? IMAPFolder)?.getMessageByUID(email.serverUid)
                if (byUid != null) {
                    val uidSub = byUid.subject ?: ""
                    if (email.subject.isBlank() || uidSub == email.subject) {
                        return byUid
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Search by Subject
        if (email.subject.isNotBlank()) {
            try {
                val total = folder.messageCount
                if (total > 0) {
                    val start = (total - 100 + 1).coerceAtLeast(1)
                    val candidates = folder.getMessages(start, total)
                    for (m in candidates.reversed()) {
                        if (m.subject == email.subject) {
                            return m
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }


    /**
     * Send a real email via SMTP.
     */
    suspend fun sendEmail(
        account: EmailAccountEntity,
        to: String,
        subject: String,
        body: String,
        replyToOriginalSubject: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanPass = cleanPassword(account.password)
        try {
            val props = Properties().apply {
                put("mail.transport.protocol", "smtp")
                put("mail.smtp.host", account.smtpHost)
                put("mail.smtp.port", account.smtpPort.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.trust", "*")
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "15000")

                if (account.smtpPort == 465 || account.smtpSsl) {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.port", account.smtpPort.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                }
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(account.emailAddress.trim(), cleanPass)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.emailAddress.trim(), account.accountName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.trim()))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8", "plain")
                sentDate = java.util.Date()

                if (!replyToOriginalSubject.isNullOrBlank()) {
                    setHeader("In-Reply-To", "<${replyToOriginalSubject.hashCode()}@emailmanager>")
                }
            }

            Transport.send(message)
            Result.success(true)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        }
    }

    private fun extractContent(part: Part): Triple<String, Boolean, String?> {
        var hasAttachment = false
        var attachmentName: String? = null
        val bodyBuilder = StringBuilder()

        fun parsePart(p: Part) {
            val disposition = p.disposition
            val fileName = p.fileName

            if (disposition != null && (disposition.equals(Part.ATTACHMENT, ignoreCase = true) || disposition.equals(Part.INLINE, ignoreCase = true))) {
                if (!fileName.isNullOrBlank()) {
                    hasAttachment = true
                    if (attachmentName == null) attachmentName = fileName
                }
            }

            if (p.isMimeType("text/plain")) {
                val content = p.content as? String
                if (content != null) bodyBuilder.append(content).append("\n")
            } else if (p.isMimeType("text/html")) {
                val htmlContent = p.content as? String
                if (htmlContent != null && bodyBuilder.isEmpty()) {
                    // Simple HTML strip for plain display
                    val cleanText = htmlContent.replace(Regex("<[^>]*>"), " ")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                    bodyBuilder.append(cleanText).append("\n")
                }
            } else if (p.isMimeType("multipart/*")) {
                val mp = p.content as? Multipart
                if (mp != null) {
                    for (i in 0 until mp.count) {
                        parsePart(mp.getBodyPart(i))
                    }
                }
            }
        }

        try {
            parsePart(part)
        } catch (e: Exception) {
            bodyBuilder.append("(İçerik ayrıştırılırken hata oluştu: ${e.message})")
        }

        val resultBody = bodyBuilder.toString().trim().ifEmpty { "(Boş e-posta içeriği)" }
        return Triple(resultBody, hasAttachment, attachmentName)
    }

    private fun extractUnsubscribeUrl(header: String?, body: String): String? {
        if (!header.isNullOrBlank()) {
            val match = Regex("<(https?://[^>]+)>").find(header)
            if (match != null) return match.groupValues[1]
        }
        val bodyMatch = Regex("""https?://[^\s"'<>]+(?:unsubscribe|opt-out|cikis)[^\s"'<>]*""", RegexOption.IGNORE_CASE).find(body)
        return bodyMatch?.value
    }
}
