package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.common.bridge.PlatformBridge
import com.example.common.data.CustomLabelEntity
import com.example.common.data.EmailDatabase
import com.example.common.data.EmailEntity
import com.example.common.data.RuleEntity
import com.example.common.filter.SmartFilterEngine
import com.example.common.model.CleanSenderGroup
import com.example.common.model.MailFolderCategory
import com.example.common.model.MailFolderInfo
import com.example.common.model.SmartCategory
import com.example.common.repository.EmailRepository
import com.example.platform.AndroidPlatformBridge
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

class EmailManagerViewModel(application: Application) : AndroidViewModel(application) {

    val bridge: PlatformBridge = AndroidPlatformBridge(application)
    private val database = EmailDatabase.getInstance(application)
    private val repository = EmailRepository(database.emailDao(), database.accountDao(), bridge)

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

    val rules: StateFlow<List<RuleEntity>> = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customLabels: StateFlow<List<CustomLabelEntity>> = repository.customLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cleanSenderGroups: StateFlow<List<CleanSenderGroup>> = repository.cleanSenderGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined filtered emails with Folder and Auto-Split filtering
    val filteredEmails: StateFlow<List<EmailEntity>> = combine(
        allEmails,
        _filterState
    ) { emails, filter ->
        var list = emails

        // Filter by selected folder if specified
        if (filter.selectedFolder != null) {
            val folderKey = filter.selectedFolder.fullName
            val folderDisp = filter.selectedFolder.displayName
            list = list.filter {
                it.folderName.equals(folderKey, ignoreCase = true) ||
                (it.folderName.isBlank() && folderKey.equals("INBOX", ignoreCase = true)) ||
                it.folderName.contains(folderDisp, ignoreCase = true)
            }
        }

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
            onlyWithAttachments = filter.onlyWithAttachments
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Ensure initial seed
        viewModelScope.launch {
            try {
                database.seedInitialData()
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
                if (acc != null && !_isSyncing.value) {
                    syncAll(isManual = false)
                }
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
            MailFolderCategory.TRASH -> {
                setTab(MainNavTab.ARCHIVE_TRASH)
                return
            }
            MailFolderCategory.STARRED -> {
                setTab(MainNavTab.INBOX)
                _filterState.value = _filterState.value.copy(
                    selectedFolder = null,
                    category = SmartCategory.ALL,
                    onlyStarred = true,
                    onlyUnread = false,
                    selectedLabel = null
                )
                return
            }
            MailFolderCategory.SPAM -> {
                setTab(MainNavTab.INBOX)
                _filterState.value = _filterState.value.copy(
                    selectedFolder = null,
                    category = SmartCategory.SPAM,
                    onlyStarred = false,
                    onlyUnread = false,
                    selectedLabel = null
                )
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

        // Silent non-blocking background fetch if account is already connected
        val acc = activeAccount.value
        if (acc != null && !_isSyncing.value) {
            viewModelScope.launch {
                try {
                    repository.syncAccountEmails(acc, folderFullName = folder.fullName, limit = 50)
                } catch (e: Exception) {
                    // Prevent any crash from server folder reading
                    e.printStackTrace()
                }
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
                _folders.value = result.getOrDefault(emptyList())
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

    fun unsubscribeSender(senderEmail: String) {
        viewModelScope.launch {
            repository.unsubscribeSender(senderEmail)
            if (_selectedEmail.value?.senderEmail == senderEmail) {
                _selectedEmail.value = _selectedEmail.value?.copy(isUnsubscribed = true)
            }
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
                onResult(true, res.getOrNull() ?: "Bağlantı başarılı!")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Bağlantı hatası!")
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
                bridge.showToastNotification("Önce bir e-posta hesabı ekleyin")
                _showAccountsDialog.value = true
            }
            return
        }

        if (_isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Senkronize ediliyor..."
            val result = repository.syncAllAccountData(currentAccount, isManual = isManual)
            _isSyncing.value = false
            if (result.isSuccess) {
                val summary = result.getOrNull()
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                _syncStatusMessage.value = "Son eşitleme: $time • Gelen: ${summary?.inboxCount ?: 0}, Çöp: ${summary?.trashCount ?: 0}, Spam: ${summary?.spamCount ?: 0}"
                fetchFolders()
            } else {
                _syncStatusMessage.value = result.exceptionOrNull()?.message ?: "Senkronizasyon hatası"
            }
        }
    }

    fun syncActiveAccount(folderFullName: String = "INBOX") {
        val currentAccount = activeAccount.value
        if (currentAccount == null) {
            bridge.showToastNotification("Önce bir e-posta hesabı ekleyin")
            return
        }

        if (folderFullName.equals("INBOX", ignoreCase = true)) {
            syncAll(isManual = true)
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "${currentAccount.emailAddress} ($folderFullName) senkronize ediliyor..."
            val result = repository.syncAccountEmails(currentAccount, folderFullName = folderFullName, limit = 60)
            _isSyncing.value = false
            if (result.isSuccess) {
                _syncStatusMessage.value = "Son güncelleme: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                fetchFolders()
            } else {
                _syncStatusMessage.value = result.exceptionOrNull()?.message ?: "Senkronizasyon hatası"
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
        onComplete: (Boolean, String?) -> Unit
    ) {
        val currentAccount = activeAccount.value
        if (currentAccount == null) {
            val msg = "E-posta göndermek için lütfen önce geçerli bir hesap ekleyin."
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
                replyToOriginalSubject = replyToOriginalSubject
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
