package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.common.bridge.PlatformBridge
import com.example.common.data.CustomLabelEntity
import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailEntity
import com.example.common.data.EmailFolderLink
import com.example.common.data.RuleEntity
import com.example.common.filter.AutoFolder
import com.example.common.filter.SmartFilterEngine
import com.example.common.i18n.t
import com.example.common.model.CleanSenderGroup
import com.example.common.model.MailFolderCategory
import com.example.common.model.MailFolderInfo
import com.example.common.model.SmartCategory
import com.example.common.repository.EmailRepository
import com.example.common.service.MailService
import com.example.common.settings.AppSettings
import com.example.common.sync.AutoSyncGate
import com.example.ui.platform.saveScaledImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MainNavTab(val titleTr: String) {
    INBOX("Gelen Kutusu"),
    CLEAN_TRIM("Temizlik & Abonelik"),
    SMART_RULES("Akıllı Filtreler"),
    ARCHIVE_TRASH("Çöp & Bilgi")
}

enum class AutoSplitCategory(val titleTr: String, val subtitleTr: String) {
    ALL("Tümü", "Tüm e-postalar"),
    NEW_INCOMING("Yeni Gelenler", "Okunmamış postalar"),
    IMPORTANT("Önemliler", "Yıldızlı ve öncelikli"),
    TO_BE_CLEANED("Silinecekler", "Bülten, tanıtım & spam"),
    OTHER("Diğer", "Genel arşiv")
}

data class EmailFilterState(
    val query: String = "",
    val category: SmartCategory = SmartCategory.ALL,
    val selectedLabel: String? = null,
    val selectedFolder: MailFolderInfo? = null,
    val splitCategory: AutoSplitCategory = AutoSplitCategory.ALL,
    val onlyUnread: Boolean = false,
    val onlyStarred: Boolean = false,
    val onlyWithAttachments: Boolean = false
)

private const val SYNC_WATCHDOG_MS = 120_000L

/** Uygulamaya dönünce Android'in ağ erişimini geri açması için kısa bekleme. */
private const val FOREGROUND_SYNC_DELAY_MS = 1_000L
private const val MAX_SYNC_CONTINUATIONS = 20
private const val AVATAR_SIZE_PX = 256

