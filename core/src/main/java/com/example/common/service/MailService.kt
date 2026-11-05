package com.example.common.service

import com.example.common.i18n.t
import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailEntity
import com.example.common.filter.SmartFilterEngine
import com.example.common.model.MailFolderCategory
import com.example.common.model.MailFolderInfo
import com.example.common.model.SmartCategory
import com.example.common.security.Credentials
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import com.sun.mail.imap.protocol.IMAPResponse
import com.sun.mail.imap.protocol.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.util.Properties
import java.util.concurrent.TimeUnit
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
import javax.mail.internet.MimeUtility
import javax.mail.search.FromStringTerm
import javax.mail.search.HeaderTerm

data class MoveOutcome(
    val found: Boolean,
    val targetFolder: String? = null
)

data class BatchMoveResult(
    val movedIds: List<Long>,
    val failedIds: List<Long>,
    val trashFolder: String?,
    val error: String? = null
)

data class SavedAttachment(
    val name: String,
    val mimeType: String,
    val file: File
)

enum class MoveTarget { ARCHIVE, INBOX }

object MailService {

    /** Sadece testlerde yerel deneme sunucusunun sertifikasını kabul etmek için kullanılır. */
    var sessionPropertyOverrides: Map<String, String> = emptyMap()

    /** Tek eşitlemenin yeni posta indirmeye ayırdığı en uzun süre. */
    const val SYNC_BUDGET_MS = 45_000L

    /** Çok büyük hesaplarda klasör başına taranan en yeni posta sayısı. */
    const val MAX_MESSAGES_PER_FOLDER = 5_000

    /** Gmail'in Käufe kategorisi IMAP klasörü değildir; bu sanal adla saklanır. */
    const val GMAIL_PURCHASES_FOLDER = "gmail-category:purchases"

    /** Test kancası: yerel test sunucusunda HIGHESTMODSEQ yoktur. */
    var allowFingerprintWithoutModSeq = false

    private const val TRASH_NOT_FOUND = "Sunucuda çöp kutusu klasörü bulunamadı. E-posta silinmedi."

