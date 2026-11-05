package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LabelImportant
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.common.model.MailFolderCategory
import com.example.common.model.SmartCategory
import com.example.ui.components.AccountManagementDialog
import com.example.ui.components.EmailReplyDialog
import com.example.ui.screens.CleanTrimScreen
import com.example.ui.screens.EmailDetailScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.SmartRulesScreen
import com.example.ui.screens.TrashArchiveScreen
import com.example.ui.viewmodel.EmailManagerViewModel
import com.example.ui.viewmodel.MainNavTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailManagerApp(
    viewModel: EmailManagerViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedEmail by viewModel.selectedEmail.collectAsStateWithLifecycle()
    val allEmails by viewModel.allEmails.collectAsStateWithLifecycle()
    val trashEmails by viewModel.trashEmails.collectAsStateWithLifecycle()
    val cleanSenderGroups by viewModel.cleanSenderGroups.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val showAccountsDialog by viewModel.showAccountsDialog.collectAsStateWithLifecycle()
    val showReplyDialog by viewModel.showReplyDialog.collectAsStateWithLifecycle()
    val replyTargetEmail by viewModel.replyTargetEmail.collectAsStateWithLifecycle()

    // Calculated badge counts matching real Gmail structure & server folder hierarchy
    val inboxFolder = folders.firstOrNull { it.category == MailFolderCategory.INBOX }
    val trashFolder = folders.firstOrNull { it.category == MailFolderCategory.TRASH }
    val spamFolder = folders.firstOrNull { it.category == MailFolderCategory.SPAM }
    val purchasesFolder = folders.firstOrNull { it.category == MailFolderCategory.PURCHASES }

    val unreadCount = allEmails.count { !it.isRead && !it.isArchived && !it.isTrash }
    val displayInboxCount = when {
        inboxFolder != null && inboxFolder.unreadCount > 0 -> inboxFolder.unreadCount
        inboxFolder != null && inboxFolder.totalCount > 0 -> inboxFolder.totalCount
        unreadCount > 0 -> unreadCount
        else -> allEmails.count { !it.isTrash }
    }
    val starredCount = allEmails.count { it.isStarred && !it.isTrash }
    val importantCount = allEmails.count { it.categoryId == SmartCategory.PRIMARY.id && !it.isTrash }
    val purchasesCount = if (purchasesFolder != null && purchasesFolder.totalCount > 0) {
        purchasesFolder.totalCount
    } else {
        allEmails.count { (it.categoryId == SmartCategory.BILLS.id || it.labelsCsv.contains("Käufe", true)) && !it.isTrash }
    }
    val sentCount = allEmails.count { it.labelsCsv.contains("Gönderilen", true) || it.folderName.contains("sent", true) }
    val allMailCount = allEmails.count { !it.isTrash }
    val spamCount = if (spamFolder != null && spamFolder.totalCount > 0) {
        spamFolder.totalCount
    } else {
        allEmails.count { it.categoryId == SmartCategory.SPAM.id || it.folderName.contains("spam", true) }
    }
    val trashCount = if (trashEmails.isNotEmpty()) {
        trashEmails.size
    } else if (trashFolder != null && trashFolder.totalCount > 0) {
        trashFolder.totalCount
    } else {
        0
    }
    val newsletterCount = cleanSenderGroups.count { it.isMailingList && !it.isUnsubscribed }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Tablet / Desktop / Wide Screen Canonical Dual-Pane Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail (Leftmost)
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().testTag("desktop_nav_rail"),
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
                        ) {
                            Text(
                                text = "M",
                                color = Color(0xFFEA4335),
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Gmail", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                ) {
                    NavigationRailItem(
                        selected = currentTab == MainNavTab.INBOX,
                        onClick = { viewModel.setTab(MainNavTab.INBOX) },
                        icon = {
                            BadgedBox(badge = {
                                if (unreadCount > 0) {
                                    Badge { Text("$unreadCount") }
                                }
                            }) {
                                Icon(if (currentTab == MainNavTab.INBOX) Icons.Default.Inbox else Icons.Outlined.Inbox, contentDescription = "Gelen")
                            }
                        },
                        label = { Text("Gelen") },
                        modifier = Modifier.testTag("rail_tab_inbox")
                    )

                    NavigationRailItem(
                        selected = currentTab == MainNavTab.CLEAN_TRIM,
                        onClick = { viewModel.setTab(MainNavTab.CLEAN_TRIM) },
                        icon = {
                            BadgedBox(badge = {
                                if (newsletterCount > 0) {
                                    Badge(containerColor = Color(0xFFEF4444)) { Text("$newsletterCount") }
                                }
                            }) {
                                Icon(if (currentTab == MainNavTab.CLEAN_TRIM) Icons.Default.CleaningServices else Icons.Outlined.CleaningServices, contentDescription = "Temizle")
                            }
                        },
                        label = { Text("Temizle") },
                        modifier = Modifier.testTag("rail_tab_clean")
                    )

                    NavigationRailItem(
                        selected = currentTab == MainNavTab.SMART_RULES,
                        onClick = { viewModel.setTab(MainNavTab.SMART_RULES) },
                        icon = {
                            Icon(if (currentTab == MainNavTab.SMART_RULES) Icons.Default.Tune else Icons.Outlined.Tune, contentDescription = "Kurallar")
                        },
                        label = { Text("Kurallar") },
                        modifier = Modifier.testTag("rail_tab_rules")
                    )

                    NavigationRailItem(
                        selected = currentTab == MainNavTab.ARCHIVE_TRASH,
                        onClick = { viewModel.setTab(MainNavTab.ARCHIVE_TRASH) },
                        icon = {
                            BadgedBox(badge = {
                                if (trashCount > 0) {
                                    Badge { Text("$trashCount") }
                                }
                            }) {
                                Icon(if (currentTab == MainNavTab.ARCHIVE_TRASH) Icons.Default.Delete else Icons.Outlined.Delete, contentDescription = "Çöp")
                            }
                        },
                        label = { Text("Çöp") },
                        modifier = Modifier.testTag("rail_tab_trash")
                    )
                }

                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Wide Master-Detail Screen Body
                Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                        when (currentTab) {
                            MainNavTab.INBOX -> InboxScreen(viewModel = viewModel)
                            MainNavTab.CLEAN_TRIM -> CleanTrimScreen(viewModel = viewModel)
                            MainNavTab.SMART_RULES -> SmartRulesScreen(viewModel = viewModel)
                            MainNavTab.ARCHIVE_TRASH -> TrashArchiveScreen(viewModel = viewModel)
                        }
                    }

                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Box(modifier = Modifier.weight(1.3f).fillMaxHeight()) {
                        if (selectedEmail != null) {
                            EmailDetailScreen(
                                email = selectedEmail!!,
                                viewModel = viewModel,
                                onBack = { viewModel.selectEmail(null) },
                                isSplitPane = true
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Mail,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Okumak için bir e-posta seçin",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Mobile Phone (Vertical layout for Android & iPhone)
            if (selectedEmail != null) {
                EmailDetailScreen(
                    email = selectedEmail!!,
                    viewModel = viewModel,
                    onBack = { viewModel.selectEmail(null) },
                    isSplitPane = false
                )
            } else {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var showAppInfoDialog by remember { mutableStateOf(false) }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .width(310.dp)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Header: Gmail Red Branding & Active Account
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 20.dp, vertical = 18.dp)
                                ) {
                                    Text(
                                        text = "Gmail",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEA4335)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activeAccount?.emailAddress ?: "bakiciersen@gmail.com",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Spacer(modifier = Modifier.height(6.dp))

                                // "Alle Posteingänge"
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.AllInbox, contentDescription = null) },
                                    label = { Text("Alle Posteingänge") },
                                    selected = currentTab == MainNavTab.INBOX && filterState.selectedFolder == null && !filterState.onlyStarred,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // "Posteingang" (Gelen Kutusu with unread badge)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                                    badge = {
                                        if (displayInboxCount > 0) {
                                            Text("$displayInboxCount", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    },
                                    label = { Text("Posteingang") },
                                    selected = currentTab == MainNavTab.INBOX && (filterState.selectedFolder?.category == MailFolderCategory.INBOX || (filterState.selectedFolder == null && !filterState.onlyStarred)),
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Section: "Alle Labels" (Matching User Screenshot!)
                                Text(
                                    text = "Alle Labels",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                // Markiert (Starred)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                    label = { Text("Markiert") },
                                    badge = { if (starredCount > 0) Text("$starredCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.INBOX && filterState.onlyStarred,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setOnlyStarred(true)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Zurückgestellt (Snoozed)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                    label = { Text("Zurückgestellt") },
                                    selected = currentTab == MainNavTab.INBOX && filterState.category == SmartCategory.SNOOZED,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setCategory(SmartCategory.SNOOZED)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Wichtig (Important)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.LabelImportant, contentDescription = null) },
                                    label = { Text("Wichtig") },
                                    badge = { if (importantCount > 0) Text("$importantCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.INBOX && filterState.category == SmartCategory.PRIMARY,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setCategory(SmartCategory.PRIMARY)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Käufe (Purchases)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
                                    label = { Text("Käufe") },
                                    badge = { if (purchasesCount > 0) Text("$purchasesCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.INBOX && filterState.category == SmartCategory.BILLS,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setCategory(SmartCategory.BILLS)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Gesendet (Sent)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                                    label = { Text("Gesendet") },
                                    badge = { if (sentCount > 0) Text("$sentCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.INBOX && filterState.selectedLabel == "Gönderilen",
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setSelectedLabel("Gönderilen")
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Geplant (Scheduled)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                                    label = { Text("Geplant") },
                                    selected = currentTab == MainNavTab.INBOX && filterState.selectedLabel == "Geplant",
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setSelectedLabel("Geplant")
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Postausgang (Outbox)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Outbox, contentDescription = null) },
                                    label = { Text("Postausgang") },
                                    selected = currentTab == MainNavTab.INBOX && filterState.selectedLabel == "Postausgang",
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setSelectedLabel("Postausgang")
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Entwürfe (Drafts)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Drafts, contentDescription = null) },
                                    label = { Text("Entwürfe") },
                                    selected = currentTab == MainNavTab.INBOX && filterState.selectedLabel == "Taslak",
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setSelectedLabel("Taslak")
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Alle E-Mails (All Mail)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Mail, contentDescription = null) },
                                    label = { Text("Alle E-Mails") },
                                    badge = { if (allMailCount > 0) Text("$allMailCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.INBOX && filterState.selectedFolder == null && !filterState.onlyStarred,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Spam
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Report, contentDescription = null) },
                                    label = { Text("Spam") },
                                    badge = { if (spamCount > 0) Text("$spamCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.INBOX && filterState.category == SmartCategory.SPAM,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.INBOX)
                                        viewModel.resetFilters()
                                        viewModel.setCategory(SmartCategory.SPAM)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Papierkorb (Trash)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    label = { Text("Papierkorb") },
                                    badge = { if (trashCount > 0) Text("$trashCount", fontSize = 12.sp) },
                                    selected = currentTab == MainNavTab.ARCHIVE_TRASH,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.ARCHIVE_TRASH)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Abos verwalten (Abonelikleri Yönet / Bülten Temizliği)
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                    label = { Text("Abos verwalten") },
                                    badge = { if (newsletterCount > 0) Text("$newsletterCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) },
                                    selected = currentTab == MainNavTab.CLEAN_TRIM,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.CLEAN_TRIM)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Akıllı Filtreler & Kurallar
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    label = { Text("Akıllı Filtreler & Kurallar") },
                                    selected = currentTab == MainNavTab.SMART_RULES,
                                    onClick = {
                                        viewModel.setTab(MainNavTab.SMART_RULES)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                // Discovered dynamic IMAP server folders (if any custom folder exists)
                                val customFolders = folders.filter { it.category == MailFolderCategory.CUSTOM }
                                if (customFolders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Sunucu Klasörleri",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                    )
                                    for (cf in customFolders) {
                                        NavigationDrawerItem(
                                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                            label = { Text(cf.displayName) },
                                            badge = { if (cf.totalCount > 0) Text("${cf.totalCount}", fontSize = 12.sp) },
                                            selected = filterState.selectedFolder?.fullName == cf.fullName,
                                            onClick = {
                                                viewModel.setTab(MainNavTab.INBOX)
                                                viewModel.selectFolder(cf)
                                                scope.launch { drawerState.close() }
                                            },
                                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp))

                                // Google-Apps & Tools Section
                                Text(
                                    text = "Google-Apps & Araçlar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                    label = { Text("Hesapları Yönet & Giriş") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        viewModel.setShowAccountsDialog(true)
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    label = { Text("Hilfe & Bilgi (Info)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        showAppInfoDialog = true
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (currentTab != MainNavTab.INBOX) {
                                CenterAlignedTopAppBar(
                                    navigationIcon = {
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier.testTag("appbar_hamburger_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Menü Aç"
                                            )
                                        }
                                    },
                                    title = {
                                        Text(
                                            text = currentTab.titleTr,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { showAppInfoDialog = true },
                                            modifier = Modifier.testTag("appbar_info_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Bilgi",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.setShowAccountsDialog(true) },
                                            modifier = Modifier.testTag("appbar_accounts_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = "Hesaplar"
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.testTag("main_top_app_bar")
                                )
                            }
                        },
                        bottomBar = {
                            NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                                NavigationBarItem(
                                    selected = currentTab == MainNavTab.INBOX,
                                    onClick = { viewModel.setTab(MainNavTab.INBOX) },
                                    icon = {
                                        BadgedBox(badge = {
                                            if (unreadCount > 0) {
                                                Badge { Text("$unreadCount") }
                                            }
                                        }) {
                                            Icon(if (currentTab == MainNavTab.INBOX) Icons.Default.Inbox else Icons.Outlined.Inbox, contentDescription = "Gelen")
                                        }
                                    },
                                    label = { Text("Gelen", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("tab_inbox")
                                )

                                NavigationBarItem(
                                    selected = currentTab == MainNavTab.CLEAN_TRIM,
                                    onClick = { viewModel.setTab(MainNavTab.CLEAN_TRIM) },
                                    icon = {
                                        BadgedBox(badge = {
                                            if (newsletterCount > 0) {
                                                Badge(containerColor = Color(0xFFEF4444)) { Text("$newsletterCount") }
                                            }
                                        }) {
                                            Icon(if (currentTab == MainNavTab.CLEAN_TRIM) Icons.Default.CleaningServices else Icons.Outlined.CleaningServices, contentDescription = "Temizle")
                                        }
                                    },
                                    label = { Text("Temizlik", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("tab_clean")
                                )

                                NavigationBarItem(
                                    selected = currentTab == MainNavTab.SMART_RULES,
                                    onClick = { viewModel.setTab(MainNavTab.SMART_RULES) },
                                    icon = {
                                        Icon(if (currentTab == MainNavTab.SMART_RULES) Icons.Default.Tune else Icons.Outlined.Tune, contentDescription = "Kurallar")
                                    },
                                    label = { Text("Kurallar", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("tab_rules")
                                )

                                NavigationBarItem(
                                    selected = currentTab == MainNavTab.ARCHIVE_TRASH,
                                    onClick = { viewModel.setTab(MainNavTab.ARCHIVE_TRASH) },
                                    icon = {
                                        BadgedBox(badge = {
                                            if (trashCount > 0) {
                                                Badge { Text("$trashCount") }
                                            }
                                        }) {
                                            Icon(if (currentTab == MainNavTab.ARCHIVE_TRASH) Icons.Default.Delete else Icons.Outlined.Delete, contentDescription = "Çöp")
                                        }
                                    },
                                    label = { Text("Çöp", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("tab_trash")
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            when (currentTab) {
                                MainNavTab.INBOX -> InboxScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenAppInfo = { showAppInfoDialog = true }
                                )
                                MainNavTab.CLEAN_TRIM -> CleanTrimScreen(viewModel = viewModel)
                                MainNavTab.SMART_RULES -> SmartRulesScreen(viewModel = viewModel)
                                MainNavTab.ARCHIVE_TRASH -> TrashArchiveScreen(viewModel = viewModel)
                            }
                        }
                    }
                }

                // App Info & User Guide Dialog (Demo yerine net bilgilendirme)
                if (showAppInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showAppInfoDialog = false },
                        icon = {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        title = {
                            Text("Gerçek Gmail Senkronizasyonu & Bilgi", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "📌 1. Gerçek Gmail Hesabınızı Bağlama:\n" +
                                            "Google güvenlik protokolleri gereği normal şifrenizi engeller. Giriş yapabilmek için 'myaccount.google.com/apppasswords' adresinden 16 haneli 'Uygulama Şifresi' alarak hesap ayarlarından giriniz.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "🗑️ 2. Gerçek Gmail Çöp Kutusu'na (Papierkorb) Taşıma:\n" +
                                            "Uygulamada sildiğiniz e-postalar doğrudan gerçek Gmail hesabınızdaki Çöp Kutusu'na (Papierkorb) aktarılır. Açılan onay penceresiyle silmeyi teyit edebilirsiniz. Silinen e-postalar Gmail mobil ve web uygulamanızda da anında Çöp Kutusu'nda görünür ve geri yüklenebilir.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "⚠️ 3. Kalıcı Olarak Silme:\n" +
                                            "Çöp Kutusu (Papierkorb) ekranında 'Kalıcı Olarak Sil' veya 'Çöp Kutusunu Boşalt' seçildiğinde, e-postalar Gmail sunucusundan kalıcı olarak tamamen silinir. Bu işlem geri alınamaz.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "📁 4. Hamburger Menü & Klasörler:\n" +
                                            "Sol üstteki menü butonundan gerçek Gmail'inizdeki gibi tüm etiketlere (Markiert, Wichtig, Käufe, Gesendet, Papierkorb) ve sunucu klasörlerinize doğrudan ulaşabilirsiniz.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showAppInfoDialog = false }) {
                                Text("Anladım")
                            }
                        }
                    )
                }
            }
        }

        // Account Management Dialog (Real IMAP/SMTP configuration)
        if (showAccountsDialog) {
            AccountManagementDialog(
                accounts = accounts,
                activeAccount = activeAccount,
                onDismiss = { viewModel.setShowAccountsDialog(false) },
                onSetActive = { id -> viewModel.setActiveAccount(id) },
                onDeleteAccount = { acc -> viewModel.deleteAccount(acc) },
                onSaveAccount = { acc -> viewModel.saveAccount(acc) },
                onTestConnection = { acc, cb -> viewModel.testAccountConnection(acc, cb) },
                onSyncNow = { viewModel.syncActiveAccount() },
                onOpenGoogleAppPasswords = { viewModel.openGoogleAppPasswordsUrl() }
            )
        }

        // Real Email Reply Dialog
        if (showReplyDialog && replyTargetEmail != null) {
            EmailReplyDialog(
                targetEmail = replyTargetEmail!!,
                activeSenderAccountEmail = activeAccount?.emailAddress,
                onDismiss = { viewModel.closeReplyDialog() },
                onOpenAccounts = { viewModel.setShowAccountsDialog(true) },
                onSendReply = { to, subject, body, onComplete ->
                    viewModel.sendRealEmail(to, subject, body, null, onComplete)
                }
            )
        }
    }
}
