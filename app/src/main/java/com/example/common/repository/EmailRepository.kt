package com.example.common.repository

import com.example.common.bridge.HapticType
import com.example.common.bridge.PlatformBridge
import com.example.common.bridge.SoundType
import com.example.common.data.AccountDao
import com.example.common.data.CustomLabelEntity
import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailDao
import com.example.common.data.EmailEntity
import com.example.common.data.RuleEntity
import com.example.common.filter.SmartFilterEngine
import com.example.common.model.CleanSenderGroup
import com.example.common.model.SmartCategory
import com.example.common.service.MailService
import com.example.common.model.MailFolderInfo
import com.example.common.model.MailFolderCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SyncSummary(
    val inboxCount: Int = 0,
    val trashCount: Int = 0,
    val spamCount: Int = 0,
    val purchasesCount: Int = 0,
    val totalFolders: Int = 0
)

class EmailRepository(
    private val emailDao: EmailDao,
    private val accountDao: AccountDao,
    private val platformBridge: PlatformBridge
) {
    val activeEmails: Flow<List<EmailEntity>> = emailDao.getAllActiveEmails()
    val trashEmails: Flow<List<EmailEntity>> = emailDao.getTrashEmails()
    val rules: Flow<List<RuleEntity>> = emailDao.getAllRules()
    val customLabels: Flow<List<CustomLabelEntity>> = emailDao.getAllLabels()
    val accounts: Flow<List<EmailAccountEntity>> = accountDao.getAllAccounts()

    val cleanSenderGroups: Flow<List<CleanSenderGroup>> = activeEmails.map { emails ->
        SmartFilterEngine.generateCleanSenderGroups(emails)
    }

    // In-memory cache of deleted/trashed message identifiers to prevent sync from resurrecting them
    private val deletedIdentifiers = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun getActiveAccount(): EmailAccountEntity? {
        return accountDao.getActiveAccount()
    }

    suspend fun addOrUpdateAccount(account: EmailAccountEntity): Long {
        val id = accountDao.insertAccount(account)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification("${account.emailAddress} hesabı kaydedildi")
        return id
    }

    suspend fun deleteAccount(account: EmailAccountEntity) {
        accountDao.deleteAccount(account)
        try {
            emailDao.deleteEmailsByAccount(account.emailAddress)
        } catch (_: Exception) {}
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
        platformBridge.showToastNotification("${account.emailAddress} hesabı kaldırıldı")
    }

    suspend fun setActiveAccount(id: Long) {
        accountDao.setActiveAccount(id)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
    }

    suspend fun testAccountConnection(account: EmailAccountEntity): Result<String> {
        return MailService.testConnection(account)
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

                val isCurrentFolderTrash = folderFullName.contains("trash", ignoreCase = true) ||
                        folderFullName.contains("çöp", ignoreCase = true) ||
                        folderFullName.contains("papierkorb", ignoreCase = true)

                val mappedEmails = rawEmails.mapNotNull { raw ->
                    // If this email was permanently deleted, skip it entirely
                    val isDeleted = (raw.messageId != null && (deletedIdentifiers.contains(raw.messageId) || platformBridge.isIdentifierDeleted(raw.messageId))) ||
                            (raw.serverUid > 0 && (deletedIdentifiers.contains(raw.serverUid.toString()) || platformBridge.isIdentifierDeleted(raw.serverUid.toString())))
                    if (isDeleted) {
                        return@mapNotNull null
                    }

                    val existing = (raw.messageId?.let { existingByMsgId[it] })
                        ?: existingByUidAndFolder["${raw.serverUid}_${raw.folderName}"]

                    if (existing != null) {
                        // If user moved it to Trash locally, don't resurrect into INBOX unless synced folder is Trash
                        val keepAsTrash = existing.isTrash && !isCurrentFolderTrash
                        raw.copy(
                            id = existing.id,
                            isTrash = if (keepAsTrash) true else raw.isTrash
                        )
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
            val err = fetchResult.exceptionOrNull()?.message ?: "Senkronizasyon hatası"
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
            val fastResult = MailService.syncAllFoldersFast(account, existingEmails)

            if (fastResult.isSuccess) {
                val syncData = fastResult.getOrThrow()
                val rawEmails = syncData.emails

                if (rawEmails.isNotEmpty()) {
                    val existingByMsgId = existingEmails.filter { !it.messageId.isNullOrBlank() }.associateBy { it.messageId }
                    val existingByUidAndFolder = existingEmails.filter { it.serverUid > 0 }.associateBy { "${it.serverUid}_${it.folderName}" }

                    val mappedEmails = rawEmails.mapNotNull { raw ->
                        // If this email was permanently deleted, skip it entirely
                        val isDeleted = (raw.messageId != null && (deletedIdentifiers.contains(raw.messageId) || platformBridge.isIdentifierDeleted(raw.messageId))) ||
                                (raw.serverUid > 0 && (deletedIdentifiers.contains(raw.serverUid.toString()) || platformBridge.isIdentifierDeleted(raw.serverUid.toString())))
                        if (isDeleted) {
                            return@mapNotNull null
                        }

                        val existing = (raw.messageId?.let { existingByMsgId[it] })
                            ?: existingByUidAndFolder["${raw.serverUid}_${raw.folderName}"]

                        val isRawTrash = raw.folderName.contains("trash", ignoreCase = true) ||
                                raw.folderName.contains("çöp", ignoreCase = true) ||
                                raw.folderName.contains("papierkorb", ignoreCase = true) ||
                                raw.isTrash

                        if (existing != null) {
                            val keepAsTrash = existing.isTrash && !isRawTrash
                            raw.copy(
                                id = existing.id,
                                isTrash = if (keepAsTrash) true else raw.isTrash
                            )
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
                }

                accountDao.updateLastSyncTime(account.id, System.currentTimeMillis())
                if (isManual) {
                    platformBridge.playSound(SoundType.NEW_MAIL)
                    platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
                    platformBridge.showToastNotification("Gmail Hızlı Eşitlendi: ${syncData.inboxCount} Gelen, ${syncData.trashCount} Çöp, ${syncData.spamCount} Spam ✅")
                }

                Result.success(
                    SyncSummary(
                        inboxCount = syncData.inboxCount,
                        trashCount = syncData.trashCount,
                        spamCount = syncData.spamCount,
                        purchasesCount = syncData.purchasesCount,
                        totalFolders = syncData.folders.size
                    )
                )
            } else {
                val err = fastResult.exceptionOrNull()?.message ?: "Senkronizasyon hatası"
                if (isManual) {
                    platformBridge.showToastNotification(err)
                }
                Result.failure(fastResult.exceptionOrNull() ?: Exception(err))
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Senkronizasyon hatası"
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
        replyToOriginalSubject: String? = null
    ): Result<Boolean> {
        val sendResult = MailService.sendEmail(
            account = account,
            to = to,
            subject = subject,
            body = body,
            replyToOriginalSubject = replyToOriginalSubject
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
            platformBridge.showToastNotification("E-posta başarıyla gönderildi ✉️")
            Result.success(true)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Gönderme hatası"
            platformBridge.showToastNotification("Gönderilemedi: $err")
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    suspend fun toggleStar(id: Long) {
        val email = emailDao.getEmailById(id)
        emailDao.toggleStar(id)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)

        if (email != null && email.serverUid > 0L) {
            val account = accountDao.getActiveAccount()
            if (account != null) {
                MailService.updateEmailFlagsOnServer(account, email, isStarred = !email.isStarred)
            }
        }
    }

    suspend fun setRead(id: Long, isRead: Boolean) {
        val email = emailDao.getEmailById(id)
        emailDao.setRead(id, isRead)

        if (email != null && email.serverUid > 0L) {
            val account = accountDao.getActiveAccount()
            if (account != null) {
                MailService.updateEmailFlagsOnServer(account, email, isRead = isRead)
            }
        }
    }

    suspend fun setReadBatch(ids: List<Long>, isRead: Boolean) {
        if (ids.isEmpty()) return
        emailDao.setReadBatch(ids, isRead)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
        val account = accountDao.getActiveAccount()
        if (account != null) {
            val emails = emailDao.getEmailsByIds(ids)
            for (email in emails) {
                if (email.serverUid > 0L) {
                    MailService.updateEmailFlagsOnServer(account, email, isRead = isRead)
                }
            }
        }
    }

    /**
     * Move to trash both locally and on real server (Gmail Papierkorb).
     */
    suspend fun moveToTrash(id: Long) {
        val email = emailDao.getEmailById(id)
        emailDao.moveToTrash(id)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)

        if (email != null) {
            val account = (if (email.accountEmail.isNotBlank()) {
                accountDao.getAllAccountsList().firstOrNull { it.emailAddress.equals(email.accountEmail, ignoreCase = true) }
            } else null) ?: accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()

            if (account != null && (email.serverUid > 0L || !email.messageId.isNullOrBlank())) {
                val serverResult = MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = true)
                if (serverResult.isSuccess) {
                    platformBridge.showToastNotification("E-posta gerçek Gmail Çöp Kutusu'na (Papierkorb) aktarıldı 🗑️")
                    return
                }
            }
        }
        platformBridge.showToastNotification("E-posta Çöp Kutusu'na aktarıldı 🗑️")
    }

    /**
     * Move multiple emails to Trash both locally and on real Gmail server (Papierkorb).
     */
    suspend fun moveToTrashBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        val emails = emailDao.getEmailsByIds(ids)
        emailDao.moveToTrashBatch(ids)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)

        val account = accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()
        var serverMoved = 0
        if (account != null && emails.isNotEmpty()) {
            val serverResult = MailService.batchMoveToTrashOnServer(account, emails)
            if (serverResult.isSuccess) {
                serverMoved = serverResult.getOrDefault(0)
            }
        }

        if (serverMoved > 0) {
            platformBridge.showToastNotification("${emails.size} e-posta gerçek Gmail Çöp Kutusu'na (Papierkorb) aktarıldı 🗑️")
        } else {
            platformBridge.showToastNotification("${ids.size} e-posta Çöp Kutusu'na aktarıldı 🗑️")
        }
    }

    /**
     * Restore an email from Trash back to Inbox.
     */
    suspend fun restoreFromTrash(id: Long) {
        emailDao.restoreFromTrash(id)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification("E-posta Gelen Kutusu'na geri yüklendi 📥")
    }

    /**
     * Permanently delete an email from local DB and from the real Gmail server.
     */
    suspend fun deletePermanently(id: Long) {
        val email = emailDao.getEmailById(id)
        if (email != null) {
            email.messageId?.let {
                deletedIdentifiers.add(it)
                platformBridge.addDeletedIdentifier(it)
            }
            if (email.serverUid > 0) {
                val uidStr = email.serverUid.toString()
                deletedIdentifiers.add(uidStr)
                platformBridge.addDeletedIdentifier(uidStr)
            }
        }
        emailDao.deleteEmailById(id)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)

        if (email != null) {
            val account = (if (email.accountEmail.isNotBlank()) {
                accountDao.getAllAccountsList().firstOrNull { it.emailAddress.equals(email.accountEmail, ignoreCase = true) }
            } else null) ?: accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()

            if (account != null && (email.serverUid > 0L || !email.messageId.isNullOrBlank())) {
                val serverResult = MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = false)
                if (serverResult.isSuccess) {
                    platformBridge.showToastNotification("Gerçek Gmail hesabınızdan kalıcı olarak silindi 🗑️")
                    return
                }
            }
        }
        platformBridge.showToastNotification("E-posta kalıcı olarak silindi")
    }

    /**
     * Permanently delete multiple emails from local DB and from the real Gmail server.
     */
    suspend fun deletePermanentlyBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        val emails = emailDao.getEmailsByIds(ids)
        for (e in emails) {
            e.messageId?.let {
                deletedIdentifiers.add(it)
                platformBridge.addDeletedIdentifier(it)
            }
            if (e.serverUid > 0) {
                val uidStr = e.serverUid.toString()
                deletedIdentifiers.add(uidStr)
                platformBridge.addDeletedIdentifier(uidStr)
            }
        }
        emailDao.deletePermanentlyBatch(ids)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)

        val account = accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()
        if (account != null && emails.isNotEmpty()) {
            for (email in emails) {
                MailService.deleteOrTrashEmailOnServer(account, email, moveToTrash = false)
            }
        }
        platformBridge.showToastNotification("${ids.size} e-posta gerçek Gmail hesabınızdan kalıcı olarak silindi 🗑️")
    }

    suspend fun archiveEmail(id: Long) {
        emailDao.archiveEmail(id)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification("E-posta arşivlendi")
    }

    suspend fun unsubscribeSender(senderEmail: String) {
        emailDao.unsubscribeSender(senderEmail)
        platformBridge.playSound(SoundType.UNSUBSCRIBE_SUCCESS)
        platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
        platformBridge.showToastNotification("$senderEmail aboneliğinden çıkıldı ✅")
    }

    /**
     * Bulk clean / delete all emails from a sender both locally and on real Gmail server!
     */
    suspend fun bulkTrashSender(senderEmail: String, senderName: String): Int {
        val emails = emailDao.getEmailsBySender(senderEmail)
        for (e in emails) {
            e.messageId?.let {
                deletedIdentifiers.add(it)
                platformBridge.addDeletedIdentifier(it)
            }
            if (e.serverUid > 0) {
                val uidStr = e.serverUid.toString()
                deletedIdentifiers.add(uidStr)
                platformBridge.addDeletedIdentifier(uidStr)
            }
        }
        val affected = emailDao.bulkTrashSender(senderEmail)
        val account = accountDao.getActiveAccount() ?: accountDao.getAllAccountsList().firstOrNull()

        var serverDeleted = 0
        if (account != null) {
            val serverResult = MailService.bulkTrashSenderOnServer(account, senderEmail)
            if (serverResult.isSuccess) {
                serverDeleted = serverResult.getOrDefault(0)
            }
        }

        platformBridge.playSound(SoundType.CLEAN_SUCCESS)
        platformBridge.triggerHaptic(HapticType.SUCCESS_DOUBLE)
        if (serverDeleted > 0) {
            platformBridge.showToastNotification("$senderName için $affected e-posta Gmail sunucusundan ve uygulamadan temizlendi 🧹")
        } else {
            platformBridge.showToastNotification("$senderName için $affected e-posta temizlendi 🧹")
        }
        return affected
    }

    /**
     * Empties trash both locally and on real Gmail server!
     */
    suspend fun emptyTrash() {
        val account = accountDao.getActiveAccount()
        var serverPurged = 0
        if (account != null) {
            val serverResult = MailService.emptyTrashOnServer(account)
            if (serverResult.isSuccess) {
                serverPurged = serverResult.getOrDefault(0)
            }
        }
        emailDao.emptyTrash()

        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
        if (serverPurged > 0) {
            platformBridge.showToastNotification("Gerçek Gmail Çöp Kutusu ($serverPurged e-posta) ve uygulama çöp kutusu boşaltıldı 🗑️")
        } else {
            platformBridge.showToastNotification("Çöp kutusu tamamen boşaltıldı 🗑️")
        }
    }

    suspend fun addRule(rule: RuleEntity): Long {
        val id = emailDao.insertRule(rule)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification("'${rule.name}' kuralı eklendi")
        return id
    }

    suspend fun toggleRule(id: Long) {
        emailDao.toggleRule(id)
        platformBridge.triggerHaptic(HapticType.LIGHT_TICK)
    }

    suspend fun deleteRule(id: Long) {
        emailDao.deleteRule(id)
        platformBridge.triggerHaptic(HapticType.DELETE_WARNING)
    }

    suspend fun addCustomLabel(label: CustomLabelEntity) {
        emailDao.insertLabel(label)
        platformBridge.triggerHaptic(HapticType.ACTION_CONFIRM)
        platformBridge.showToastNotification("'${label.name}' etiketi eklendi")
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
        platformBridge.showToastNotification("Yeni e-posta alındı: $subject")
        return id
    }
}
