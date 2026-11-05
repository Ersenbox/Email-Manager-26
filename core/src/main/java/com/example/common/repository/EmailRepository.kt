package com.example.common.repository

import com.example.common.bridge.HapticType
import com.example.common.bridge.PlatformBridge
import com.example.common.bridge.SoundType
import com.example.common.data.AccountDao
import com.example.common.data.CustomLabelEntity
import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailDao
import com.example.common.data.EmailEntity
import com.example.common.data.EmailFolderLink
import com.example.common.data.RuleEntity
import com.example.common.data.SenderOverride
import com.example.common.filter.AutoFolder
import com.example.common.filter.MailClassifier
import com.example.common.filter.SmartFilterEngine
import com.example.common.i18n.t
import com.example.common.model.CleanSenderGroup
import com.example.common.model.SmartCategory
import com.example.common.security.Credentials
import com.example.common.service.FolderMerge
import com.example.common.service.MailFolderNames
import com.example.common.service.MailService
import com.example.common.settings.AppSettings
import com.example.common.service.MoveTarget
import com.example.common.service.SavedAttachment
import com.example.common.service.UnsubscribeOptions
import com.example.common.service.UnsubscribeParser
import com.example.common.model.MailFolderInfo
import com.example.common.model.MailFolderCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val AUTO_LABEL_BATCH = 200
private const val MAX_MAIL_NOTIFICATIONS = 5

data class WeeklyReport(
    val received: Int,
    val unread: Int,
    val ads: Int,
    val newsletters: Int,
    val social: Int
)

data class BulkUnsubscribeResult(val done: Int, val needsPage: Int)

data class SyncSummary(
    val inboxCount: Int = 0,
    val trashCount: Int = 0,
    val spamCount: Int = 0,
    val purchasesCount: Int = 0,
    val totalFolders: Int = 0,
    val isPartial: Boolean = false,
    val folders: List<MailFolderInfo> = emptyList(),
    val timedOut: Boolean = false
)