class EmailManagerViewModel(
    val bridge: PlatformBridge,
    private val repository: EmailRepository,
    private val seedData: suspend () -> Unit,
    private val avatarDir: File
) : ViewModel() {

    // Current Navigation Tab
    private val _currentTab = MutableStateFlow(MainNavTab.INBOX)
    val currentTab: StateFlow<MainNavTab> = _currentTab.asStateFlow()

    // Filter criteria
    private val _filterState = MutableStateFlow(EmailFilterState())
    val filterState: StateFlow<EmailFilterState> = _filterState.asStateFlow()

    // Selected email for detail viewing
    private val _selectedEmail = MutableStateFlow<EmailEntity?>(null)
    val selectedEmail: StateFlow<EmailEntity?> = _selectedEmail.asStateFlow()

    // Reclaimed storage in KB during this session (Trimbox feature)
    private val _reclaimedStorageKb = MutableStateFlow(0L)
    val reclaimedStorageKb: StateFlow<Long> = _reclaimedStorageKb.asStateFlow()

    // Real Email Accounts and Sync State
    val accounts: StateFlow<List<com.example.common.data.EmailAccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<com.example.common.data.EmailAccountEntity?> = repository.accounts.map { list ->
        list.firstOrNull { it.isActive } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Discovered server folders (Inbox, Sent, Trash, Drafts, etc.)
    private val _folders = MutableStateFlow<List<MailFolderInfo>>(emptyList())
    val folders: StateFlow<List<MailFolderInfo>> = _folders.asStateFlow()

    private val _isLoadingFolders = MutableStateFlow(false)
    val isLoadingFolders: StateFlow<Boolean> = _isLoadingFolders.asStateFlow()


    private val autoSyncGate = AutoSyncGate()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _showAccountsDialog = MutableStateFlow(false)
    val showAccountsDialog: StateFlow<Boolean> = _showAccountsDialog.asStateFlow()

    private val _showReplyDialog = MutableStateFlow(false)
    val showReplyDialog: StateFlow<Boolean> = _showReplyDialog.asStateFlow()

    private val _replyTargetEmail = MutableStateFlow<EmailEntity?>(null)
    val replyTargetEmail: StateFlow<EmailEntity?> = _replyTargetEmail.asStateFlow()

    val allEmails: StateFlow<List<EmailEntity>> = repository.activeEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashEmails: StateFlow<List<EmailEntity>> = repository.trashEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedEmails: StateFlow<List<EmailEntity>> = repository.archivedEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Birden fazla ek olduğunda kullanıcıya gösterilen liste
    private val _attachmentChoices = MutableStateFlow<List<com.example.common.service.SavedAttachment>>(emptyList())
    val attachmentChoices: StateFlow<List<com.example.common.service.SavedAttachment>> = _attachmentChoices.asStateFlow()

    private val _isDownloadingAttachment = MutableStateFlow(false)
    val isDownloadingAttachment: StateFlow<Boolean> = _isDownloadingAttachment.asStateFlow()

    val blockedSenders = repository.blockedSenders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<RuleEntity>> = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customLabels: StateFlow<List<CustomLabelEntity>> = repository.customLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cleanSenderGroups: StateFlow<List<CleanSenderGroup>> = repository.cleanSenderGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderLinks: StateFlow<List<EmailFolderLink>> = repository.folderLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined filtered emails with Folder and Auto-Split filtering
    val filteredEmails: StateFlow<List<EmailEntity>> = combine(
        allEmails,
        folderLinks,
        activeAccount,
        _filterState
    ) { emails, links, account, filter ->
        val selected = filter.selectedFolder
        var list = emailsInFolder(emails, links, account, filter)

        // Filter by user's requested auto-split categories
        list = when (filter.splitCategory) {
            AutoSplitCategory.ALL -> list
            AutoSplitCategory.NEW_INCOMING -> list.filter { !it.isRead }
            AutoSplitCategory.IMPORTANT -> list.filter {
                it.isStarred || it.categoryId == SmartCategory.PRIMARY.id || it.categoryId == SmartCategory.WORK.id
            }
            AutoSplitCategory.TO_BE_CLEANED -> list.filter {
                it.isMailingList || it.categoryId == SmartCategory.SUBSCRIPTIONS.id ||
                it.categoryId == SmartCategory.SPAM.id || it.labelsCsv.contains("Bülten", ignoreCase = true)
            }
            AutoSplitCategory.OTHER -> list.filter {
                it.isRead && !it.isStarred && !it.isMailingList &&
                it.categoryId != SmartCategory.SUBSCRIPTIONS.id && it.categoryId != SmartCategory.SPAM.id
            }
        }

        SmartFilterEngine.filterEmails(
            emails = list,
            query = filter.query,
            selectedCategory = filter.category,
            selectedLabel = filter.selectedLabel,
            onlyUnread = filter.onlyUnread,
            onlyStarred = filter.onlyStarred,
            onlyWithAttachments = filter.onlyWithAttachments,
            includeArchived = selected != null && selected.category != MailFolderCategory.INBOX
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Seçili klasördeki postalar; sayaçlar da bu listeden hesaplanır. */
    val currentFolderEmails: StateFlow<List<EmailEntity>> = combine(
        allEmails,
        folderLinks,
        activeAccount,
        _filterState
    ) { emails, links, account, filter ->
        val selected = filter.selectedFolder
        val includeArchived = selected != null && selected.category != MailFolderCategory.INBOX
        emailsInFolder(emails, links, account, filter).filter { !it.isTrash && (includeArchived || !it.isArchived) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sunucudaki klasör üyeliği varsa liste Gmail ile birebir aynı olur
    private fun emailsInFolder(
        emails: List<EmailEntity>,
        links: List<EmailFolderLink>,
        account: EmailAccountEntity?,
        filter: EmailFilterState
    ): List<EmailEntity> {
        val selected = filter.selectedFolder
        val folderEmailIds = when {
            links.isEmpty() -> null
            selected != null && account != null -> links
                .filter { it.accountEmail == account.emailAddress && it.folderName == selected.fullName }
                .map { it.emailId }.toSet()
            selected == null -> links
                .filter { it.folderName.equals("INBOX", ignoreCase = true) }
                .map { it.emailId }.toSet()
            else -> null
        }
        if (folderEmailIds != null) {
            return emails.filter { it.id in folderEmailIds }
        }
        if (selected == null) return emails
        return emails.filter {
            it.folderName.equals(selected.fullName, ignoreCase = true) ||
                (it.folderName.isBlank() && selected.fullName.equals("INBOX", ignoreCase = true)) ||
                it.folderName.contains(selected.displayName, ignoreCase = true)
        }
    }

    init {
        // Ensure initial seed
        viewModelScope.launch {
            // Varsayılanlar bir kez eklenir; kullanıcının sildikleri geri gelmez
            if (!AppSettings.isInitialDataSeeded()) {
                try {
                    seedData()
                    AppSettings.markInitialDataSeeded()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                repository.migrateStoredPasswords()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Auto-fetch server folders and trigger sync ONLY once per active account change (breaks infinite loop!)
        var lastSyncedAccountId: Long? = null
        viewModelScope.launch {
            activeAccount.collect { acc ->
                if (acc != null && acc.id != lastSyncedAccountId) {
                    lastSyncedAccountId = acc.id
                    fetchFolders()
                    // Automatic sync on initial account load or account switch
                    syncAll(isManual = false)
                }
            }
        }

        // Periodic background auto-sync every 5 minutes while app is running
        viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)
                val acc = activeAccount.value
                if (acc != null && !_isSyncing.value && autoSyncGate.isForeground) {
                    syncAll(isManual = false)
                }
            }
        }
    }

    /** Uygulama ekrana geldiğinde çağrılır; gerekiyorsa eşitlemeyi başlatır ve eski hatayı kaldırır. */
    fun onAppForeground() {
        AppSettings.appInForeground = true
        if (autoSyncGate.onForeground(isSyncing = _isSyncing.value)) {
            scheduleForegroundSync()
        }
    }

    /** Uygulama ekrandan çıktığında çağrılır; arka planda otomatik eşitleme denenmez. */
    fun onAppBackground() {
        AppSettings.appInForeground = false
        autoSyncGate.onBackground()
    }

    private fun scheduleForegroundSync() {
        viewModelScope.launch {
            delay(FOREGROUND_SYNC_DELAY_MS)
            if (autoSyncGate.isForeground) {
                syncAll(isManual = false)
            }
        }
    }

    fun setTab(tab: MainNavTab) {
        _currentTab.value = tab
    }

    fun setQuery(q: String) {
        _filterState.value = _filterState.value.copy(query = q)
    }

    fun setCategory(category: SmartCategory) {
        _filterState.value = _filterState.value.copy(category = category)
    }

    fun setSelectedLabel(label: String?) {
        val next = if (_filterState.value.selectedLabel == label) null else label
        _filterState.value = _filterState.value.copy(selectedLabel = next)
    }

    fun toggleOnlyUnread() {
        _filterState.value = _filterState.value.copy(onlyUnread = !_filterState.value.onlyUnread)
    }

    fun setOnlyUnread(value: Boolean) {
        _filterState.value = _filterState.value.copy(onlyUnread = value)
    }

    fun toggleOnlyStarred() {
        _filterState.value = _filterState.value.copy(onlyStarred = !_filterState.value.onlyStarred)
    }

    fun setOnlyStarred(value: Boolean) {
        _filterState.value = _filterState.value.copy(onlyStarred = value)
    }

    fun toggleOnlyWithAttachments() {
        _filterState.value = _filterState.value.copy(onlyWithAttachments = !_filterState.value.onlyWithAttachments)
    }

    fun resetFilters() {
        _filterState.value = EmailFilterState()
    }

    fun selectEmail(email: EmailEntity?) {
        _selectedEmail.value = email
        if (email != null && !email.isRead) {
            setRead(email.id, true)
        }
    }

    fun toggleStar(id: Long) {
        viewModelScope.launch {
            repository.toggleStar(id)
            if (_selectedEmail.value?.id == id) {
                _selectedEmail.value = _selectedEmail.value?.let { it.copy(isStarred = !it.isStarred) }
            }
        }
    }

    fun setRead(id: Long, isRead: Boolean) {
        viewModelScope.launch {
            repository.setRead(id, isRead)
            if (_selectedEmail.value?.id == id) {
                _selectedEmail.value = _selectedEmail.value?.copy(isRead = isRead)
            }
        }
    }

    fun setReadBatch(ids: List<Long>, isRead: Boolean) {
        viewModelScope.launch {
            repository.setReadBatch(ids, isRead)
            if (ids.contains(_selectedEmail.value?.id)) {
                _selectedEmail.value = _selectedEmail.value?.copy(isRead = isRead)
            }
        }
    }

    fun selectFolder(folder: MailFolderInfo?) {
        if (folder == null) {
            _filterState.value = _filterState.value.copy(
                selectedFolder = null,
                category = SmartCategory.ALL,
                onlyStarred = false,
                onlyUnread = false,
                selectedLabel = null
            )
            return
        }

        // Handle special folder categories gracefully
        when (folder.category) {
            MailFolderCategory.TRASH, MailFolderCategory.HOLD -> {
                setTab(MainNavTab.ARCHIVE_TRASH)
                return
            }
            MailFolderCategory.SUBSCRIPTIONS -> {
                setTab(MainNavTab.CLEAN_TRIM)
                return
            }
            else -> {
                setTab(MainNavTab.INBOX)
                _filterState.value = _filterState.value.copy(
                    selectedFolder = folder,
                    category = SmartCategory.ALL,
                    onlyStarred = false,
                    onlyUnread = false,
                    selectedLabel = null
                )
            }
        }

    }

    fun setSplitCategory(split: AutoSplitCategory) {
        _filterState.value = _filterState.value.copy(splitCategory = split)
    }

    fun fetchFolders() {
        val currentAccount = activeAccount.value ?: return
        viewModelScope.launch {
            _isLoadingFolders.value = true
            val result = repository.fetchFolderHierarchy(currentAccount)
            _isLoadingFolders.value = false
            if (result.isSuccess) {
                // Käufe gibi sanal klasörler yalnızca eşitlemede hesaplanır
                val virtual = _folders.value.filter { it.fullName == MailService.GMAIL_PURCHASES_FOLDER }
                _folders.value = result.getOrDefault(emptyList()) + virtual
            }
        }
    }

    fun moveToTrash(id: Long) {
        viewModelScope.launch {
            repository.moveToTrash(id)
            if (_selectedEmail.value?.id == id) {
                _selectedEmail.value = null
            }
        }
    }

    fun moveToTrashBatch(ids: List<Long>) {
        viewModelScope.launch {
            repository.moveToTrashBatch(ids)
            if (ids.contains(_selectedEmail.value?.id)) {
                _selectedEmail.value = null
            }
        }
    }

    fun restoreFromTrash(id: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
            if (_selectedEmail.value?.id == id) {
                _selectedEmail.value = null
            }
        }
    }

    fun deletePermanently(id: Long) {
        viewModelScope.launch {
            repository.deletePermanently(id)
            if (_selectedEmail.value?.id == id) {
                _selectedEmail.value = null
            }
        }
    }

    fun deletePermanentlyBatch(ids: List<Long>) {
        viewModelScope.launch {
            repository.deletePermanentlyBatch(ids)
            if (ids.contains(_selectedEmail.value?.id)) {
                _selectedEmail.value = null
            }
        }
    }

    fun archiveEmail(id: Long) {
        viewModelScope.launch {
            repository.archiveEmail(id)
            if (_selectedEmail.value?.id == id) {
                _selectedEmail.value = null
            }
        }
    }

    fun unarchiveEmail(id: Long) {
        viewModelScope.launch {
            repository.unarchiveEmail(id)
        }
    }

    fun openAttachments(email: EmailEntity) {
        if (_isDownloadingAttachment.value) return
        viewModelScope.launch {
            _isDownloadingAttachment.value = true
            val saved = repository.downloadAttachments(email)
            _isDownloadingAttachment.value = false
            when {
                saved.size == 1 -> bridge.openFile(saved.first().file, saved.first().mimeType)
                saved.size > 1 -> _attachmentChoices.value = saved
            }
        }
    }

    fun openSavedAttachment(attachment: com.example.common.service.SavedAttachment) {
        _attachmentChoices.value = emptyList()
        bridge.openFile(attachment.file, attachment.mimeType)
    }

    fun dismissAttachmentChoices() {
        _attachmentChoices.value = emptyList()
    }

    fun unsubscribeSender(senderEmail: String) {
        viewModelScope.launch {
            val requested = repository.unsubscribeSender(senderEmail)
            if (requested && _selectedEmail.value?.senderEmail == senderEmail) {
                _selectedEmail.value = _selectedEmail.value?.copy(isUnsubscribed = true)
            }
        }
    }

    fun unsubscribeSenders(senderEmails: List<String>) {
        viewModelScope.launch {
            repository.unsubscribeSenders(senderEmails)
        }
    }

    fun blockSender(senderEmail: String, senderName: String) {
        viewModelScope.launch {
            repository.blockSender(senderEmail, senderName)
            if (_selectedEmail.value?.senderEmail == senderEmail) {
                _selectedEmail.value = null
            }
        }
    }

    fun unblockSender(senderEmail: String) {
        viewModelScope.launch {
            repository.unblockSender(senderEmail)
        }
    }

    fun bulkTrashSender(senderEmail: String, senderName: String, sizeKb: Long) {
        viewModelScope.launch {
            val count = repository.bulkTrashSender(senderEmail, senderName)
            if (count > 0) {
                _reclaimedStorageKb.value += sizeKb
            }
            if (_selectedEmail.value?.senderEmail == senderEmail) {
                _selectedEmail.value = null
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun addRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.addRule(rule)
        }
    }

    fun updateRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }

    private fun avatarFile(account: EmailAccountEntity): File {
        avatarDir.mkdirs()
        return File(avatarDir, "account_${account.id}.jpg")
    }

    fun setAccountPhoto(account: EmailAccountEntity, bytes: ByteArray) {
        viewModelScope.launch {
            val file = avatarFile(account)
            val saved = withContext(Dispatchers.IO) { saveScaledImage(bytes, file, AVATAR_SIZE_PX) }
            if (saved) {
                // Aynı dosya adı yeniden yazıldığı için kayıt önce temizlenir ve resim yenilenir
                repository.setAccountPhoto(account, null)
                repository.setAccountPhoto(account, file.absolutePath)
            } else {
                bridge.showToastNotification(t("Resim okunamadı"))
            }
        }
    }

    fun removeAccountPhoto(account: EmailAccountEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { avatarFile(account).delete() }
            repository.setAccountPhoto(account, null)
        }
    }

    /** Gönderenin otomatik klasörünü değiştirir; karar sonraki postalar için de hatırlanır. */
    fun setSenderFolder(senderEmail: String, folder: AutoFolder?) {
        viewModelScope.launch {
            repository.setSenderFolder(senderEmail, folder)
        }
    }

    fun toggleRule(id: Long) {
        viewModelScope.launch {
            repository.toggleRule(id)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun addCustomLabel(label: CustomLabelEntity) {
        viewModelScope.launch {
            repository.addCustomLabel(label)
        }
    }

    fun deleteLabel(id: String) {
        viewModelScope.launch {
            repository.deleteLabel(id)
        }
    }

    fun setShowAccountsDialog(show: Boolean) {
        _showAccountsDialog.value = show
    }

    fun openReplyDialog(email: EmailEntity) {
        _replyTargetEmail.value = email
        _showReplyDialog.value = true
    }

    fun closeReplyDialog() {
        _showReplyDialog.value = false
        _replyTargetEmail.value = null
    }

    fun addOrUpdateAccount(account: com.example.common.data.EmailAccountEntity) {
        viewModelScope.launch {
            val id = repository.addOrUpdateAccount(account)
            // If it's the only account, set it active
            if (accounts.value.size <= 1) {
                repository.setActiveAccount(if (account.id != 0L) account.id else id)
            }
            fetchFolders()
        }
    }

    fun saveAccount(account: com.example.common.data.EmailAccountEntity) {
        addOrUpdateAccount(account)
    }

    fun deleteAccount(account: com.example.common.data.EmailAccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            _folders.value = emptyList()
        }
    }

    fun setActiveAccount(id: Long) {
        viewModelScope.launch {
            repository.setActiveAccount(id)
            fetchFolders()
        }
    }

    fun testAccountConnection(
        account: com.example.common.data.EmailAccountEntity,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.testAccountConnection(account)
            if (res.isSuccess) {
                onResult(true, res.getOrNull() ?: t("Bağlantı başarılı!"))
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: t("Bağlantı hatası!"))
            }
        }
    }

    /**
     * Complete synchronization across all folders (INBOX, Papierkorb, Spam, Käufe).
     * Can be invoked manually via the sync button or automatically by the app.
     */
    fun syncAll(isManual: Boolean = true) {
        val currentAccount = activeAccount.value
        if (currentAccount == null) {
            if (isManual) {
                bridge.showToastNotification(t("Önce bir e-posta hesabı ekleyin"))
                _showAccountsDialog.value = true
            }
            return
        }

        if (_isSyncing.value) return

        viewModelScope.launch {
            val runId = beginSync(t("Senkronize ediliyor..."))
            var succeeded = false
            var continueSync = false
            try {
                val result = repository.syncAllAccountData(currentAccount, isManual = isManual)
                if (runId != syncRunId) return@launch
                succeeded = result.isSuccess
                if (result.isSuccess) {
                    val summary = result.getOrNull()
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                    val prefix = if (summary?.isPartial == true) t("Kısmi eşitleme") else t("Son eşitleme")
                    _syncStatusMessage.value = t("{0}: {1} • Gelen: {2}, Çöp: {3}, Spam: {4}", prefix, time, summary?.inboxCount ?: 0, summary?.trashCount ?: 0, summary?.spamCount ?: 0)
                    if (!summary?.folders.isNullOrEmpty()) {
                        _folders.value = summary.folders
                    } else {
                        fetchFolders()
                    }
                    continueSync = summary?.timedOut == true
                } else {
                    _syncStatusMessage.value = result.exceptionOrNull()?.message ?: t("Senkronizasyon hatası")
                }
            } finally {
                endSync(runId, succeeded)
            }
            // İlk eşitleme süreye sığmadıysa kalan postalar hemen alınır
            if (continueSync && syncContinuations < MAX_SYNC_CONTINUATIONS) {
                syncContinuations++
                scheduleForegroundSync()
            } else if (!continueSync) {
                syncContinuations = 0
            }
        }
    }

    private var syncRunId = 0L
    private var syncContinuations = 0

    /**
     * Eşitlemeyi başlatır ve güvenlik zamanlayıcısı kurar: uygulama Android tarafından
     * dondurulsa veya bağlantı askıda kalsa bile "Senkronize ediliyor" yazısı sonsuza dek kalmaz.
     */
    private fun beginSync(message: String): Long {
        val runId = ++syncRunId
        _isSyncing.value = true
        _syncStatusMessage.value = message
        viewModelScope.launch {
            delay(SYNC_WATCHDOG_MS)
            if (runId == syncRunId && _isSyncing.value) {
                syncRunId++
                _isSyncing.value = false
                _syncStatusMessage.value = t("Eşitleme zaman aşımına uğradı. Tekrar deneyin.")
                autoSyncGate.onSyncFinished(success = false)
            }
        }
        return runId
    }

    private fun endSync(runId: Long, succeeded: Boolean) {
        if (runId == syncRunId) {
            _isSyncing.value = false
            if (autoSyncGate.onSyncFinished(succeeded)) {
                scheduleForegroundSync()
            }
        }
    }

    fun syncActiveAccount(folderFullName: String = "INBOX") {
        val currentAccount = activeAccount.value
        if (currentAccount == null) {
            bridge.showToastNotification(t("Önce bir e-posta hesabı ekleyin"))
            return
        }

        // Tüm klasörler birlikte eşitlenir; tek klasör eşitlemesi postanın ana konumunu bozardı
        if (folderFullName.isNotBlank()) {
            syncAll(isManual = true)
            return
        }

        viewModelScope.launch {
            val runId = beginSync(t("{0} ({1}) senkronize ediliyor...", currentAccount.emailAddress, folderFullName))
            var succeeded = false
            try {
                val result = repository.syncAccountEmails(currentAccount, folderFullName = folderFullName, limit = 60)
                if (runId != syncRunId) return@launch
                succeeded = result.isSuccess
                if (result.isSuccess) {
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                    _syncStatusMessage.value = t("Son güncelleme: {0}", time)
                    fetchFolders()
                } else {
                    _syncStatusMessage.value = result.exceptionOrNull()?.message ?: t("Senkronizasyon hatası")
                }
            } finally {
                endSync(runId, succeeded)
            }
        }
    }

    fun openGoogleAppPasswordsUrl() {
        bridge.openUrl("https://myaccount.google.com/apppasswords")
    }

    fun sendRealEmail(
        to: String,
        subject: String,
        body: String,
        replyToOriginalSubject: String? = null,
        inReplyToMessageId: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val currentAccount = activeAccount.value
        if (currentAccount == null) {
            val msg = t("E-posta göndermek için lütfen önce geçerli bir hesap ekleyin.")
            bridge.showToastNotification(msg)
            _showAccountsDialog.value = true
            onComplete(false, msg)
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            val result = repository.sendRealEmail(
                account = currentAccount,
                to = to,
                subject = subject,
                body = body,
                replyToOriginalSubject = replyToOriginalSubject,
                inReplyToMessageId = inReplyToMessageId
            )
            _isSending.value = false
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun composeOrSimulateEmail(
        senderName: String,
        senderEmail: String,
        subject: String,
        body: String,
        isNewsletter: Boolean,
        labelsCsv: String = ""
    ) {
        val currentAccount = activeAccount.value
        if (currentAccount != null) {
            // Real send if active account is configured
            sendRealEmail(
                to = senderEmail,
                subject = subject,
                body = body,
                replyToOriginalSubject = null
            ) { _, _ -> }
        } else {
            // Local fallback
            viewModelScope.launch {
                repository.sendOrReceiveEmail(
                    senderName = senderName,
                    senderEmail = senderEmail,
                    subject = subject,
                    body = body,
                    isMailingList = isNewsletter,
                    labelsCsv = labelsCsv
                )
            }
        }
    }
}
