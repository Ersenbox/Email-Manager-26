package com.example.ui.screens

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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shadowElevation = 1.dp
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
                            contentDescription = "Gmail Menüsü",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = filterState.query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = {
                            Text(
                                text = "Gmail'de ara...",
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
                            Icon(Icons.Default.Clear, contentDescription = "Aramayı Temizle")
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
                                contentDescription = "Manuel Senkronize Et",
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
                            contentDescription = "Bilgi",
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
                                val initial = activeAccount?.emailAddress?.take(1)?.uppercase() ?: "?"
                                Text(
                                    text = initial,
                                    color = if (activeAccount != null) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
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
                                text = syncStatusMessage ?: "Senkronize ediliyor...",
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
                                text = syncStatusMessage ?: "Gmail tam senkronize",
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
                        Text(if (isSyncing) "Eşitleniyor" else "Eşitle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                text = "Gmail Girişi: 16 Haneli Şifre Gerekli",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Google normal şifrenizi engelledi. 'Uygulama Şifresi' girmek için buraya dokunun.",
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
                    label = { Text("Tümü", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_all")
                )

                FilterChip(
                    selected = filterState.onlyUnread,
                    onClick = { viewModel.toggleOnlyUnread() },
                    label = {
                        val unreadCount = allEmails.count { !it.isRead && !it.isTrash }
                        Text(if (unreadCount > 0) "Okunmamış ($unreadCount)" else "Okunmamış", fontSize = 12.sp)
                    },
                    leadingIcon = { Icon(Icons.Default.MarkEmailUnread, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_unread")
                )

                FilterChip(
                    selected = filterState.onlyStarred,
                    onClick = { viewModel.toggleOnlyStarred() },
                    label = {
                        val starCount = allEmails.count { it.isStarred && !it.isTrash }
                        Text(if (starCount > 0) "Yıldızlı ($starCount)" else "Yıldızlı", fontSize = 12.sp)
                    },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("filter_chip_starred")
                )

                FilterChip(
                    selected = filterState.onlyWithAttachments,
                    onClick = { viewModel.toggleOnlyWithAttachments() },
                    label = { Text("Ekli Dosyalar", fontSize = 12.sp) },
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
                    label = { Text("Bültenler", fontSize = 12.sp) },
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
                    label = { Text("Käufe / Alışveriş", fontSize = 12.sp) },
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
                                Icon(Icons.Default.Close, contentDescription = "Vazgeç")
                            }
                            Text(
                                text = "${selectedIds.size} seçildi",
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
                                Text(if (selectedIds.size == filteredEmails.size) "Kaldır" else "Tümü", fontSize = 11.sp)
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
                                    Icon(Icons.Default.MarkEmailRead, contentDescription = "Okundu Yap", modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.setReadBatch(selectedIds.toList(), false)
                                        isSelectionMode = false
                                        selectedIds.clear()
                                    },
                                    modifier = Modifier.testTag("batch_mark_unread_btn")
                                ) {
                                    Icon(Icons.Default.MarkEmailUnread, contentDescription = "Okunmadı Yap", modifier = Modifier.size(18.dp))
                                }

                                Button(
                                    onClick = { showBatchDeleteConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("batch_delete_btn")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sil (${selectedIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        val folderName = filterState.selectedFolder?.displayName ?: "Gelen Kutusu (Posteingang)"
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
                                text = "• ${filteredEmails.size} e-posta",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filterState.selectedFolder != null || filterState.onlyStarred || filterState.query.isNotEmpty()) {
                                TextButton(onClick = { viewModel.resetFilters() }) {
                                    Text("Sıfırla", fontSize = 11.sp)
                                }
                            }

                            if (filteredEmails.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { isSelectionMode = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("toggle_selection_mode_btn")
                                ) {
                                    Text("Seç", fontSize = 11.sp)
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
                            text = if (activeAccount == null) "Henüz E-posta Hesabı Eklenmedi" else "Bu Klasörde E-posta Yok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (activeAccount == null)
                                "Demo postalar kaldırıldı. Gerçek Gmail hesabınızı bağlayarak e-postalarınızı hemen eşitleyebilirsiniz."
                            else
                                "Yeni e-postaları sunucudan çekmek için 'Senkronize Et' butonuna dokunun.",
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
                                Text("Gmail / E-posta Hesabı Ekle", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.syncActiveAccount(filterState.selectedFolder?.fullName ?: "INBOX") },
                                enabled = !isSyncing,
                                modifier = Modifier.testTag("empty_state_sync_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Şimdi Senkronize Et", fontWeight = FontWeight.Bold)
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
                Icon(Icons.Default.Add, contentDescription = "Yeni Posta")
                Spacer(modifier = Modifier.width(6.dp))
                Text("E-posta Yaz", fontWeight = FontWeight.Bold)
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
                    Text("Çöp Kutusuna Taşı", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "\"${email.subject}\" başlıklı e-posta silinecek ve Gmail hesabınızdaki gerçek Çöp Kutusu'na (Papierkorb) aktarılacaktır.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "İstediğiniz zaman Çöp Kutusu'ndan bu e-postayı geri yükleyebilirsiniz. Devam etmek istiyor musunuz?",
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
                    Text("Çöp Kutusuna Aktar")
                }
            },
            dismissButton = {
                TextButton(onClick = { emailToTrash = null }) { Text("İptal") }
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
                    Text("Seçilen E-postaları Çöpe At", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Seçilen ${selectedIds.size} adet e-posta silinecek ve Gmail hesabınızdaki gerçek Çöp Kutusu'na (Papierkorb) aktarılacaktır.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "İstediğiniz zaman Çöp Kutusu'ndan bu e-postaları geri yükleyebilirsiniz. Devam etmek istiyor musunuz?",
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
                    Text("Çöp Kutusuna Aktar (${selectedIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("İptal") }
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
                viewModel.sendRealEmail(to, subject, body, null, onComplete)
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
                    text = "Yeni E-posta Gönder (SMTP)",
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
                                text = "Gönderen: ${activeAccount.emailAddress}",
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
                    label = { Text("Kime (Alıcı E-posta)") },
                    placeholder = { Text("ornek@alanadi.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("compose_to_input")
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Konu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("compose_subject_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("İçerik") },
                    placeholder = { Text("E-posta metnini buraya yazın...") },
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
                                text = "Hata: $err",
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
                                    Text("Şifreyi Güncelle / Uygulama Şifresi Gir", fontSize = 11.sp)
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
                                    errorMessage = err ?: "Gönderilemedi"
                                }
                            }
                        } else {
                            onSaveSimulated("Kullanıcı", toEmail.trim(), subject.trim(), body.trim(), false, "Gönderilen")
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
                    Text("Gönderiliyor...")
                } else {
                    Text("Gönder ✉️")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