class EmailRepository(
    private val emailDao: EmailDao,
    private val accountDao: AccountDao,
    private val platformBridge: PlatformBridge
) {
    val activeEmails: Flow<List<EmailEntity>> = emailDao.getAllActiveEmails()
    val trashEmails: Flow<List<EmailEntity>> = emailDao.getTrashEmails()
    val archivedEmails: Flow<List<EmailEntity>> = emailDao.getArchivedEmails()
    val folderLinks: Flow<List<EmailFolderLink>> = emailDao.getFolderLinks()
    val rules: Flow<List<RuleEntity>> = emailDao.getAllRules()
    val customLabels: Flow<List<CustomLabelEntity>> = emailDao.getAllLabels()
    val accounts: Flow<List<EmailAccountEntity>> = accountDao.getAllAccounts()

    val cleanSenderGroups: Flow<List<CleanSenderGroup>> = activeEmails.map { emails ->
        SmartFilterEngine.generateCleanSenderGroups(emails)
    }

    // In-memory cache of deleted/trashed message identifiers to prevent sync from resurrecting them
    private val deletedIdentifiers = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Sunucu işlemi süren postalar; eşitleme bu kayıtları değiştirmez
    private val pendingServerIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    // Son tam eşitlemenin özeti; sunucu değişmediyse tarama atlanır
    private data class CompletedSync(val fingerprint: String, val folders: List<MailFolderInfo>)
    private val completedSyncs = java.util.concurrent.ConcurrentHashMap<String, CompletedSync>()

    private fun isGmail(account: EmailAccountEntity): Boolean =
        account.imapHost.contains("gmail", ignoreCase = true)

    // Otomasyon kararları; testler bunları değiştirebilir
    var holdFolderFor: (EmailAccountEntity) -> String? = { account ->
        if (AppSettings.keepDeletedEmails() && isGmail(account)) MailFolderNames.HOLD_FOLDER else null
    }
    var autoLabelsEnabled: (EmailAccountEntity) -> Boolean = { account ->
        AppSettings.autoLabels() && isGmail(account)
    }
    var spamDeleteDays: () -> Int = { AppSettings.spamAutoDeleteDays() }
    var adCleanupDays: () -> Int = { AppSettings.adCleanupDays() }

    /** Eşitlemede gelen yeni önemli postalar; arka plan işi bildirim gösterir. */
    var onNewImportantMail: (List<EmailEntity>) -> Unit = {}

    val blockedSenders: Flow<List<SenderOverride>> = emailDao.getSenderOverridesByFolder(MailClassifier.BLOCKED)

    suspend fun allAccounts(): List<EmailAccountEntity> = accountDao.getAllAccountsList()

    private fun hasServerIdentity(email: EmailEntity): Boolean =
        email.serverUid > 0L || !email.messageId.isNullOrBlank()

    /** Postanın ait olduğu hesap. Hesabı kayıtlı olmayan posta için başka hesap kullanılmaz. */
    private suspend fun accountFor(email: EmailEntity): EmailAccountEntity? {
        if (email.accountEmail.isNotBlank()) {
            return accountDao.getAllAccountsList()
                .firstOrNull { it.emailAddress.equals(email.accountEmail, ignoreCase = true) }
        }
        return accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()
    }

    private suspend fun groupByAccount(emails: List<EmailEntity>): Map<EmailAccountEntity?, List<EmailEntity>> {
        val result = linkedMapOf<EmailAccountEntity?, MutableList<EmailEntity>>()
        for (email in emails) {
            result.getOrPut(accountFor(email)) { mutableListOf() }.add(email)
        }
        return result
    }

    /** UID sadece hesap ve klasör içinde benzersizdir; anahtar bu ikisini de içerir. */
    private fun deletionKeys(email: EmailEntity): List<String> {
        val keys = mutableListOf<String>()
        if (!email.messageId.isNullOrBlank()) {
            keys.add("${email.accountEmail}|mid|${email.messageId}")
        }
        if (email.serverUid > 0L) {
            keys.add("${email.accountEmail}|${email.folderName}|uid|${email.serverUid}")
        }
        return keys
    }

    private fun rememberDeleted(email: EmailEntity) {
        for (key in deletionKeys(email)) {
            deletedIdentifiers.add(key)
            platformBridge.addDeletedIdentifier(key)
        }
    }

    private fun forgetDeleted(email: EmailEntity) {
        val keys = deletionKeys(email) + listOfNotNull(email.messageId)
        for (key in keys) {
            deletedIdentifiers.remove(key)
            platformBridge.removeDeletedIdentifier(key)
        }
    }

    private fun isMarkedDeleted(raw: EmailEntity): Boolean {
        val keys = deletionKeys(raw) + listOfNotNull(raw.messageId)
        return keys.any { deletedIdentifiers.contains(it) || platformBridge.isIdentifierDeleted(it) }
    }

    /** Önceki sürümlerden kalan düz metin şifreleri Android Keystore ile şifreler. */
    suspend fun migrateStoredPasswords() {
        for (account in accountDao.getAllAccountsList()) {
            if (!Credentials.codec.isEncrypted(account.password)) {
                accountDao.updateAccount(account.copy(password = Credentials.codec.encrypt(account.password)))
            }
        }
    }

    suspend fun getActiveAccount(): EmailAccountEntity? {
        return accountDao.getActiveAccount()
    }

    suspend fun addOrUpdateAccount(account: EmailAccountEntity): Long {
        val isNewSecondary = account.id == 0L && accountDao.getAccountCount() > 0
        val previous = if (account.id != 0L) accountDao.getAccountById(account.id) else null
        val id = accountDao.insertAccount(
            account.copy(
                photoPath = account.photoPath ?: previous?.photoPath,
                verifiedAt = maxOf(account.verifiedAt, previous?.verifiedAt ?: 0L),
                googleVerifiedAt = maxOf(account.googleVerifiedAt, previous?.googleVerifiedAt ?: 0L),
                password = Credentials.codec.encrypt(account.password),
                isActive = if (isNewSecondary) false else account.isActive
            )
        )
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("{0} hesabı kaydedildi", account.emailAddress))
        return id
    }

    suspend fun deleteAccount(account: EmailAccountEntity) {
        accountDao.deleteAccount(account)
        completedSyncs.remove(account.emailAddress)
        try {
            emailDao.deleteEmailsByAccount(account.emailAddress)
            emailDao.deleteFolderLinksByAccount(account.emailAddress)
        } catch (_: Exception) {}
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
        platformBridge.showToastNotification(t("{0} hesabı kaldırıldı", account.emailAddress))
    }

    suspend fun setActiveAccount(id: Long) {
        accountDao.setActiveAccount(id)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
    }

    suspend fun testAccountConnection(account: EmailAccountEntity): Result<String> {
        val result = MailService.testConnection(account)
        if (result.isSuccess && account.id != 0L) {
            accountDao.updateVerifiedAt(account.id, System.currentTimeMillis())
        }
        return result
    }

    /**
     * Fetch original folders and message counts from server.
     */
    suspend fun fetchFolderHierarchy(account: EmailAccountEntity): Result<List<MailFolderInfo>> {
        return MailService.fetchFolderHierarchy(account)
    }

    /**
     * Fetch real emails via IMAP from the mail server (specific folder or INBOX).
     * No restrictions: fetches all emails up to limit (300) with complete read/unread state.
     */
    suspend fun syncAccountEmails(
        account: EmailAccountEntity,
        folderFullName: String = "INBOX",
        limit: Int = 60
    ): Result<Int> {
        val existingEmails = emailDao.getEmailsByAccount(account.emailAddress)
        val fetchResult = MailService.fetchEmails(account, folderFullName, limit, existingEmails)
        return if (fetchResult.isSuccess) {
            val rawEmails = fetchResult.getOrDefault(emptyList())
            if (rawEmails.isNotEmpty()) {
                val existingByMsgId = existingEmails.filter { !it.messageId.isNullOrBlank() }.associateBy { it.messageId }
                val existingByUidAndFolder = existingEmails.filter { it.serverUid > 0 }.associateBy { "${it.serverUid}_${it.folderName}" }

                val mappedEmails = rawEmails.mapNotNull { raw ->
                    // If this email was permanently deleted, skip it entirely
                    if (isMarkedDeleted(raw)) {
                        return@mapNotNull null
                    }

                    val existing = (raw.messageId?.let { existingByMsgId[it] })
                        ?: existingByUidAndFolder["${raw.serverUid}_${raw.folderName}"]

                    if (existing != null) {
                        if (existing.id in pendingServerIds) return@mapNotNull null
                        raw.copy(id = existing.id)
                    } else {
                        raw
                    }
                }

                val rules = emailDao.getEnabledRulesList()
                val emails = if (rules.isNotEmpty()) {
                    mappedEmails.map { SmartFilterEngine.applyRules(it, rules) }
                } else {
                    mappedEmails
                }
                emailDao.insertEmails(emails)
                accountDao.updateLastSyncTime(account.id, System.currentTimeMillis())
            }
            Result.success(rawEmails.size)
        } else {
            val err = fetchResult.exceptionOrNull()?.message ?: t("Senkronizasyon hatası")
            Result.failure(fetchResult.exceptionOrNull() ?: Exception(err))
        }
    }

    /**
     * Ultra-fast single-session synchronization across all critical Gmail folders:
     * - INBOX (all recent emails, read and unread, envelope-cached)
     * - Papierkorb / Trash (exact matching trash emails)
     * - Spam (exact matching spam emails)
     * Completes in ~1-2 seconds instead of minutes!
     */
    suspend fun syncAllAccountData(
        account: EmailAccountEntity,
        isManual: Boolean = true
    ): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val existingEmails = emailDao.getEmailsByAccount(account.emailAddress)
            val cached = completedSyncs[account.emailAddress]
            val fastResult = MailService.syncAllFoldersFast(
                account,
                existingEmails,
                previousFingerprint = cached?.fingerprint?.takeIf { existingEmails.isNotEmpty() }
            )

            if (fastResult.isSuccess) {
                val syncData = fastResult.getOrThrow()
                val rawEmails = syncData.emails

                val idByKey = mutableMapOf<String, Long>()
                if (rawEmails.isNotEmpty()) {
                    val existingByMsgId = existingEmails.filter { !it.messageId.isNullOrBlank() }.associateBy { it.messageId }
                    val existingByUidAndFolder = existingEmails.filter { it.serverUid > 0 }.associateBy { "${it.serverUid}_${it.folderName}" }

                    val mappedEmails = rawEmails.mapNotNull { raw ->
                        // If this email was permanently deleted, skip it entirely
                        if (isMarkedDeleted(raw)) {
                            return@mapNotNull null
                        }

                        val existing = (raw.messageId?.let { existingByMsgId[it] })
                            ?: existingByUidAndFolder["${raw.serverUid}_${raw.folderName}"]

                        if (existing != null) {
                            if (existing.id in pendingServerIds) {
                                idByKey[FolderMerge.messageKey(raw)] = existing.id
                                return@mapNotNull null
                            }
                            raw.copy(id = existing.id)
                        } else {
                            raw
                        }
                    }

                    val rules = emailDao.getEnabledRulesList()
                    val emails = mappedEmails.map { SmartFilterEngine.applyRules(SmartFilterEngine.refreshCategory(it), rules) }
                    // Değişmeyen postalar yeniden yazılmaz; liste gereksiz yere yenilenmez
                    val existingById = existingEmails.associateBy { it.id }
                    val (unchanged, changed) = emails.partition { it.id != 0L && existingById[it.id] == it }
                    unchanged.forEach { idByKey[FolderMerge.messageKey(it)] = it.id }
                    val ids = emailDao.insertEmailsReturningIds(changed)
                    changed.forEachIndexed { index, email -> idByKey[FolderMerge.messageKey(email)] = ids[index] }

                    // İlk eşitlemede tüm eski postalar için bildirim gönderilmez
                    if (existingEmails.isNotEmpty()) {
                        val importantNames = syncData.folders
                            .filter { it.category == MailFolderCategory.IMPORTANT }
                            .map { it.fullName }.toSet()
                        val importantKeys = syncData.memberships.filter { it.folderName in importantNames }.map { it.messageKey }.toSet()
                        val inboxKeys = syncData.memberships.filter { it.folderName.equals("INBOX", ignoreCase = true) }.map { it.messageKey }.toSet()
                        val newImportant = changed.filter { email ->
                            val key = FolderMerge.messageKey(email)
                            email.id == 0L && !email.isRead && key in importantKeys && key in inboxKeys
                        }
                        if (newImportant.isNotEmpty()) {
                            onNewImportantMail(newImportant.take(MAX_MAIL_NOTIFICATIONS))
                        }
                    }
                }

                if (!syncData.unchanged) {
                    val links = syncData.memberships.mapNotNull { member ->
                        idByKey[member.messageKey]?.let { id ->
                            EmailFolderLink(account.emailAddress, member.folderName, id, member.serverUid)
                        }
                    }
                    val storedLinks = emailDao.getFolderLinksByAccount(account.emailAddress)
                        .filter { it.folderName in syncData.scannedFolders }
                    if (storedLinks.toSet() != links.toSet()) {
                        emailDao.replaceFolderLinks(account.emailAddress, syncData.scannedFolders, links)
                    }
                }

                // Sunucuda artık bulunmayan postaları yerel listeden kaldır (sadece uygulama kopyası)
                val seenMessageIds = rawEmails.mapNotNull { it.messageId }.toSet()
                // Gönderim sırasında eklenen yerel kopya, sunucudaki gerçek kayıt gelince gereksizdir
                val serverSubjects = rawEmails.map { it.subject }.toSet()
                val localSentCopies = existingEmails.filter { local ->
                    local.serverUid == 0L && local.messageId == null &&
                            local.labelsCsv.contains("Gönderilen") && local.subject in serverSubjects
                }.map { it.id }
                val staleIds = existingEmails.filter { local ->
                    local.serverUid > 0L &&
                            local.id !in pendingServerIds &&
                            local.messageId !in seenMessageIds &&
                            syncData.missingUids[local.folderName]?.contains(local.serverUid) == true
                }.map { it.id }
                if (staleIds.isNotEmpty() || localSentCopies.isNotEmpty()) {
                    emailDao.deletePermanentlyBatch(staleIds + localSentCopies)
                }

                val folders = if (syncData.unchanged) cached?.folders ?: syncData.folders else syncData.folders
                val fingerprint = syncData.fingerprint
                if (fingerprint != null && !syncData.isPartial) {
                    completedSyncs[account.emailAddress] = CompletedSync(fingerprint, folders)
                } else {
                    completedSyncs.remove(account.emailAddress)
                }

                val now = System.currentTimeMillis()
                accountDao.updateLastSyncTime(account.id, now)
                accountDao.updateVerifiedAt(account.id, now)
                runSyncAutomations(account)
                if (isManual) {
                    platformBridge.playSound(SoundType.NEW_MAIL)
                    platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
                    platformBridge.showToastNotification(
                        if (syncData.isPartial) t("Eşitleme kısmen tamamlandı, kalan postalar sonraki eşitlemede alınacak")
                        else t("Gmail Hızlı Eşitlendi: {0} Gelen, {1} Çöp, {2} Spam ✅", syncData.inboxCount, syncData.trashCount, syncData.spamCount)
                    )
                }

                Result.success(
                    SyncSummary(
                        inboxCount = syncData.inboxCount,
                        trashCount = syncData.trashCount,
                        spamCount = syncData.spamCount,
                        purchasesCount = syncData.purchasesCount,
                        totalFolders = syncData.folders.size,
                        isPartial = syncData.isPartial,
                        folders = folders,
                        timedOut = syncData.timedOut
                    )
                )
            } else {
                val err = fastResult.exceptionOrNull()?.message ?: t("Senkronizasyon hatası")
                if (isManual) {
                    platformBridge.showToastNotification(err)
                }
                Result.failure(fastResult.exceptionOrNull() ?: Exception(err))
            }
        } catch (e: Exception) {
            val msg = e.message ?: t("Senkronizasyon hatası")
            if (isManual) {
                platformBridge.showToastNotification(msg)
            }
            Result.failure(e)
        }
    }

    /**
     * Send a real email via SMTP.
     */
    suspend fun sendRealEmail(
        account: EmailAccountEntity,
        to: String,
        subject: String,
        body: String,
        replyToOriginalSubject: String? = null,
        inReplyToMessageId: String? = null
    ): Result<Boolean> {
        val sendResult = MailService.sendEmail(
            account = account,
            to = to,
            subject = subject,
            body = body,
            replyToOriginalSubject = replyToOriginalSubject,
            inReplyToMessageId = inReplyToMessageId
        )

        return if (sendResult.isSuccess) {
            // Also store sent email locally so the user can see it
            val sentEntity = EmailEntity(
                senderName = account.accountName.ifBlank { "Ben" },
                senderEmail = account.emailAddress,
                subject = subject,
                preview = body.replace("\n", " ").trim().take(120),
                body = body,
                timestamp = System.currentTimeMillis(),
                isRead = true,
                isStarred = false,
                categoryId = SmartCategory.PRIMARY.id,
                labelsCsv = "Gönderilen",
                storageSizeKb = (body.length / 20L).coerceAtLeast(15L),
                hasAttachment = false,
                isMailingList = false,
                trackingPixelBlocked = false,
                accountEmail = account.emailAddress,
                folderName = "[Gmail]/Sent Mail"
            )
            emailDao.insertEmail(sentEntity)

            platformBridge.playSound(SoundType.CLEAN_SUCCESS)
            platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
            platformBridge.showToastNotification(t("E-posta başarıyla gönderildi ✉️"))
            Result.success(true)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: t("Gönderme hatası")
            platformBridge.showToastNotification(t("Gönderilemedi: {0}", err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    suspend fun toggleStar(id: Long) {
        val email = emailDao.getEmailById(id)
        emailDao.toggleStar(id)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)

        if (email != null && email.serverUid > 0L) {
            val account = accountFor(email)
            if (account != null) {
                MailService.updateEmailFlagsOnServer(account, email, isStarred = !email.isStarred)
            }
        }
    }

    suspend fun setRead(id: Long, isRead: Boolean) {
        val email = emailDao.getEmailById(id)
        emailDao.setRead(id, isRead)

        if (email != null && email.serverUid > 0L) {
            val account = accountFor(email)
            if (account != null) {
                MailService.updateEmailFlagsOnServer(account, email, isRead = isRead)
            }
        }
    }

    suspend fun setReadBatch(ids: List<Long>, isRead: Boolean) {
        if (ids.isEmpty()) return
        emailDao.setReadBatch(ids, isRead)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
        for ((account, group) in groupByAccount(emailDao.getEmailsByIds(ids))) {
            if (account == null) continue
            MailService.batchUpdateReadOnServer(account, group.filter { it.serverUid > 0L }, isRead)
        }
    }

    /**
     * Move to trash both locally and on real server (Gmail Papierkorb).
     * Sunucu işlemi başarısız olursa posta yerinde bırakılır.
     */
    suspend fun moveToTrash(id: Long) {
        val email = emailDao.getEmailById(id) ?: return
        pendingServerIds.add(id)
        try {
            emailDao.moveToTrash(id)
            platformBridge.triggerHaptic(HapticType.DELETE_WARNING)

            val account = accountFor(email)
            if (account == null || !hasServerIdentity(email)) {
                platformBridge.showToastNotification(t("E-posta sadece uygulamada Çöp Kutusu'na aktarıldı 🗑️"))
                return
            }

            val serverResult = MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = true, holdFolder = holdFolderFor(account))
            val outcome = serverResult.getOrNull()
            when {
                outcome == null -> {
                    emailDao.restoreFromTrash(id)
                    platformBridge.showToastNotification(t("Çöpe taşınamadı, e-posta yerinde bırakıldı: {0}", serverResult.exceptionOrNull()?.message))
                }
                outcome.found -> {
                    emailDao.updateLocation(id, outcome.targetFolder ?: email.folderName, isTrash = true, isArchived = false)
                    platformBridge.showToastNotification(t("E-posta silindi, Çöp sekmesinden geri yüklenebilir 🗑️"))
                }
                else -> platformBridge.showToastNotification(t("E-posta sunucuda bulunamadı, sadece uygulamada çöpe taşındı"))
            }
        } finally {
            pendingServerIds.remove(id)
        }
    }

    /**
     * Move multiple emails to Trash. Her posta kendi hesabında taşınır;
     * sunucuda taşınamayanlar yerinde bırakılır.
     */
    suspend fun moveToTrashBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        val emails = emailDao.getEmailsByIds(ids)
        pendingServerIds.addAll(ids)
        try {
            emailDao.moveToTrashBatch(ids)
            platformBridge.triggerHaptic(HapticType.DELETE_WARNING)

            var moved = 0
            var failed = 0
            var lastError: String? = null
            for ((account, group) in groupByAccount(emails)) {
                val serverEmails = group.filter { hasServerIdentity(it) }
                if (account == null || serverEmails.isEmpty()) continue

                val result = MailService.batchMoveToTrashOnServer(account, serverEmails, holdFolderFor(account))
                val batch = result.getOrNull()
                if (batch == null) {
                    val restoreIds = serverEmails.map { it.id }
                    restoreIds.forEach { emailDao.restoreFromTrash(it) }
                    failed += restoreIds.size
                    lastError = result.exceptionOrNull()?.message
                    continue
                }
                batch.failedIds.forEach { emailDao.restoreFromTrash(it) }
                failed += batch.failedIds.size
                lastError = batch.error ?: lastError
                for (movedId in batch.movedIds) {
                    emailDao.updateLocation(movedId, batch.trashFolder ?: "", isTrash = true, isArchived = false)
                }
                moved += batch.movedIds.size
            }

            when {
                failed > 0 -> platformBridge.showToastNotification(t("{0} e-posta taşınamadı ve yerinde bırakıldı: {1}", failed, lastError))
                moved > 0 -> platformBridge.showToastNotification(t("{0} e-posta silindi, Çöp sekmesinden geri yüklenebilir 🗑️", moved))
                else -> platformBridge.showToastNotification(t("{0} e-posta uygulamada Çöp Kutusu'na aktarıldı 🗑️", ids.size))
            }
        } finally {
            pendingServerIds.removeAll(ids.toSet())
        }
    }

    /** Taşınan posta sonraki eşitlemeyi beklemeden yeni klasörün listesinde görünür. */
    private suspend fun showInFolder(email: EmailEntity, folderName: String) {
        emailDao.insertFolderLinks(listOf(EmailFolderLink(email.accountEmail, folderName, email.id, 0L)))
    }

    /**
     * Restore an email from Trash back to Inbox, sunucuda da Gelen Kutusu'na taşır.
     */
    suspend fun restoreFromTrash(id: Long) {
        val email = emailDao.getEmailById(id) ?: return
        val moved = moveOnServer(email, MoveTarget.INBOX, failurePrefix = t("Geri yüklenemedi")) ?: return
        emailDao.updateLocation(id, moved, isTrash = false, isArchived = false)
        showInFolder(email, moved)
        forgetDeleted(email)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("E-posta Gelen Kutusu'na geri yüklendi 📥"))
    }

    /**
     * Sunucu taşıması yapar ve postanın yeni klasörünü döndürür. Hata olursa null döner;
     * bu durumda yerel kayıt değiştirilmemelidir.
     */
    private suspend fun moveOnServer(email: EmailEntity, target: MoveTarget, failurePrefix: String): String? {
        val fallbackFolder = if (target == MoveTarget.INBOX) "INBOX" else email.folderName
        val account = accountFor(email)
        if (account == null || !hasServerIdentity(email)) return fallbackFolder

        pendingServerIds.add(email.id)
        try {
            val result = MailService.moveEmailOnServer(account, email, target)
            val outcome = result.getOrNull()
            if (outcome == null) {
                platformBridge.showToastNotification("$failurePrefix: ${result.exceptionOrNull()?.message}")
                return null
            }
            if (!outcome.found) {
                platformBridge.showToastNotification(t("E-posta sunucuda bulunamadı, sadece uygulamada güncellendi"))
            }
            return outcome.targetFolder ?: fallbackFolder
        } finally {
            pendingServerIds.remove(email.id)
        }
    }

    /**
     * Permanently delete an email. Önce sunucudan silinir; sunucu hatasında yerel kayıt korunur.
     */
    suspend fun deletePermanently(id: Long) {
        val email = emailDao.getEmailById(id) ?: return
        val account = accountFor(email)

        if (account != null && hasServerIdentity(email)) {
            val serverResult = MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = false)
            if (serverResult.isFailure) {
                platformBridge.showToastNotification(t("Silinemedi: {0}", serverResult.exceptionOrNull()?.message))
                return
            }
        }

        rememberDeleted(email)
        emailDao.deleteEmailById(id)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
        platformBridge.showToastNotification(t("E-posta kalıcı olarak silindi 🗑️"))
    }

    /**
     * Permanently delete multiple emails, her biri kendi hesabından.
     */
    suspend fun deletePermanentlyBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        val emails = emailDao.getEmailsByIds(ids)
        val deletedIds = mutableListOf<Long>()
        var failed = 0

        for ((account, group) in groupByAccount(emails)) {
            // Çöp kutusundakiler tek bağlantıda silinir; bulunamayanlar aşağıdaki ayrı yoldan denenir
            val inTrash = group.filter { it.isTrash && account != null && hasServerIdentity(it) }
            val batch = if (account != null) MailService.batchDeleteFromTrashOnServer(account, inTrash).getOrNull() else null
            val batchDeleted = batch?.movedIds?.toSet().orEmpty()
            group.filter { it.id in batchDeleted }.forEach {
                rememberDeleted(it)
                deletedIds.add(it.id)
            }

            for (email in group.filter { it.id !in batchDeleted }) {
                val canDelete = if (account != null && hasServerIdentity(email)) {
                    MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = false).isSuccess
                } else {
                    true
                }
                if (canDelete) {
                    rememberDeleted(email)
                    deletedIds.add(email.id)
                } else {
                    failed++
                }
            }
        }

        emailDao.deletePermanentlyBatch(deletedIds)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
        if (failed > 0) {
            platformBridge.showToastNotification(t("{0} e-posta silindi, {1} e-posta silinemedi", deletedIds.size, failed))
        } else {
            platformBridge.showToastNotification(t("{0} e-posta kalıcı olarak silindi 🗑️", deletedIds.size))
        }
    }

    /**
     * Arşivler: posta sunucuda da Gelen Kutusu'ndan arşive taşınır.
     */
    suspend fun archiveEmail(id: Long) {
        val email = emailDao.getEmailById(id) ?: return
        val moved = moveOnServer(email, MoveTarget.ARCHIVE, failurePrefix = t("Arşivlenemedi")) ?: return
        emailDao.updateLocation(id, moved, isTrash = false, isArchived = true)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("E-posta arşivlendi"))
    }

    suspend fun unarchiveEmail(id: Long) {
        val email = emailDao.getEmailById(id) ?: return
        val moved = moveOnServer(email, MoveTarget.INBOX, failurePrefix = t("Arşivden çıkarılamadı")) ?: return
        emailDao.updateLocation(id, moved, isTrash = false, isArchived = false)
        showInFolder(email, moved)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("E-posta Gelen Kutusu'na taşındı 📥"))
    }

    /**
     * Gerçek abonelikten çıkma. Sırayla: RFC 8058 tek tık, mailto isteği, tarayıcı sayfası.
     * Sadece istek gerçekten gönderildiyse true döner.
     */
    suspend fun unsubscribeSender(senderEmail: String, openBrowser: Boolean = true): Boolean {
        val candidates = emailDao.getEmailsBySender(senderEmail).sortedByDescending { it.timestamp }
        val email = candidates.firstOrNull { hasServerIdentity(it) && it.isMailingList }
            ?: candidates.firstOrNull { hasServerIdentity(it) }
            ?: candidates.firstOrNull()
        if (email == null) {
            platformBridge.showToastNotification(t("Bu gönderene ait e-posta bulunamadı"))
            return false
        }

        val account = accountFor(email)
        val options = if (account != null && hasServerIdentity(email)) {
            MailService.fetchUnsubscribeOptions(account, email).getOrNull()
        } else {
            null
        } ?: UnsubscribeParser.parse(null, null, email.unsubscribeUrl)

        return performUnsubscribe(senderEmail, email, account, options, openBrowser)
    }

    private suspend fun performUnsubscribe(
        senderEmail: String,
        email: EmailEntity,
        account: EmailAccountEntity?,
        options: UnsubscribeOptions,
        openBrowser: Boolean
    ): Boolean {
        if (options.oneClick && options.httpsUrl != null) {
            val posted = MailService.postOneClickUnsubscribe(options.httpsUrl).getOrDefault(false)
            if (posted) {
                markUnsubscribed(senderEmail, email)
                platformBridge.showToastNotification(t("{0} aboneliğinden çıkıldı ✅", senderEmail))
                return true
            }
        }

        val mailto = options.mailto
        if (mailto != null && account != null) {
            val sent = MailService.sendEmail(account, mailto.address, mailto.subject, mailto.body)
            if (sent.isSuccess) {
                markUnsubscribed(senderEmail, email)
                platformBridge.showToastNotification(t("{0} için abonelikten çıkma e-postası gönderildi ✅", senderEmail))
                return true
            }
        }

        val browserUrl = options.browserUrl
        if (browserUrl != null) {
            if (!openBrowser) return false
            platformBridge.openUrl(browserUrl)
            platformBridge.showToastNotification(t("Abonelikten çıkmayı açılan sayfada tamamlayın"))
            return false
        }

        platformBridge.showToastNotification(t("Bu gönderen için abonelikten çıkma bağlantısı bulunamadı"))
        return false
    }

    private suspend fun markUnsubscribed(senderEmail: String, email: EmailEntity) {
        emailDao.unsubscribeSenderForAccount(senderEmail, email.accountEmail)
        platformBridge.playSound(SoundType.UNSUBSCRIBE_SUCCESS)
        platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
    }

    /**
     * Postanın eklerini indirir. Hata olursa kullanıcıya bildirir ve boş liste döner.
     */
    suspend fun downloadAttachments(email: EmailEntity): List<SavedAttachment> {
        val account = accountFor(email)
        if (account == null || !hasServerIdentity(email)) {
            platformBridge.showToastNotification(t("Bu e-postanın eki sunucudan indirilemiyor"))
            return emptyList()
        }
        val result = MailService.downloadAttachments(account, email, platformBridge.attachmentDirectory(email.id))
        return result.getOrElse {
            platformBridge.showToastNotification(t("Ek indirilemedi: {0}", it.message))
            emptyList()
        }
    }

    /**
     * Bulk clean: gönderenin postaları her hesapta sunucudaki çöp kutusuna taşınır.
     * Çöp Kutusu'ndan geri yüklenebilirler. Sunucuda taşınamayanlar yerinde kalır.
     */
    suspend fun bulkTrashSender(senderEmail: String, senderName: String): Int {
        val emails = emailDao.getEmailsBySender(senderEmail).filter { !it.isTrash }
        var affected = 0
        var failed = 0
        var serverMoved = 0
        var lastError: String? = null

        for ((account, group) in groupByAccount(emails)) {
            val serverEmails = group.filter { hasServerIdentity(it) }
            val localOnly = group.filterNot { hasServerIdentity(it) && account != null }

            if (localOnly.isNotEmpty()) {
                emailDao.moveToTrashBatch(localOnly.map { it.id })
                affected += localOnly.size
            }
            if (account == null || serverEmails.isEmpty()) continue

            val inboxEmails = serverEmails.filter { it.folderName.isBlank() || it.folderName.equals("INBOX", ignoreCase = true) }
            val otherEmails = serverEmails - inboxEmails.toSet()
            pendingServerIds.addAll(serverEmails.map { it.id })
            try {
                if (inboxEmails.isNotEmpty()) {
                    val result = MailService.bulkTrashSenderOnServer(account, senderEmail, holdFolderFor(account))
                    if (result.isSuccess) {
                        serverMoved += result.getOrDefault(0)
                        emailDao.moveToTrashBatch(inboxEmails.map { it.id })
                        affected += inboxEmails.size
                    } else {
                        failed += inboxEmails.size
                        lastError = result.exceptionOrNull()?.message
                    }
                }
                if (otherEmails.isNotEmpty()) {
                    val result = MailService.batchMoveToTrashOnServer(account, otherEmails, holdFolderFor(account))
                    val batch = result.getOrNull()
                    if (batch != null) {
                        emailDao.moveToTrashBatch(batch.movedIds)
                        serverMoved += batch.movedIds.size
                        affected += batch.movedIds.size
                        failed += batch.failedIds.size
                        lastError = batch.error ?: lastError
                    } else {
                        failed += otherEmails.size
                        lastError = result.exceptionOrNull()?.message
                    }
                }
            } finally {
                pendingServerIds.removeAll(serverEmails.map { it.id }.toSet())
            }
        }

        platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
        when {
            failed > 0 -> platformBridge.showToastNotification(t("{0}: {1} e-posta temizlendi, {2} e-posta yerinde bırakıldı: {3}", senderName, affected, failed, lastError))
            serverMoved > 0 -> {
                platformBridge.playSound(SoundType.CLEAN_SUCCESS)
                platformBridge.showToastNotification(t("{0} için {1} e-posta silindi 🧹", senderName, affected))
            }
            else -> platformBridge.showToastNotification(t("{0} için {1} e-posta temizlendi 🧹", senderName, affected))
        }
        return affected
    }

    /**
     * Sadece aktif hesabın çöp kutusunu boşaltır. Sunucu hatasında yerel kayıtlar korunur.
     */
    suspend fun emptyTrash() {
        val account = accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()
        if (account == null) {
            emailDao.emptyTrash()
            platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
            platformBridge.showToastNotification(t("Çöp kutusu boşaltıldı 🗑️"))
            return
        }

        // Silinecekler etiketindekiler gerçek çöpte değildir, ayrıca silinir
        val held = emailDao.getEmailsByAccount(account.emailAddress)
            .filter { it.isTrash && MailFolderNames.isHold(it.folderName) }
        deletePermanentlyBatch(held.map { it.id })

        val serverResult = MailService.emptyTrashOnServer(account)
        if (serverResult.isFailure) {
            platformBridge.showToastNotification(t("Çöp kutusu boşaltılamadı: {0}", serverResult.exceptionOrNull()?.message))
            return
        }
        emailDao.emptyTrashForAccount(account.emailAddress)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
        platformBridge.showToastNotification(t("{0} çöp kutusu boşaltıldı ({1} e-posta) 🗑️", account.emailAddress, serverResult.getOrDefault(0)))
    }

    private suspend fun runSyncAutomations(account: EmailAccountEntity) {
        try {
            if (applyAutoLabels(account) > 0) completedSyncs.remove(account.emailAddress)
            deleteOldSpam(account)
            trashBlockedSenders(account)
            cleanOldAds(account)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Sınıflandırılan postalara Gmail etiketi ekler. Her eşitlemede en fazla bir parti işlenir. */
    private suspend fun applyAutoLabels(account: EmailAccountEntity): Int {
        if (!autoLabelsEnabled(account)) return 0
        val overrides = emailDao.getSenderOverrides().associate { it.senderEmail to it.folder }
        val targets = emailDao.getEmailsByAccount(account.emailAddress)
            .filter { email ->
                email.autoFolder == null && !email.isTrash && email.serverUid > 0L &&
                    email.categoryId != SmartCategory.SPAM.id &&
                    !email.senderEmail.equals(account.emailAddress, ignoreCase = true) &&
                    email.id !in pendingServerIds
            }
            .mapNotNull { email ->
                MailClassifier.classify(email, overrides[email.senderEmail])?.let { email to it.serverName }
            }
            .take(AUTO_LABEL_BATCH)
        if (targets.isEmpty()) return 0

        val byLabel = targets.groupBy({ it.second }, { it.first })
        val done = MailService.copyToLabels(account, byLabel).getOrNull() ?: return 0
        var labelled = 0
        for ((label, ids) in done) {
            if (ids.isEmpty()) continue
            emailDao.updateAutoFolder(ids, label)
            labelled += ids.size
        }
        return labelled
    }

    /** Ayar açıksa süresi dolan spam postaları kalıcı siler. */
    private suspend fun deleteOldSpam(account: EmailAccountEntity) {
        val days = spamDeleteDays()
        if (days <= 0) return
        val limit = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        val oldSpam = emailDao.getEmailsByAccount(account.emailAddress).filter { email ->
            !email.isTrash && email.timestamp < limit &&
                MailService.categorizeFolder(email.folderName, email.folderName.substringAfterLast('/')).first == MailFolderCategory.SPAM
        }
        val deleted = mutableListOf<Long>()
        for (email in oldSpam.take(AUTO_LABEL_BATCH)) {
            if (MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = false).isSuccess) {
                rememberDeleted(email)
                deleted.add(email.id)
            }
        }
        if (deleted.isNotEmpty()) {
            emailDao.deletePermanentlyBatch(deleted)
            completedSyncs.remove(account.emailAddress)
        }
    }

    /** Engellenen göndericilerden gelen postalar geri alınabilir şekilde silinir. */
    private suspend fun trashBlockedSenders(account: EmailAccountEntity) {
        val blocked = emailDao.getSenderOverrides()
            .filter { it.folder == MailClassifier.BLOCKED }
            .map { it.senderEmail }.toSet()
        if (blocked.isEmpty()) return
        val ids = emailDao.getEmailsByAccount(account.emailAddress)
            .filter { !it.isTrash && it.senderEmail in blocked }
            .map { it.id }
        if (ids.isNotEmpty()) {
            moveToTrashBatch(ids)
            completedSyncs.remove(account.emailAddress)
        }
    }

    /** Ayar açıksa eski reklamlar geri alınabilir şekilde silinir; yıldızlılara dokunulmaz. */
    private suspend fun cleanOldAds(account: EmailAccountEntity) {
        val days = adCleanupDays()
        if (days <= 0) return
        val limit = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        val ids = emailDao.getEmailsByAccount(account.emailAddress)
            .filter { email ->
                email.autoFolder == AutoFolder.ADS.serverName && !email.isTrash && !email.isStarred && email.timestamp < limit
            }
            .take(AUTO_LABEL_BATCH)
            .map { it.id }
        if (ids.isNotEmpty()) {
            moveToTrashBatch(ids)
            completedSyncs.remove(account.emailAddress)
        }
    }

    suspend fun blockSender(senderEmail: String, senderName: String) {
        emailDao.insertSenderOverride(SenderOverride(senderEmail, MailClassifier.BLOCKED))
        bulkTrashSender(senderEmail, senderName)
    }

    suspend fun unblockSender(senderEmail: String) {
        emailDao.deleteSenderOverride(senderEmail)
    }

    /** Birden çok bültenden çıkar; web sayfası gerektirenler açılmaz, sayılır. */
    suspend fun unsubscribeSenders(senderEmails: List<String>): BulkUnsubscribeResult {
        var done = 0
        var needsPage = 0
        for (sender in senderEmails) {
            if (unsubscribeSender(sender, openBrowser = false)) done++ else needsPage++
        }
        platformBridge.showToastNotification(t("{0} abonelikten çıkıldı, {1} için sayfa açmak gerekiyor", done, needsPage))
        return BulkUnsubscribeResult(done, needsPage)
    }

    suspend fun weeklyReport(now: Long = System.currentTimeMillis()): WeeklyReport {
        val since = now - 7L * 24L * 60L * 60L * 1000L
        val week = emailDao.getAllEmailsList().filter { it.timestamp >= since && !it.isTrash }
        return WeeklyReport(
            received = week.size,
            unread = week.count { !it.isRead },
            ads = week.count { it.autoFolder == AutoFolder.ADS.serverName },
            newsletters = week.count { it.autoFolder == AutoFolder.NEWSLETTER.serverName },
            social = week.count { it.autoFolder == AutoFolder.SOCIAL.serverName }
        )
    }

    /** Gönderenin klasörünü değiştirir; eski etiketi kaldırır ve yeni postalar için hatırlar. */
    suspend fun setSenderFolder(senderEmail: String, folder: AutoFolder?) {
        emailDao.insertSenderOverride(SenderOverride(senderEmail, folder?.serverName ?: MailClassifier.NO_FOLDER))
        val emails = emailDao.getEmailsBySender(senderEmail)
        for ((account, group) in groupByAccount(emails)) {
            if (account == null) continue
            for ((label, labelled) in group.filter { it.autoFolder != null && it.autoFolder != folder?.serverName }.groupBy { it.autoFolder!! }) {
                MailService.removeFromLabel(account, label, labelled)
                emailDao.updateAutoFolder(labelled.map { it.id }, null)
            }
            applyAutoLabels(account)
            completedSyncs.remove(account.emailAddress)
        }
        platformBridge.showToastNotification(t("{0} için klasör kaydedildi", senderEmail))
    }

    /** Kuralları kayıtlı tüm postalara uygular; yalnızca değişen kayıtlar yazılır. */
    suspend fun reapplyRules() {
        val rules = emailDao.getEnabledRulesList()
        val changed = emailDao.getAllEmailsList()
            .filter { !it.isTrash && it.id !in pendingServerIds }
            .mapNotNull { email ->
                val updated = SmartFilterEngine.applyRules(SmartFilterEngine.refreshCategory(email), rules)
                updated.takeIf { it != email }
            }
        if (changed.isNotEmpty()) {
            emailDao.insertEmails(changed)
        }
    }

    suspend fun updateRule(rule: RuleEntity) {
        emailDao.insertRule(rule)
        reapplyRules()
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("'{0}' kuralı güncellendi", rule.name))
    }

    suspend fun setAccountPhoto(account: EmailAccountEntity, path: String?) {
        accountDao.updatePhotoPath(account.id, path)
    }

    suspend fun addRule(rule: RuleEntity): Long {
        val id = emailDao.insertRule(rule)
        reapplyRules()
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("'{0}' kuralı eklendi", rule.name))
        return id
    }

    suspend fun toggleRule(id: Long) {
        emailDao.toggleRule(id)
        reapplyRules()
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
    }

    suspend fun deleteRule(id: Long) {
        emailDao.deleteRule(id)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
    }

    suspend fun addCustomLabel(label: CustomLabelEntity) {
        emailDao.insertLabel(label)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("'{0}' etiketi eklendi", label.name))
    }

    suspend fun deleteLabel(id: String) {
        emailDao.deleteLabel(id)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
    }

    suspend fun sendOrReceiveEmail(
        senderName: String,
        senderEmail: String,
        subject: String,
        body: String,
        isMailingList: Boolean,
        labelsCsv: String = ""
    ): Long {
        val category = SmartFilterEngine.detectSmartCategory(senderEmail, subject, body)
        val hasTracker = SmartFilterEngine.hasTrackingPixel(senderEmail, body)
        val now = System.currentTimeMillis()

        val entity = EmailEntity(
            senderName = senderName,
            senderEmail = senderEmail,
            subject = subject,
            preview = if (body.length > 80) body.substring(0, 80) + "..." else body,
            body = body,
            timestamp = now,
            isRead = false,
            isStarred = false,
            categoryId = category.id,
            labelsCsv = labelsCsv,
            storageSizeKb = (body.length / 15L).coerceAtLeast(15L),
            hasAttachment = false,
            isMailingList = isMailingList,
            unsubscribeUrl = if (isMailingList) "https://unsub.demo.org?from=$senderEmail" else null,
            isUnsubscribed = false,
            trackingPixelBlocked = hasTracker
        )

        val id = emailDao.insertEmail(entity)
        platformBridge.playSound(SoundType.NEW_MAIL)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification(t("Yeni e-posta alındı: {0}", subject))
        return id
    }
}
