package com.example.ui.screens

import com.example.common.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.common.data.EmailEntity
import com.example.common.model.SmartCategory
import com.example.ui.components.EmailListItem
import com.example.ui.components.AccountAvatar
import com.example.ui.viewmodel.EmailManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: EmailManagerViewModel,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {}
) {
    val filteredEmails by viewModel.filteredEmails.collectAsStateWithLifecycle()
    val allEmails by viewModel.allEmails.collectAsStateWithLifecycle()
    val currentFolderEmails by viewModel.currentFolderEmails.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val selectedEmail by viewModel.selectedEmail.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()

    var showComposeDialog by remember { mutableStateOf(false) }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // Confirmation dialog states
    var emailToTrash by remember { mutableStateOf<EmailEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek Gmail-style Top Bar & Search Input
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("inbox_drawer_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = t("Gmail Menüsü"),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = filterState.query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = {
                            Text(
                                text = t("Postalarda ara..."),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input")
                    )

                    if (filterState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = t("Aramayı Temizle"))
                        }
                    }

                    IconButton(
                        onClick = { viewModel.syncAll(isManual = true) },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("inbox_manual_sync_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = t("Manuel Senkronize Et"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenAppInfo,
                        modifier = Modifier.testTag("inbox_info_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = t("Bilgi"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Account Avatar or Login Button
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.setShowAccountsDialog(true) }
                            .testTag("inbox_account_avatar_btn"),
                        color = if (activeAccount != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                val account = activeAccount
                                if (account != null) {
                                    AccountAvatar(account, 34.dp, showBadge = false)
                                } else {
                                    Text(
                                        text = "?",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            // Real-time Sync Status & Manual Sync Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = syncStatusMessage ?: t("Senkronize ediliyor..."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = syncStatusMessage ?: if (activeAccount == null) t("Henüz hesap eklenmedi") else t("Gmail tam senkronize"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = { viewModel.syncAll(isManual = true) },
                        enabled = !isSyncing,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.testTag("inbox_banner_sync_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSyncing) t("Eşitleniyor") else t("Eşitle"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Google App Password warning alert if credentials fail
            if (syncStatusMessage?.contains("Uygulama Şifresi", ignoreCase = true) == true ||
                syncStatusMessage?.contains("Application-specific", ignoreCase = true) == true ||
                syncStatusMessage?.contains("534", ignoreCase = true) == true ||
                syncStatusMessage?.contains("535", ignoreCase = true) == true) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { viewModel.setShowAccountsDialog(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = t("Gmail Girişi: 16 Haneli Şifre Gerekli"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = t("Google normal şifrenizi engelledi. 'Uygulama Şifresi' girmek için buraya dokunun."),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Smart Filter Chips (All, Unread, Starred, Attachments, Newsletters, Purchases)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !filterState.onlyUnread && !filterState.onlyStarred && !filterState.onlyWithAttachments && filterState.selectedLabel == null && filterState.category == SmartCategory.ALL,
                    onClick = { viewModel.resetFilters() },
                    label = { Text(t("Tümü"), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_all")
                )

                FilterChip(
                    selected = filterState.onlyUnread,
                    onClick = { viewModel.toggleOnlyUnread() },
                    label = {
                        val unreadCount = currentFolderEmails.count { !it.isRead }
                        Text(if (unreadCount > 0) t("Okunmamış ({0})", unreadCount) else t("Okunmamış"), fontSize = 12.sp)
                    },
                    leadingIcon = { Icon(Icons.Default.MarkEmailUnread, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_unread")
                )

                FilterChip(
                    selected = filterState.onlyStarred,
                    onClick = { viewModel.toggleOnlyStarred() },
                    label = {
                        val starCount = currentFolderEmails.count { it.isStarred }
                        Text(if (starCount > 0) t("Yıldızlı ({0})", starCount) else t("Yıldızlı"), fontSize = 12.sp)
                    },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_starred")
                )

                FilterChip(
                    selected = filterState.onlyWithAttachments,
                    onClick = { viewModel.toggleOnlyWithAttachments() },
                    label = { Text(t("Ekli Dosyalar"), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_attachments")
                )

                FilterChip(
                    selected = filterState.selectedLabel == "Bülten" || filterState.category == SmartCategory.SUBSCRIPTIONS,
                    onClick = {
                        if (filterState.selectedLabel == "Bülten") {
                            viewModel.setSelectedLabel(null)
                        } else {
                            viewModel.setSelectedLabel("Bülten")
                        }
                    },
                    label = { Text(t("Bültenler"), fontSize = 12.sp) },
                    modifier = Modifier.testTag("filter_chip_newsletters")
                )

                FilterChip(
                    selected = filterState.category == SmartCategory.BILLS || filterState.selectedLabel == "Käufe",
                    onClick = {
                        if (filterState.category == SmartCategory.BILLS) {
                            viewModel.setCategory(SmartCategory.ALL)
                        } else {
                            viewModel.setCategory(SmartCategory.BILLS)
                        }
                    },
                    label = { Text(t("Käufe / Alışveriş"), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_purchases")
                )
            }

            // Folder Header & Multi-Selection Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                if (isSelectionMode) {
                    // Multi-selection Active Toolbar with Rich Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedIds.clear()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = t("Vazgeç"))
                            }
                            Text(
                                text = t("{0} seçildi", selectedIds.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = {
                                if (selectedIds.size == filteredEmails.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(filteredEmails.map { it.id })
                                }
                            }) {
                                Text(if (selectedIds.size == filteredEmails.size) t("Kaldır") else t("Tümü"), fontSize = 11.sp)
                            }

                            if (selectedIds.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.setReadBatch(selectedIds.toList(), true)
                                        isSelectionMode = false
                                        selectedIds.clear()
                                    },
                                    modifier = Modifier.testTag("batch_mark_read_btn")
                                ) {
                                    Icon(Icons.Default.MarkEmailRead, contentDescription = t("Okundu Yap"), modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.setReadBatch(selectedIds.toList(), false)
                                        isSelectionMode = false
                                        selectedIds.clear()
                                    },
                                    modifier = Modifier.testTag("batch_mark_unread_btn")
                                ) {
                                    Icon(Icons.Default.MarkEmailUnread, contentDescription = t("Okunmadı Yap"), modifier = Modifier.size(18.dp))
                                }

                                Button(
                                    onClick = { showBatchDeleteConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("batch_delete_btn")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(t("Sil ({0})", selectedIds.size), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                } else {
                    // Normal Folder Status Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val folderName = filterState.selectedFolder?.let { folder -> if (folder.category == com.example.common.model.MailFolderCategory.CUSTOM) folder.displayName else t(folder.category.displayNameTr) } ?: t("Gelen Kutusu (Posteingang)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (filterState.selectedFolder != null) Icons.Default.Folder else Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = t("• {0} e-posta", filteredEmails.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filterState.selectedFolder != null || filterState.onlyStarred || filterState.query.isNotEmpty()) {
                                TextButton(onClick = { viewModel.resetFilters() }) {
                                    Text(t("Sıfırla"), fontSize = 11.sp)
                                }
                            }

                            if (filteredEmails.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { isSelectionMode = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("toggle_selection_mode_btn")
                                ) {
                                    Text(t("Seç"), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Email List or Clean Empty State
            if (filteredEmails.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (activeAccount == null) t("Henüz E-posta Hesabı Eklenmedi") else t("Bu Klasörde E-posta Yok"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (activeAccount == null)
                                t("Demo postalar kaldırıldı. Gerçek Gmail hesabınızı bağlayarak e-postalarınızı hemen eşitleyebilirsiniz.")
                            else
                                t("Yeni e-postaları sunucudan çekmek için 'Senkronize Et' butonuna dokunun."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (activeAccount == null) {
                            Button(
                                onClick = { viewModel.setShowAccountsDialog(true) },
                                modifier = Modifier.testTag("empty_state_add_account_btn")
                            ) {
                                Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(t("Gmail / E-posta Hesabı Ekle"), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.syncActiveAccount(filterState.selectedFolder?.fullName ?: "INBOX") },
                                enabled = !isSyncing,
                                modifier = Modifier.testTag("empty_state_sync_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(t("Şimdi Senkronize Et"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredEmails, key = { it.id }) { email ->
                        if (isSelectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedIds.contains(email.id)) {
                                            selectedIds.remove(email.id)
                                        } else {
                                            selectedIds.add(email.id)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedIds.contains(email.id),
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(email.id) else selectedIds.remove(email.id)
                                    }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    EmailListItem(
                                        email = email,
                                        isSelected = selectedIds.contains(email.id),
                                        onClick = {
                                            if (selectedIds.contains(email.id)) selectedIds.remove(email.id) else selectedIds.add(email.id)
                                        },
                                        onToggleStar = { viewModel.toggleStar(email.id) },
                                        onUnsubscribe = { viewModel.unsubscribeSender(email.senderEmail) },
                                        onDelete = { emailToTrash = email },
                                        onArchive = { viewModel.archiveEmail(email.id) }
                                    )
                                }
                            }
                        } else {
                            EmailListItem(
                                email = email,
                                isSelected = selectedEmail?.id == email.id,
                                onClick = { viewModel.selectEmail(email) },
                                onToggleStar = { viewModel.toggleStar(email.id) },
                                onUnsubscribe = { viewModel.unsubscribeSender(email.senderEmail) },
                                onDelete = { emailToTrash = email },
                                onArchive = { viewModel.archiveEmail(email.id) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button to compose real email
        FloatingActionButton(
            onClick = { showComposeDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("compose_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = t("Yeni Posta"))
                Spacer(modifier = Modifier.width(6.dp))
                Text(t("E-posta Yaz"), fontWeight = FontWeight.Bold)
            }
        }
    }

    // Confirmation Dialog for Single Email Deletion (Moved to Real Gmail Trash / Papierkorb)
    emailToTrash?.let { email ->
        AlertDialog(
            onDismissRequest = { emailToTrash = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(t("Çöp Kutusuna Taşı"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = t("\"{0}\" başlıklı e-posta silinecek.", email.subject),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = t("Çöp sekmesinden geri yükleyebilirsiniz. Devam etmek istiyor musunuz?"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.moveToTrash(email.id)
                        emailToTrash = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_single_move_to_trash")
                ) {
                    Text(t("Çöp Kutusuna Aktar"))
                }
            },
            dismissButton = {
                TextButton(onClick = { emailToTrash = null }) { Text(t("İptal")) }
            }
        )
    }

    // Confirmation Dialog for Batch Email Deletion (Moved to Real Gmail Trash / Papierkorb)
    if (showBatchDeleteConfirm && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(t("Seçilen E-postaları Çöpe At"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = t("Seçilen {0} adet e-posta silinecek.", selectedIds.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = t("Çöp sekmesinden geri yükleyebilirsiniz. Devam etmek istiyor musunuz?"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToTrash = selectedIds.toList()
                        viewModel.moveToTrashBatch(idsToTrash)
                        selectedIds.clear()
                        isSelectionMode = false
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_batch_move_to_trash")
                ) {
                    Text(t("Çöp Kutusuna Aktar ({0})", selectedIds.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(t("İptal")) }
            }
        )
    }

    // Compose / Send Email Dialog
    if (showComposeDialog) {
        ComposeEmailDialog(
            activeAccount = activeAccount,
            onDismiss = { showComposeDialog = false },
            onOpenAccounts = { viewModel.setShowAccountsDialog(true) },
            onSendReal = { to, subject, body, onComplete ->
                viewModel.sendRealEmail(to, subject, body, null, null, onComplete)
            },
            onSaveSimulated = { name, email, subject, body, isNewsletter, labels ->
                viewModel.composeOrSimulateEmail(name, email, subject, body, isNewsletter, labels)
                showComposeDialog = false
            }
        )
    }
}

@Composable
fun ComposeEmailDialog(
    activeAccount: com.example.common.data.EmailAccountEntity?,
    onDismiss: () -> Unit,
    onOpenAccounts: () -> Unit = {},
    onSendReal: (to: String, subject: String, body: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onSaveSimulated: (name: String, email: String, subject: String, body: String, isNewsletter: Boolean, labels: String) -> Unit
) {
    var toEmail by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = t("Yeni E-posta Gönder (SMTP)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeAccount != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = t("Gönderen: {0}", activeAccount.emailAddress),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = toEmail,
                    onValueChange = { toEmail = it },
                    label = { Text(t("Kime (Alıcı E-posta)")) },
                    placeholder = { Text("ornek@alanadi.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("compose_to_input")
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(t("Konu")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("compose_subject_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(t("İçerik")) },
                    placeholder = { Text(t("E-posta metnini buraya yazın...")) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().testTag("compose_body_input")
                )

                errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = t("Hata: {0}", err),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            if (err.contains("Uygulama Şifresi", ignoreCase = true) ||
                                err.contains("Google Güvenlik", ignoreCase = true) ||
                                err.contains("Application-specific", ignoreCase = true) ||
                                err.contains("534", ignoreCase = true) ||
                                err.contains("535", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenAccounts()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(t("Şifreyi Güncelle / Uygulama Şifresi Gir"), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (toEmail.isNotBlank() && subject.isNotBlank()) {
                        if (activeAccount != null) {
                            isSending = true
                            errorMessage = null
                            onSendReal(toEmail.trim(), subject.trim(), body.trim()) { success, err ->
                                isSending = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = err ?: t("Gönderilemedi")
                                }
                            }
                        } else {
                            onSaveSimulated(t("Kullanıcı"), toEmail.trim(), subject.trim(), body.trim(), false, t("Gönderilen"))
                        }
                    }
                },
                enabled = !isSending && toEmail.isNotBlank() && subject.isNotBlank(),
                modifier = Modifier.testTag("compose_submit_btn")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("Gönderiliyor..."))
                } else {
                    Text(t("Gönder ✉️"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("İptal")) }
        }
    )
}