    private fun resolvePassword(account: EmailAccountEntity): String =
        cleanPassword(Credentials.codec.decryptOrPlain(account.password))

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
                t("Google Güvenlik Kuralı: Google, normal Gmail şifrenizi engelledi. E-posta okumak ve göndermek için 16 haneli 'Uygulama Şifresi' (App Password) zorunludur.")
            }
            raw.contains("535-5.7.8") ||
            raw.contains("535 5.7.8") ||
            raw.contains("Username and Password not accepted", ignoreCase = true) ||
            raw.contains("AUTHENTICATIONFAILED", ignoreCase = true) -> {
                if (email.endsWith("@gmail.com", ignoreCase = true) || email.endsWith("@googlemail.com", ignoreCase = true)) {
                    t("Gmail Kimlik Doğrulama Hatası: Normal Gmail şifreniz kabul edilmedi. myaccount.google.com/apppasswords adresinden 16 haneli 'Uygulama Şifresi' alınız.")
                } else {
                    t("Kimlik doğrulama başarısız! Lütfen e-posta adresinizi ve şifrenizi kontrol edin.")
                }
            }
            raw.contains("SSLHandshake", ignoreCase = true) ||
            raw.contains("CertPathValidator", ignoreCase = true) ||
            raw.contains("PKIX", ignoreCase = true) ||
            raw.contains("certificate_unknown", ignoreCase = true) ||
            raw.contains("server's identity", ignoreCase = true) ||
            raw.contains("Trust anchor", ignoreCase = true) -> {
                t("Güvenlik uyarısı: Sunucu sertifikası doğrulanamadı, bağlantı kesildi. Şifreniz gönderilmedi.")
            }
            raw.contains("ConnectException", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true) ||
            raw.contains("SocketTimeout", ignoreCase = true) -> {
                t("Sunucuya bağlanılamadı (Zaman aşımı). İnternet bağlantınızı veya sunucu adreslerini kontrol edin.")
            }
            raw.contains("connect to host", ignoreCase = true) ||
            raw.contains("failed to connect", ignoreCase = true) ||
            raw.contains("UnknownHost", ignoreCase = true) ||
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("Network is unreachable", ignoreCase = true) -> {
                t("Sunucuya bağlanılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.")
            }
            else -> t("Hata: {0}", e.localizedMessage ?: e.message ?: "Bağlantı kurulamadı")
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
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "8000")
            put("mail.imaps.timeout", "8000")
            put("mail.imaps.writetimeout", "8000")
            put("mail.imaps.partialfetch", "true")
            // Gövde indirmek postayı okundu yapmaz
            put("mail.imaps.peek", "true")
            put("mail.imaps.fetchsize", "262144")
            sessionPropertyOverrides.forEach { (key, value) -> put(key, value) }
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
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val messageCount = inbox.messageCount
            inbox.close(false)

            Result.success(t("Bağlantı Başarılı! Gelen Kutusunda {0} e-posta tespit edildi.", messageCount))
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
    fun categorizeFolder(
        fullName: String,
        name: String,
        attributes: Array<String> = emptyArray()
    ): Pair<MailFolderCategory, String> {
        val lower = fullName.lowercase()
        val cleanName = name.removePrefix("[Gmail]/").removePrefix("[Google Mail]/")
        val hasAttribute = { attr: String -> attributes.any { it.equals(attr, ignoreCase = true) } }
        return when {
            MailFolderNames.isHold(fullName) -> MailFolderCategory.HOLD to "Silinecekler"
            fullName.startsWith(MailFolderNames.APP_LABEL_PREFIX) -> MailFolderCategory.CUSTOM to cleanName
            // RFC 6154: sunucunun bildirdiği özel klasör türü, klasör adından ve hesap dilinden bağımsızdır
            hasAttribute("\\All") -> MailFolderCategory.ALL_MAIL to "Tüm Postalar (Alle E-Mails)"
            hasAttribute("\\Sent") -> MailFolderCategory.SENT to "Gönderilenler (Gesendet)"
            hasAttribute("\\Drafts") -> MailFolderCategory.DRAFTS to "Taslaklar (Entwürfe)"
            hasAttribute("\\Trash") -> MailFolderCategory.TRASH to "Çöp Kutusu (Papierkorb)"
            hasAttribute("\\Junk") -> MailFolderCategory.SPAM to "Spam / İstenmeyen"
            hasAttribute("\\Flagged") -> MailFolderCategory.STARRED to "Yıldızlı (Markiert)"
            hasAttribute("\\Important") -> MailFolderCategory.IMPORTANT to "Önemli (Wichtig)"
            lower == "inbox" || lower.contains("posteingang") -> MailFolderCategory.INBOX to "Gelen Kutusu (Posteingang)"
            lower.contains("starred") || lower.contains("markiert") || lower.contains("yıldız") -> MailFolderCategory.STARRED to "Yıldızlı (Markiert)"
            lower.contains("snooze") || lower.contains("zurückgestellt") || lower.contains("zurueckgestellt") || lower.contains("ertele") -> MailFolderCategory.SNOOZED to "Ertelenen (Zurückgestellt)"
            lower.contains("important") || lower.contains("wichtig") || lower.contains("önemli") -> MailFolderCategory.IMPORTANT to "Önemli (Wichtig)"
            lower.contains("purchases") || lower.contains("käufe") || lower.contains("kaeufe") || lower.contains("sipariş") || lower.contains("alışveriş") -> MailFolderCategory.PURCHASES to "Alışverişler (Käufe)"
            lower.contains("sent") || lower.contains("gesendet") || lower.contains("gönderil") -> MailFolderCategory.SENT to "Gönderilenler (Gesendet)"
            lower.contains("scheduled") || lower.contains("geplant") || lower.contains("zamanla") -> MailFolderCategory.SCHEDULED to "Zamanlanan (Geplant)"
            lower.contains("outbox") || lower.contains("postausgang") || lower.contains("giden") -> MailFolderCategory.OUTBOX to "Giden Kutusu (Postausgang)"
            lower.contains("draft") || lower.contains("entwürfe") || lower.contains("entwuerfe") || lower.contains("taslak") -> MailFolderCategory.DRAFTS to "Taslaklar (Entwürfe)"
            lower.contains("all mail") || lower.contains("alle e-mails") || lower.contains("alle nachrichten") || lower.contains("tüm postalar") || lower == "[gmail]/all" -> MailFolderCategory.ALL_MAIL to "Tüm Postalar (Alle E-Mails)"
            lower.contains("spam") || lower.contains("junk") || lower.contains("istenmeyen") -> MailFolderCategory.SPAM to "Spam / İstenmeyen"
            MailFolderNames.isTrash(fullName) -> MailFolderCategory.TRASH to "Çöp Kutusu (Papierkorb)"
            lower.contains("abo") || lower.contains("subscri") || lower.contains("bülten") -> MailFolderCategory.SUBSCRIPTIONS to "Abonelikleri Yönet (Abos verwalten)"
            else -> MailFolderCategory.CUSTOM to cleanName.ifBlank { name }
        }
    }

    /**
     * Locate Trash / Papierkorb folder dynamically across languages (German, English, Turkish).
     * RFC 6154 \Trash attribute + Candidate list + Fuzzy search.
     */
    fun findTrashFolder(store: Store): Folder? {
        val allFolders = listAllFolders(store)

        // 1. RFC 6154 \\Trash özelliği en güvenilir kaynaktır
        for (f in allFolders) {
            try {
                val attrs = (f as? IMAPFolder)?.attributes
                if (attrs != null && attrs.any { it.equals("\\Trash", ignoreCase = true) }) {
                    if (f.exists()) return f
                }
            } catch (_: Exception) {}
        }

        // 2. Bilinen klasör adları
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

        // 3. Tam klasör adı eşleşmesi
        for (f in allFolders) {
            try {
                if (MailFolderNames.isTrash(f.fullName) && f.exists()) return f
            } catch (_: Exception) {}
        }
        return null
    }

    private fun listAllFolders(store: Store): List<Folder> {
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
        return allFolders
    }

    /**
     * Arşiv klasörünü bulur: önce RFC 6154 \\Archive ve \\All, sonra bilinen adlar.
     * Hiçbiri yoksa Gmail dışı sunucularda "Archive" klasörü oluşturulur.
     */
    private fun findArchiveFolder(store: Store): Folder? {
        val allFolders = listAllFolders(store)
        for (wanted in listOf("\\Archive", "\\All")) {
            for (f in allFolders) {
                try {
                    val attrs = (f as? IMAPFolder)?.attributes
                    if (attrs != null && attrs.any { it.equals(wanted, ignoreCase = true) } && f.exists()) return f
                } catch (_: Exception) {}
            }
        }
        for (f in allFolders) {
            try {
                if (MailFolderNames.isArchive(f.fullName) && f.exists()) return f
            } catch (_: Exception) {}
        }
        return try {
            val created = store.getFolder("Archive")
            if (created.exists() || created.create(Folder.HOLDS_MESSAGES)) created else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Postayı taşır. Sunucu MOVE destekliyorsa tek adımda taşır. Desteklemiyorsa önce kopyalar;
     * kopyalama başarısız olursa hata fırlatır ve kaynak posta silinmez.
     */
    private fun safeMove(store: Store, source: Folder, messages: Array<Message>, target: Folder) {
        val imapSource = source as? IMAPFolder
        if (imapSource != null && (store as? IMAPStore)?.hasCapability("MOVE") == true) {
            imapSource.moveMessages(messages, target)
            return
        }
        source.copyMessages(messages, target)
        for (m in messages) {
            m.setFlag(Flags.Flag.DELETED, true)
        }
        source.expunge()
    }

    /**
     * Discovers all original folders on the email account (INBOX, Sent, Trash, Drafts, Spam, etc.)
     * with exact message counts and unread counts.
     */
    suspend fun fetchFolderHierarchy(account: EmailAccountEntity): Result<List<MailFolderInfo>> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
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
                        val (cat, dispName) = categorizeFolder(f.fullName, f.name, folderAttributes(f))
                        // Klasör kapalıyken sayılar tek STATUS komutuyla gelir
                        val total = try { f.messageCount.coerceAtLeast(0) } catch (_: Exception) { 0 }
                        val unread = try { f.unreadMessageCount.coerceAtLeast(0) } catch (_: Exception) { 0 }

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
                            displayName = t("Gelen Kutusu (Posteingang)"),
                            category = MailFolderCategory.INBOX,
                            totalCount = total,
                            unreadCount = unread
                        )
                    )
                } catch (_: Exception) {}
            }

            Result.success(sortFolders(folderList))
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    private fun folderAttributes(folder: Folder): Array<String> =
        try { (folder as? IMAPFolder)?.attributes ?: emptyArray() } catch (_: Exception) { emptyArray() }

    // Gmail'deki sıra: INBOX, STARRED, SNOOZED, IMPORTANT, PURCHASES, SENT, SCHEDULED, OUTBOX, DRAFTS, ALL_MAIL, SPAM, TRASH
    private fun sortFolders(folders: List<MailFolderInfo>): List<MailFolderInfo> {
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
            MailFolderCategory.HOLD to 12,
            MailFolderCategory.TRASH to 13,
            MailFolderCategory.SUBSCRIPTIONS to 14,
            MailFolderCategory.CUSTOM to 15
        )
        return folders.sortedBy { orderMap[it.category] ?: 16 }
    }

    /** Taramada önce kullanıcının gördüğü klasörler gelir; süre dolarsa onlar eksik kalmaz. */
    private fun syncRank(category: MailFolderCategory): Int = when (category) {
        MailFolderCategory.INBOX -> 0
        MailFolderCategory.SENT -> 1
        MailFolderCategory.DRAFTS -> 2
        MailFolderCategory.SPAM -> 3
        MailFolderCategory.TRASH -> 4
        MailFolderCategory.ALL_MAIL -> 6
        else -> 5
    }

    /** Klasörü açmadan tek STATUS komutuyla durumunu okur. */
    private fun readFolderStatus(folder: Folder, hasModSeq: Boolean): Status? {
        val imapFolder = folder as? IMAPFolder ?: return null
        val items = mutableListOf("MESSAGES", "UNSEEN", "UIDNEXT", "UIDVALIDITY")
        if (hasModSeq) items.add("HIGHESTMODSEQ")
        return try {
            imapFolder.doCommand { protocol -> protocol.status(folder.fullName, items.toTypedArray()) } as? Status
        } catch (_: Exception) {
            null
        }
    }

    /** Klasörlerin ortak özeti; bir durum eksikse veya bayrak değişimi izlenemiyorsa null döner. */
    private fun folderFingerprint(names: List<String>, statuses: List<Status?>, hasModSeq: Boolean): String? {
        if (!hasModSeq && !allowFingerprintWithoutModSeq) return null
        if (statuses.any { it == null }) return null
        return names.zip(statuses).sortedBy { it.first }.joinToString("|") { (name, status) ->
            "$name:${status!!.uidvalidity}:${status.uidnext}:${status.total}:${status.unseen}:${status.highestmodseq}"
        }
    }

    /** Gmail arama sözdizimiyle UID listesi alır; klasör açık olmalıdır. */
    private fun gmailSearchUids(folder: IMAPFolder, query: String): List<Long> {
        val uids = mutableListOf<Long>()
        folder.doCommand { protocol ->
            val responses = protocol.command("UID SEARCH X-GM-RAW \"$query\"", null)
            for (response in responses) {
                val imapResponse = response as? IMAPResponse ?: continue
                if (imapResponse.keyEquals("SEARCH")) {
                    while (true) {
                        val uid = imapResponse.readLong()
                        if (uid < 0) break
                        uids.add(uid)
                    }
                }
            }
            protocol.notifyResponseHandlers(responses)
            protocol.handleResult(responses.last())
            null
        }
        return uids
    }

    data class MultiFolderSyncResult(
        val emails: List<EmailEntity>,
        val folders: List<MailFolderInfo>,
        val inboxCount: Int,
        val trashCount: Int,
        val spamCount: Int,
        val purchasesCount: Int,
        val missingUids: Map<String, Set<Long>> = emptyMap(),
        val isPartial: Boolean = false,
        val memberships: List<FolderMembership> = emptyList(),
        val scannedFolders: Set<String> = emptySet(),
        val timedOut: Boolean = false,
        val fingerprint: String? = null,
        val unchanged: Boolean = false
    )

    /**
     * Yerelde kayıtlı olup bu klasörde artık bulunmayan UID'leri döndürür.
     * Tüm UID'ler birden kayıpsa (UIDVALIDITY değişmiş olabilir) yerel kayıtlara dokunulmaz.
     */
    private fun findMissingUids(folder: Folder, existingEmails: List<EmailEntity>): Set<Long> {
        val uidFolder = folder as? UIDFolder ?: return emptySet()
        val localUids = existingEmails
            .filter { it.serverUid > 0 && it.folderName == folder.fullName }
            .map { it.serverUid }
            .distinct()
        if (localUids.isEmpty()) return emptySet()

        val missing = mutableSetOf<Long>()
        for (chunk in localUids.chunked(200)) {
            val found = uidFolder.getMessagesByUID(chunk.toLongArray())
            chunk.forEachIndexed { index, uid ->
                if (found[index] == null) missing.add(uid)
            }
        }
        return if (missing.size == localUids.size && localUids.size > 20) emptySet() else missing
    }

    /**
     * Extracts emails from an open folder with UID cache checking.
     * Skips expensive body download if email is already in local database!
     */
    fun extractEmailsFromFolder(
        targetFolder: Folder,
        accountEmail: String,
        limit: Int,
        existingEmails: List<EmailEntity> = emptyList(),
        deadlineMs: Long = Long.MAX_VALUE,
        onDeadline: () -> Unit = {}
    ): List<EmailEntity> {
        val total = targetFolder.messageCount
        if (total == 0) return emptyList()

        val start = (total - limit + 1).coerceAtLeast(1)
        val messages = targetFolder.getMessages(start, total)

        val existingByUid = existingEmails.filter { it.serverUid > 0 }.associateBy { "${it.serverUid}_${it.folderName}" }
        val existingByMsgId = existingEmails.filter { !it.messageId.isNullOrBlank() }.associateBy { it.messageId!! }

        // Önce her posta için küçük bilgi alınır; ağır alanlar sadece yeni postalar için istenir
        try {
            val lightProfile = FetchProfile().apply {
                add(FetchProfile.Item.FLAGS)
                add(UIDFolder.FetchProfileItem.UID)
                add("Message-ID")
            }
            targetFolder.fetch(messages, lightProfile)
        } catch (_: Exception) {}

        val uidOf = { msg: Message ->
            try {
                (targetFolder as? UIDFolder)?.getUID(msg) ?: (targetFolder as? IMAPFolder)?.getUID(msg) ?: 0L
            } catch (_: Exception) { 0L }
        }
        val messageIdOf = { msg: Message ->
            try { msg.getHeader("Message-ID")?.firstOrNull() } catch (_: Exception) { null }
        }
        val newMessages = messages.filter { msg ->
            messageIdOf(msg)?.let { existingByMsgId[it] } == null &&
                existingByUid["${uidOf(msg)}_${targetFolder.fullName}"] == null
        }
        if (newMessages.isNotEmpty()) {
            try {
                val fullProfile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.SIZE)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add("List-Unsubscribe")
                }
                targetFolder.fetch(newMessages.toTypedArray(), fullProfile)
            } catch (_: Exception) {}
        }

        val isTrashFolder = MailFolderNames.isTrash(targetFolder.fullName) || MailFolderNames.isHold(targetFolder.fullName)
        val isInboxFolder = targetFolder.fullName.equals("INBOX", ignoreCase = true)
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
                val uid = uidOf(msg)
                val msgIdHeader = messageIdOf(msg)

                val isSeen = msg.isSet(Flags.Flag.SEEN)
                val isFlagged = msg.isSet(Flags.Flag.FLAGGED)

                // FAST PATH: Check if email is already in database
                val existing = (msgIdHeader?.let { existingByMsgId[it] }) ?: existingByUid["${uid}_${targetFolder.fullName}"]
                if (existing != null) {
                    val updated = existing.copy(
                        isRead = if (isSentFolder) true else isSeen,
                        isStarred = isFlagged,
                        isTrash = isTrashFolder,
                        isArchived = if (isInboxFolder) false else existing.isArchived,
                        categoryId = if (isSpamFolder) SmartCategory.SPAM.id else existing.categoryId,
                        folderName = targetFolder.fullName,
                        serverUid = uid
                    )
                    resultList.add(updated)
                    continue
                }

                // Süre dolduysa kalan yeni postalar sonraki eşitlemeye bırakılır (en yeniler önce gelir)
                if (System.currentTimeMillis() >= deadlineMs) {
                    onDeadline()
                    continue
                }

                // NEW EMAIL: Parse envelope and content
                val subject = msg.subject ?: t("(Konusuz)")
                val fromAddresses: Array<Address>? = msg.from
                val fromAddress = fromAddresses?.firstOrNull() as? InternetAddress
                val senderName = fromAddress?.personal?.takeIf { it.isNotBlank() }
                    ?: fromAddress?.address?.substringBefore("@")
                    ?: t("Bilinmeyen Gönderen")
                val senderEmail = fromAddress?.address ?: accountEmail
                val date = msg.sentDate ?: msg.receivedDate ?: java.util.Date()
                val timestamp = date.time

                val (extractedBody, hasAttachment, attachmentName, htmlHasTracker) = extractContent(msg)
                val preview = HtmlText.preview(extractedBody)

                val sizeKb = (msg.size / 1024L).coerceAtLeast(12L)

                val listUnsubHeader = msg.getHeader("List-Unsubscribe")?.firstOrNull()
                val isMailingList = !listUnsubHeader.isNullOrBlank() ||
                        extractedBody.contains("unsubscribe", ignoreCase = true) ||
                        extractedBody.contains("abonelikten çık", ignoreCase = true)

                val unsubUrl = extractUnsubscribeUrl(listUnsubHeader, extractedBody)

                val hasTrackingPixel = htmlHasTracker ||
                        extractedBody.contains("open.track", ignoreCase = true) ||
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
        existingEmails: List<EmailEntity> = emptyList(),
        budgetMs: Long = SYNC_BUDGET_MS,
        previousFingerprint: String? = null
    ): Result<MultiFolderSyncResult> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        val deadlineMs = System.currentTimeMillis() + budgetMs
        var timedOut = false
        var folderFailed = false
        val markTimedOut = { timedOut = true }
        var store: Store? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val missingUids = mutableMapOf<String, Set<Long>>()
            val versionsByKey = linkedMapOf<String, MutableList<EmailEntity>>()
            val known = existingEmails.toMutableList()
            val scannedFolders = mutableSetOf<String>()
            val memberships = mutableListOf<FolderMembership>()

            // 1. Sunucudaki klasörleri bul
            val rawFolders = try {
                store.defaultFolder.list("*")
            } catch (_: Exception) {
                store.defaultFolder.list()
            }
            val syncFolders = rawFolders
                .filter { f -> try { (f.type and Folder.HOLDS_MESSAGES) != 0 } catch (_: Exception) { false } }
                .map { f -> f to categorizeFolder(f.fullName, f.name, folderAttributes(f)) }
                .sortedBy { (_, info) -> syncRank(info.first) }

            // Sunucuda hiçbir şey değişmediyse klasörler yeniden taranmaz
            val hasModSeq = (store as? IMAPStore)?.hasCapability("CONDSTORE") == true
            val statuses = syncFolders.map { (folder, _) -> readFolderStatus(folder, hasModSeq) }
            val fingerprint = folderFingerprint(syncFolders.map { it.first.fullName }, statuses, hasModSeq)
            if (fingerprint != null && fingerprint == previousFingerprint) {
                val folders = sortFolders(syncFolders.mapIndexed { index, (folder, info) ->
                    MailFolderInfo(folder.fullName, info.second, info.first, statuses[index]!!.total, statuses[index]!!.unseen)
                })
                val countOfFolder = { category: MailFolderCategory ->
                    folders.firstOrNull { it.category == category }?.totalCount ?: 0
                }
                return@withContext Result.success(
                    MultiFolderSyncResult(
                        emails = emptyList(),
                        folders = folders,
                        inboxCount = countOfFolder(MailFolderCategory.INBOX),
                        trashCount = countOfFolder(MailFolderCategory.TRASH) + countOfFolder(MailFolderCategory.HOLD),
                        spamCount = countOfFolder(MailFolderCategory.SPAM),
                        purchasesCount = 0,
                        fingerprint = fingerprint,
                        unchanged = true
                    )
                )
            }

            val folderList = mutableListOf<MailFolderInfo>()
            val categoryByFolder = mutableMapOf<String, MailFolderCategory>()
            var purchaseUids: List<Long>? = null
            var allMailName: String? = null
            val isGmail = (store as? IMAPStore)?.hasCapability("X-GM-EXT-1") == true

            // 2. Her klasörü tek bağlantıda tara; gövde her posta için bir kez indirilir
            for ((folder, info) in syncFolders) {
                val (category, displayName) = info
                categoryByFolder[folder.fullName] = category
                var total = 0
                var unread = 0
                try {
                    folder.open(Folder.READ_ONLY)
                    total = folder.messageCount
                    unread = folder.unreadMessageCount
                    val fetched = extractEmailsFromFolder(
                        folder, account.emailAddress, MAX_MESSAGES_PER_FOLDER, known, deadlineMs, markTimedOut
                    )
                    missingUids[folder.fullName] = findMissingUids(folder, existingEmails)
                    scannedFolders.add(folder.fullName)
                    for (email in fetched) {
                        val key = FolderMerge.messageKey(email)
                        versionsByKey.getOrPut(key) { mutableListOf() }.add(email)
                        memberships.add(FolderMembership(key, folder.fullName, email.serverUid))
                        if (email.id == 0L) known.add(email)
                    }
                    if (isGmail && category == MailFolderCategory.ALL_MAIL && folder is IMAPFolder) {
                        allMailName = folder.fullName
                        purchaseUids = try {
                            gmailSearchUids(folder, "category:purchases")
                        } catch (_: Exception) {
                            null
                        }
                    }
                } catch (_: Exception) {
                    folderFailed = true
                } finally {
                    try { if (folder.isOpen) folder.close(false) } catch (_: Exception) {}
                }
                folderList.add(MailFolderInfo(folder.fullName, displayName, category, total, unread))
            }

            // 3. Gmail Käufe kategorisi: All Mail içindeki UID'lerden sanal klasör
            val uids = purchaseUids
            if (uids != null && allMailName != null) {
                val uidSet = uids.toSet()
                val inCategory = memberships.filter { it.folderName == allMailName && it.serverUid in uidSet }
                val unread = inCategory.count { member ->
                    versionsByKey[member.messageKey]?.firstOrNull()?.isRead == false
                }
                memberships.addAll(inCategory.map { it.copy(folderName = GMAIL_PURCHASES_FOLDER) })
                scannedFolders.add(GMAIL_PURCHASES_FOLDER)
                categoryByFolder[GMAIL_PURCHASES_FOLDER] = MailFolderCategory.PURCHASES
                folderList.add(
                    MailFolderInfo(
                        GMAIL_PURCHASES_FOLDER, "Alışverişler (Käufe)", MailFolderCategory.PURCHASES, uids.size, unread
                    )
                )
            }

            val merged = versionsByKey.values.map { versions ->
                FolderMerge.merge(versions) { categoryByFolder[it] ?: MailFolderCategory.CUSTOM }
            }
            val sortedFolders = sortFolders(folderList)
            val countOf = { category: MailFolderCategory ->
                sortedFolders.firstOrNull { it.category == category }?.totalCount ?: 0
            }

            Result.success(
                MultiFolderSyncResult(
                    emails = merged,
                    folders = sortedFolders,
                    inboxCount = countOf(MailFolderCategory.INBOX),
                    trashCount = countOf(MailFolderCategory.TRASH) + countOf(MailFolderCategory.HOLD),
                    spamCount = countOf(MailFolderCategory.SPAM),
                    purchasesCount = countOf(MailFolderCategory.PURCHASES),
                    missingUids = missingUids,
                    isPartial = timedOut || folderFailed,
                    memberships = memberships,
                    scannedFolders = scannedFolders,
                    timedOut = timedOut,
                    fingerprint = fingerprint
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
        val cleanPass = resolvePassword(account)
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
        moveToTrash: Boolean = true,
        holdFolder: String? = null
    ): Result<MoveOutcome> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var sourceFolder: Folder? = null
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
            if (msg == null && !sourceFolder.fullName.equals("INBOX", ignoreCase = true)) {
                val inbox = store.getFolder("INBOX")
                if (inbox.exists()) {
                    sourceFolder.close(false)
                    inbox.open(Folder.READ_WRITE)
                    sourceFolder = inbox
                    msg = findMessageOnFolder(sourceFolder, email)
                }
            }

            val trashFolder = findTrashFolder(store)
            val isAlreadyTrash = MailFolderNames.isTrash(sourceFolder.fullName) ||
                    (trashFolder != null && sourceFolder.fullName.equals(trashFolder.fullName, ignoreCase = true))

            if (msg == null) {
                // Silme isteğinde çöp kutusuna da bakılır; posta başka yerde değilse bulunamadı döner
                if (!moveToTrash && trashFolder != null && !isAlreadyTrash) {
                    val deleted = deleteFromFolderById(trashFolder, email)
                    return@withContext Result.success(MoveOutcome(found = deleted))
                }
                return@withContext Result.success(MoveOutcome(found = false))
            }

            if (moveToTrash) {
                val destination = holdFolder?.let { holdTarget(store, it) } ?: trashFolder
                if (isAlreadyTrash || destination?.fullName == sourceFolder.fullName) {
                    return@withContext Result.success(MoveOutcome(found = true, targetFolder = sourceFolder.fullName))
                }
                if (destination == null) {
                    return@withContext Result.failure(Exception(t(TRASH_NOT_FOUND)))
                }
                safeMove(store, sourceFolder, arrayOf(msg), destination)
                return@withContext Result.success(MoveOutcome(found = true, targetFolder = destination.fullName))
            }

            // Kalıcı silme
            if (isAlreadyTrash || trashFolder == null) {
                msg.setFlag(Flags.Flag.DELETED, true)
                sourceFolder.expunge()
            } else {
                // Gmail'de kalıcı silme için posta önce çöp kutusuna taşınmalıdır
                safeMove(store, sourceFolder, arrayOf(msg), trashFolder)
                deleteFromFolderById(trashFolder, email)
            }
            Result.success(MoveOutcome(found = true))
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { if (sourceFolder?.isOpen == true) sourceFolder.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    private fun deleteFromFolderById(folder: Folder, email: EmailEntity): Boolean {
        val wasOpen = folder.isOpen
        if (!wasOpen) folder.open(Folder.READ_WRITE)
        try {
            val msg = findMessageOnFolder(folder, email) ?: return false
            msg.setFlag(Flags.Flag.DELETED, true)
            folder.expunge()
            return true
        } finally {
            if (!wasOpen) {
                try { folder.close(false) } catch (_: Exception) {}
            }
        }
    }

    /**
     * Postayı arşive veya Gelen Kutusu'na taşır. Kaynak olarak kayıtlı klasöre,
     * çöp kutusuna ve arşive bakılır. Eşleşme sadece Message-ID / UID ile yapılır.
     */
    suspend fun moveEmailOnServer(
        account: EmailAccountEntity,
        email: EmailEntity,
        target: MoveTarget
    ): Result<MoveOutcome> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val targetFolder = when (target) {
                MoveTarget.INBOX -> store.getFolder("INBOX")
                MoveTarget.ARCHIVE -> findArchiveFolder(store)
            } ?: return@withContext Result.failure(Exception(t("Sunucuda arşiv klasörü bulunamadı.")))

            val sources = listOfNotNull(
                store.getFolder(email.folderName.ifBlank { "INBOX" }),
                findTrashFolder(store),
                if (target == MoveTarget.INBOX) findArchiveFolder(store) else store.getFolder("INBOX")
            ).distinctBy { it.fullName }

            for (source in sources) {
                if (source.fullName == targetFolder.fullName || !source.exists()) continue
                try {
                    source.open(Folder.READ_WRITE)
                    val msg = findMessageOnFolder(source, email) ?: continue
                    safeMove(store, source, arrayOf(msg), targetFolder)
                    return@withContext Result.success(MoveOutcome(found = true, targetFolder = targetFolder.fullName))
                } finally {
                    try { if (source.isOpen) source.close(false) } catch (_: Exception) {}
                }
            }
            Result.success(MoveOutcome(found = false, targetFolder = targetFolder.fullName))
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Batch move multiple emails to Trash on the IMAP server (Gmail).
     * Connects once and efficiently moves all specified emails to the real Trash / Papierkorb.
     */
    private fun holdTarget(store: Store, name: String): Folder {
        val folder = store.getFolder(name)
        if (!folder.exists()) folder.create(Folder.HOLDS_MESSAGES)
        return folder
    }

    /**
     * Postalara uygulama etiketi ekler. Gmail'de kopyalama etiket eklemek demektir,
     * posta bulunduğu yerde kalır. Başarılı postaların kimlikleri döner.
     */
    suspend fun copyToLabels(
        account: EmailAccountEntity,
        emailsByLabel: Map<String, List<EmailEntity>>
    ): Result<Map<String, List<Long>>> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        val done = mutableMapOf<String, List<Long>>()
        try {
            store = createConnectedStore(account, cleanPass)
            for ((label, emails) in emailsByLabel) {
                if (!label.startsWith(MailFolderNames.APP_LABEL_PREFIX)) continue
                val target = holdTarget(store, label)
                val labelled = mutableListOf<Long>()
                for ((sourceName, group) in emails.groupBy { it.folderName }) {
                    val source = store.getFolder(sourceName)
                    if (!source.exists()) continue
                    source.open(Folder.READ_ONLY)
                    try {
                        val found = (source as UIDFolder).getMessagesByUID(group.map { it.serverUid }.toLongArray())
                        val pairs = group.zip(found.toList()).filter { it.second != null }
                        if (pairs.isNotEmpty()) {
                            source.copyMessages(pairs.map { it.second }.toTypedArray(), target)
                            labelled.addAll(pairs.map { it.first.id })
                        }
                    } finally {
                        try { if (source.isOpen) source.close(false) } catch (_: Exception) {}
                    }
                }
                done[label] = labelled
            }
            Result.success(done)
        } catch (e: Exception) {
            Result.failure(Exception(parseFriendlyMailError(e, account.emailAddress), e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /** Uygulama etiketini kaldırır. Yalnızca EM26 etiketlerine dokunur; posta silinmez. */
    suspend fun removeFromLabel(
        account: EmailAccountEntity,
        label: String,
        emails: List<EmailEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!label.startsWith(MailFolderNames.APP_LABEL_PREFIX) || MailFolderNames.isHold(label)) {
            return@withContext Result.success(0)
        }
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var folder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            folder = store.getFolder(label)
            if (!folder.exists()) return@withContext Result.success(0)
            folder.open(Folder.READ_WRITE)
            val messages = emails.mapNotNull { findMessageOnFolder(folder, it) }
            if (messages.isNotEmpty()) {
                folder.setFlags(messages.toTypedArray(), Flags(Flags.Flag.DELETED), true)
                folder.expunge()
            }
            Result.success(messages.size)
        } catch (e: Exception) {
            Result.failure(Exception(parseFriendlyMailError(e, account.emailAddress), e))
        } finally {
            try { if (folder?.isOpen == true) folder.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /** Okundu bilgisini tek bağlantıda ve klasör başına tek komutla değiştirir. */
    suspend fun batchUpdateReadOnServer(
        account: EmailAccountEntity,
        emails: List<EmailEntity>,
        isRead: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (emails.isEmpty()) return@withContext Result.success(0)
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var updated = 0
        try {
            store = createConnectedStore(account, cleanPass)
            for ((folderName, group) in emails.groupBy { it.folderName.ifBlank { "INBOX" } }) {
                val folder = store.getFolder(folderName)
                if (!folder.exists()) continue
                folder.open(Folder.READ_WRITE)
                try {
                    val messages = group.mapNotNull { findMessageOnFolder(folder, it) }
                    if (messages.isNotEmpty()) {
                        folder.setFlags(messages.toTypedArray(), Flags(Flags.Flag.SEEN), isRead)
                        updated += messages.size
                    }
                } finally {
                    try { if (folder.isOpen) folder.close(false) } catch (_: Exception) {}
                }
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(Exception(parseFriendlyMailError(e, account.emailAddress), e))
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Çöp kutusundaki postaları tek bağlantıda kalıcı siler.
     * Yalnızca sunucunun çöp klasöründeki postalara dokunur; diğerleri başarısız sayılır.
     */
    suspend fun batchDeleteFromTrashOnServer(
        account: EmailAccountEntity,
        emails: List<EmailEntity>
    ): Result<BatchMoveResult> = withContext(Dispatchers.IO) {
        if (emails.isEmpty()) return@withContext Result.success(BatchMoveResult(emptyList(), emptyList(), null))
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var trashFolder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val trash = findTrashFolder(store)
                ?: return@withContext Result.failure(Exception(t(TRASH_NOT_FOUND)))
            trashFolder = trash
            trash.open(Folder.READ_WRITE)
            val deletedIds = mutableListOf<Long>()
            val failedIds = mutableListOf<Long>()
            val messages = mutableListOf<Message>()
            for (email in emails) {
                val message = findMessageOnFolder(trash, email)
                if (message != null) {
                    messages.add(message)
                    deletedIds.add(email.id)
                } else {
                    failedIds.add(email.id)
                }
            }
            if (messages.isNotEmpty()) {
                trash.setFlags(messages.toTypedArray(), Flags(Flags.Flag.DELETED), true)
                trash.expunge()
            }
            Result.success(BatchMoveResult(deletedIds, failedIds, trash.fullName))
        } catch (e: Exception) {
            Result.failure(Exception(parseFriendlyMailError(e, account.emailAddress), e))
        } finally {
            try { if (trashFolder?.isOpen == true) trashFolder.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    suspend fun batchMoveToTrashOnServer(
        account: EmailAccountEntity,
        emails: List<EmailEntity>,
        holdFolder: String? = null
    ): Result<BatchMoveResult> = withContext(Dispatchers.IO) {
        if (emails.isEmpty()) return@withContext Result.success(BatchMoveResult(emptyList(), emptyList(), null))
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        val movedIds = mutableListOf<Long>()
        val failedIds = mutableListOf<Long>()
        var lastError: String? = null
        try {
            store = createConnectedStore(account, cleanPass)
            val trashFolder = holdFolder?.let { holdTarget(store, it) } ?: findTrashFolder(store)
                ?: return@withContext Result.failure(Exception(t(TRASH_NOT_FOUND)))

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
                    if (folder.fullName.equals(trashFolder.fullName, ignoreCase = true)) {
                        movedIds.addAll(group.map { it.id })
                        continue
                    }
                    folder.open(Folder.READ_WRITE)

                    val msgsToTrash = mutableListOf<Message>()
                    val matchedIds = mutableListOf<Long>()
                    for (email in group) {
                        val msg = findMessageOnFolder(folder, email)
                        if (msg != null) {
                            msgsToTrash.add(msg)
                            matchedIds.add(email.id)
                        }
                    }

                    if (msgsToTrash.isNotEmpty()) {
                        safeMove(store, folder, msgsToTrash.toTypedArray(), trashFolder)
                        movedIds.addAll(matchedIds)
                    }
                } catch (e: Exception) {
                    failedIds.addAll(group.map { it.id })
                    lastError = parseFriendlyMailError(e, account.emailAddress)
                } finally {
                    try { if (folder?.isOpen == true) folder.close(false) } catch (_: Exception) {}
                }
            }

            Result.success(BatchMoveResult(movedIds, failedIds, trashFolder.fullName, lastError))
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
        senderEmail: String,
        holdFolder: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var inbox: Folder? = null
        var trashFolder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            // Locate Trash folder (supports German Papierkorb)
            trashFolder = holdFolder?.let { holdTarget(store, it) } ?: findTrashFolder(store)
                ?: return@withContext Result.failure(Exception(t(TRASH_NOT_FOUND)))

            val total = inbox.messageCount
            if (total == 0) return@withContext Result.success(0)

            val wantedSender = senderEmail.trim()
            val term = FromStringTerm(wantedSender)
            val searched = try {
                inbox.search(term)
            } catch (_: Exception) {
                val start = (total - 100 + 1).coerceAtLeast(1)
                inbox.getMessages(start, total).filter { msg ->
                    msg.from?.any { it.toString().contains(senderEmail, ignoreCase = true) } == true
                }.toTypedArray()
            }
            // FROM araması parça eşleşmesi yapar; sadece aynı adres kabul edilir
            val matched = searched.filter { msg ->
                msg.from?.any { (it as? InternetAddress)?.address.equals(wantedSender, ignoreCase = true) } == true
            }.toTypedArray()

            if (matched.isNotEmpty()) {
                safeMove(store, inbox, matched, trashFolder)
            }

            Result.success(matched.size)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        } finally {
            try { if (inbox?.isOpen == true) inbox.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Permanently deletes all emails from the Trash folder on the IMAP server (Gmail).
     * Gerçek Gmail Çöp Kutusunu tamamen boşaltır.
     */
    suspend fun emptyTrashOnServer(account: EmailAccountEntity): Result<Int> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
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
        val cleanPass = resolvePassword(account)
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

    /**
     * Postayı sadece kesin kimlikle bulur: Message-ID veya aynı klasördeki UID.
     * Konu ile arama yapılmaz; aynı konulu başka bir posta asla seçilmez.
     */
    private fun findMessageOnFolder(folder: Folder, email: EmailEntity): Message? {
        val wantedId = email.messageId?.trim()
        if (!wantedId.isNullOrBlank()) {
            try {
                val found = folder.search(HeaderTerm("Message-ID", wantedId)).firstOrNull { candidate ->
                    candidate.getHeader("Message-ID")?.any { it.trim() == wantedId } == true
                }
                if (found != null) return found
            } catch (_: Exception) {}
        }

        // UID sadece kayıtlı klasörde anlamlıdır
        if (email.serverUid > 0L && folder.fullName == email.folderName) {
            try {
                val byUid = (folder as? UIDFolder)?.getMessageByUID(email.serverUid)
                if (byUid != null) {
                    val uidMessageId = byUid.getHeader("Message-ID")?.firstOrNull()?.trim()
                    val sameId = wantedId.isNullOrBlank() || uidMessageId == null || uidMessageId == wantedId
                    val uidSubject = byUid.subject ?: t("(Konusuz)")
                    if (sameId && (email.subject.isBlank() || uidSubject == email.subject)) {
                        return byUid
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Postanın güncel List-Unsubscribe başlıklarını sunucudan okur.
     * Posta bulunamazsa kayıtlı bağlantıya geri dönülür.
     */
    suspend fun fetchUnsubscribeOptions(
        account: EmailAccountEntity,
        email: EmailEntity
    ): Result<UnsubscribeOptions> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var folder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            folder = store.getFolder(email.folderName.ifBlank { "INBOX" }).takeIf { it.exists() }
                ?: store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val msg = findMessageOnFolder(folder, email)
            Result.success(
                UnsubscribeParser.parse(
                    listUnsubscribe = msg?.getHeader("List-Unsubscribe")?.joinToString(","),
                    listUnsubscribePost = msg?.getHeader("List-Unsubscribe-Post")?.firstOrNull(),
                    fallbackUrl = email.unsubscribeUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(parseFriendlyMailError(e, account.emailAddress), e))
        } finally {
            try { if (folder?.isOpen == true) folder.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /** RFC 8058 tek tık abonelikten çıkma isteği gönderir. */
    suspend fun postOneClickUnsubscribe(url: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(url)
                .post("List-Unsubscribe=One-Click".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Postanın eklerini indirip targetDir altına kaydeder.
     */
    suspend fun downloadAttachments(
        account: EmailAccountEntity,
        email: EmailEntity,
        targetDir: File
    ): Result<List<SavedAttachment>> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        var store: Store? = null
        var folder: Folder? = null
        try {
            store = createConnectedStore(account, cleanPass)
            folder = store.getFolder(email.folderName.ifBlank { "INBOX" }).takeIf { it.exists() }
                ?: store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val msg = findMessageOnFolder(folder, email)
                ?: return@withContext Result.failure(Exception(t("E-posta sunucuda bulunamadı.")))

            targetDir.mkdirs()
            val saved = mutableListOf<SavedAttachment>()

            fun collect(part: Part) {
                if (part.isMimeType("multipart/*")) {
                    val multipart = part.content as? Multipart ?: return
                    for (i in 0 until multipart.count) collect(multipart.getBodyPart(i))
                    return
                }
                val rawName = part.fileName ?: return
                val name = safeAttachmentName(rawName, saved.size)
                val file = uniqueFile(targetDir, name)
                part.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                saved.add(SavedAttachment(name = file.name, mimeType = attachmentMimeType(part.contentType, file.name), file = file))
            }
            collect(msg)

            if (saved.isEmpty()) {
                Result.failure(Exception(t("Bu e-postada indirilebilir ek bulunamadı.")))
            } else {
                Result.success(saved)
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseFriendlyMailError(e, account.emailAddress), e))
        } finally {
            try { if (folder?.isOpen == true) folder.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /** Sunucudan gelen dosya adındaki klasör yollarını temizler. */
    fun safeAttachmentName(rawName: String, index: Int): String {
        val decoded = try { MimeUtility.decodeText(rawName) } catch (_: Exception) { rawName }
        val leaf = decoded.substringAfterLast('/').substringAfterLast('\\')
        val clean = leaf.replace(Regex("[\\x00-\\x1f:*?\"<>|]"), "_").trim().trimStart('.')
        return clean.ifBlank { "ek_${index + 1}" }.take(120)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${counter}_$name")
            counter++
        }
        return candidate
    }

    private fun attachmentMimeType(contentType: String?, fileName: String): String {
        val declared = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (declared.isNotBlank() && declared != "application/octet-stream") return declared
        return URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
    }

    /**
     * Send a real email via SMTP.
     */
    suspend fun sendEmail(
        account: EmailAccountEntity,
        to: String,
        subject: String,
        body: String,
        replyToOriginalSubject: String? = null,
        inReplyToMessageId: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanPass = resolvePassword(account)
        try {
            val props = Properties().apply {
                put("mail.transport.protocol", "smtp")
                put("mail.smtp.host", account.smtpHost)
                put("mail.smtp.port", account.smtpPort.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "15000")

                if (account.smtpPort == 465 || account.smtpSsl) {
                    put("mail.smtp.ssl.enable", "true")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                sessionPropertyOverrides.forEach { (key, value) -> put(key, value) }
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

                // Gmail yanıtı bu başlıklarla aynı konuşmaya bağlar
                if (!inReplyToMessageId.isNullOrBlank()) {
                    setHeader("In-Reply-To", inReplyToMessageId)
                    setHeader("References", inReplyToMessageId)
                }
            }

            Transport.send(message)
            Result.success(true)
        } catch (e: Exception) {
            val friendly = parseFriendlyMailError(e, account.emailAddress)
            Result.failure(Exception(friendly, e))
        }
    }

    private data class ExtractedContent(
        val body: String,
        val hasAttachment: Boolean,
        val attachmentName: String?,
        val hasTrackingPixel: Boolean
    )

    private fun extractContent(part: Part): ExtractedContent {
        var hasTrackingPixel = false
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
                // HTML metne çevrilmeden önce bakılır, görsel etiketleri sonra kaybolur
                if (htmlContent != null && HtmlText.hasTrackingPixel(htmlContent)) {
                    hasTrackingPixel = true
                }
                if (htmlContent != null && bodyBuilder.isBlank()) {
                    bodyBuilder.append(HtmlText.toPlainText(htmlContent)).append("\n")
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
            bodyBuilder.append(t("(İçerik ayrıştırılırken hata oluştu: {0})", e.message))
        }

        val resultBody = bodyBuilder.toString().trim().ifEmpty { t("(Boş e-posta içeriği)") }
        return ExtractedContent(resultBody, hasAttachment, attachmentName, hasTrackingPixel)
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
