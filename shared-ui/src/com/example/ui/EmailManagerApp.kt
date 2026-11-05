package com.example.ui

import com.example.common.i18n.t
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.platform.PlatformBackHandler
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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.example.common.model.MailFolderCategory
import com.example.common.model.SmartCategory
import com.example.ui.components.AccountManagementDialog
import com.example.ui.components.EmailReplyDialog
import com.example.ui.screens.CleanTrimScreen
import com.example.ui.screens.EmailDetailScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.SmartRulesScreen
import com.example.ui.screens.TrashArchiveScreen
import com.example.ui.components.MailDrawerContent
import com.example.ui.components.SettingsDialog
import com.example.ui.viewmodel.EmailManagerViewModel
import com.example.ui.viewmodel.MainNavTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailManagerApp(
    viewModel: EmailManagerViewModel
) {
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppBackground()
                    // Arama kutusu odakta kalırsa dönüşte klavye kendiliğinden açılır.
                    focusManager.clearFocus()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    val unreadCount = inboxFolder?.unreadCount ?: allEmails.count { !it.isRead && !it.isArchived && !it.isTrash }
    val holdFolder = folders.firstOrNull { it.category == MailFolderCategory.HOLD }
    val trashCount = maxOf(trashEmails.size, (trashFolder?.totalCount ?: 0) + (holdFolder?.totalCount ?: 0))
    val newsletterCount = cleanSenderGroups.count { it.isMailingList && !it.isUnsubscribed }

    // Telefonun Geri tuşu okuma ekranından uygulamadan çıkmadan listeye döner
    PlatformBackHandler(enabled = selectedEmail != null) {
        viewModel.selectEmail(null)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp
        // Liste ve okuma alanı yan yana ancak yeterli genişlikte okunaklı olur
        val isSplitScreen = maxWidth >= 840.dp

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = isWideScreen || selectedEmail == null,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(310.dp).fillMaxHeight()) {
                    MailDrawerContent(
                        viewModel = viewModel,
                        activeAccount = activeAccount,
                        accountCount = accounts.size,
                        folders = folders,
                        filterState = filterState,
                        currentTab = currentTab,
                        newsletterCount = newsletterCount,
                        onClose = { scope.launch { drawerState.close() } },
                        onOpenInfo = { showAppInfoDialog = true },
                        onOpenSettings = { showSettingsDialog = true }
                    )
                }
            }
        ) {
        if (isWideScreen) {
            // Tablet / Desktop / Wide Screen Canonical Dual-Pane Layout
            Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                // Navigation Rail (Leftmost)
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().testTag("desktop_nav_rail"),
                    header = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp).testTag("rail_menu_btn")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = t("Menü Aç"))
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
                                Icon(if (currentTab == MainNavTab.INBOX) Icons.Default.Inbox else Icons.Outlined.Inbox, contentDescription = t("Gelen"))
                            }
                        },
                        label = { Text(t("Gelen")) },
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
                                Icon(if (currentTab == MainNavTab.CLEAN_TRIM) Icons.Default.CleaningServices else Icons.Outlined.CleaningServices, contentDescription = t("Temizle"))
                            }
                        },
                        label = { Text(t("Temizle")) },
                        modifier = Modifier.testTag("rail_tab_clean")
                    )

                    NavigationRailItem(
                        selected = currentTab == MainNavTab.SMART_RULES,
                        onClick = { viewModel.setTab(MainNavTab.SMART_RULES) },
                        icon = {
                            Icon(if (currentTab == MainNavTab.SMART_RULES) Icons.Default.Tune else Icons.Outlined.Tune, contentDescription = t("Kurallar"))
                        },
                        label = { Text(t("Kurallar")) },
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
                                Icon(if (currentTab == MainNavTab.ARCHIVE_TRASH) Icons.Default.Delete else Icons.Outlined.Delete, contentDescription = t("Çöp"))
                            }
                        },
                        label = { Text(t("Çöp")) },
                        modifier = Modifier.testTag("rail_tab_trash")
                    )
                }

                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Wide Master-Detail Screen Body
                val listPane: @Composable () -> Unit = {
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
                if (isSplitScreen) {
                    Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) { listPane() }

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
                                            text = t("Okumak için bir e-posta seçin"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (selectedEmail != null) {
                            EmailDetailScreen(
                                email = selectedEmail!!,
                                viewModel = viewModel,
                                onBack = { viewModel.selectEmail(null) },
                                isSplitPane = false
                            )
                        } else {
                            listPane()
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
                                                contentDescription = t("Menü Aç")
                                            )
                                        }
                                    },
                                    title = {
                                        Text(
                                            text = t(currentTab.titleTr),
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
                                                contentDescription = t("Bilgi"),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.setShowAccountsDialog(true) },
                                            modifier = Modifier.testTag("appbar_accounts_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = t("Hesaplar")
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
                                            Icon(if (currentTab == MainNavTab.INBOX) Icons.Default.Inbox else Icons.Outlined.Inbox, contentDescription = t("Gelen"))
                                        }
                                    },
                                    label = { Text(t("Gelen"), fontSize = 11.sp) },
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
                                            Icon(if (currentTab == MainNavTab.CLEAN_TRIM) Icons.Default.CleaningServices else Icons.Outlined.CleaningServices, contentDescription = t("Temizle"))
                                        }
                                    },
                                    label = { Text(t("Temizlik"), fontSize = 11.sp) },
                                    modifier = Modifier.testTag("tab_clean")
                                )

                                NavigationBarItem(
                                    selected = currentTab == MainNavTab.SMART_RULES,
                                    onClick = { viewModel.setTab(MainNavTab.SMART_RULES) },
                                    icon = {
                                        Icon(if (currentTab == MainNavTab.SMART_RULES) Icons.Default.Tune else Icons.Outlined.Tune, contentDescription = t("Kurallar"))
                                    },
                                    label = { Text(t("Kurallar"), fontSize = 11.sp) },
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
                                            Icon(if (currentTab == MainNavTab.ARCHIVE_TRASH) Icons.Default.Delete else Icons.Outlined.Delete, contentDescription = t("Çöp"))
                                        }
                                    },
                                    label = { Text(t("Çöp"), fontSize = 11.sp) },
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
                    Text(t("Gerçek Gmail Senkronizasyonu & Bilgi"), fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = t("📌 1. Gerçek Gmail Hesabınızı Bağlama:\n") +
                                    t("Google güvenlik protokolleri gereği normal şifrenizi engeller. Giriş yapabilmek için 'myaccount.google.com/apppasswords' adresinden 16 haneli 'Uygulama Şifresi' alarak hesap ayarlarından giriniz."),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = t("🗑️ 2. Gerçek Gmail Çöp Kutusu'na (Papierkorb) Taşıma:\n") +
                                    t("Uygulamada sildiğiniz e-postalar doğrudan gerçek Gmail hesabınızdaki Çöp Kutusu'na (Papierkorb) aktarılır. Açılan onay penceresiyle silmeyi teyit edebilirsiniz. Silinen e-postalar Gmail mobil ve web uygulamanızda da anında Çöp Kutusu'nda görünür ve geri yüklenebilir."),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = t("⚠️ 3. Kalıcı Olarak Silme:\n") +
                                    t("Çöp Kutusu (Papierkorb) ekranında 'Kalıcı Olarak Sil' veya 'Çöp Kutusunu Boşalt' seçildiğinde, e-postalar Gmail sunucusundan kalıcı olarak tamamen silinir. Bu işlem geri alınamaz."),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = t("📁 4. Hamburger Menü & Klasörler:\n") +
                                    t("Sol üstteki menü butonundan gerçek Gmail'inizdeki gibi tüm etiketlere (Markiert, Wichtig, Käufe, Gesendet, Papierkorb) ve sunucu klasörlerinize doğrudan ulaşabilirsiniz."),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showAppInfoDialog = false }) {
                        Text(t("Anladım"))
                    }
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                viewModel = viewModel,
                account = activeAccount,
                onDismiss = { showSettingsDialog = false }
            )
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
                    viewModel.sendRealEmail(to, subject, body, null, replyTargetEmail?.messageId, onComplete)
                }
            )
        }
    }
}
