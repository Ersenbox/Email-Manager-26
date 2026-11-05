package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LabelImportant
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.common.data.EmailAccountEntity
import com.example.common.i18n.t
import com.example.common.model.MailFolderCategory
import com.example.common.model.MailFolderInfo
import com.example.ui.viewmodel.EmailFilterState
import com.example.ui.viewmodel.EmailManagerViewModel
import com.example.ui.viewmodel.MainNavTab

/** Menü, sunucudaki gerçek klasörlerden ve sayılarından oluşur. */
@Composable
fun MailDrawerContent(
    viewModel: EmailManagerViewModel,
    activeAccount: EmailAccountEntity?,
    accountCount: Int,
    folders: List<MailFolderInfo>,
    filterState: EmailFilterState,
    currentTab: MainNavTab,
    newsletterCount: Int,
    onClose: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isMailList = currentTab == MainNavTab.INBOX
    val openFolder = { folder: MailFolderInfo ->
        viewModel.setTab(MainNavTab.INBOX)
        viewModel.resetFilters()
        viewModel.selectFolder(folder)
        onClose()
    }
    val openInbox = {
        viewModel.setTab(MainNavTab.INBOX)
        viewModel.resetFilters()
        onClose()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            if (activeAccount != null) {
                AccountAvatar(activeAccount, 44.dp)
            }
            Column {
                Text(
                    text = activeAccount?.accountName?.ifBlank { null } ?: "Email-Manager 26",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeAccount?.emailAddress ?: t("Henüz hesap eklenmedi"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(6.dp))

        if (accountCount > 1) {
            DrawerRow(
                icon = Icons.Default.AllInbox,
                label = t("Tüm Gelen Kutuları"),
                selected = isMailList && filterState.selectedFolder == null,
                onClick = openInbox
            )
        }

        val standardFolders = folders.filter { it.category != MailFolderCategory.CUSTOM && it.category != MailFolderCategory.SUBSCRIPTIONS }
        if (standardFolders.isEmpty()) {
            DrawerRow(icon = Icons.Default.Inbox, label = t("Gelen Kutusu"), selected = isMailList, onClick = openInbox)
        }
        for (folder in standardFolders) {
            val isTrash = folder.category == MailFolderCategory.TRASH || folder.category == MailFolderCategory.HOLD
            DrawerRow(
                icon = folderIcon(folder.category),
                iconTint = if (folder.category == MailFolderCategory.STARRED) Color(0xFFF59E0B) else null,
                label = shortFolderTitle(folder.category),
                count = folder.totalCount,
                unreadCount = folder.unreadCount,
                selected = if (isTrash) {
                    currentTab == MainNavTab.ARCHIVE_TRASH
                } else {
                    isMailList && (filterState.selectedFolder?.fullName == folder.fullName ||
                        (filterState.selectedFolder == null && folder.category == MailFolderCategory.INBOX && accountCount <= 1))
                },
                tag = "drawer_folder_${folder.category.name.lowercase()}",
                onClick = { openFolder(folder) }
            )
        }

        val customFolders = folders.filter { it.category == MailFolderCategory.CUSTOM && it.fullName != "EM26" }
        if (customFolders.isNotEmpty()) {
            SectionLabel(t("Sunucu Klasörleri"))
            for (folder in customFolders) {
                DrawerRow(
                    icon = Icons.Default.Folder,
                    label = t(folder.displayName),
                    count = folder.totalCount,
                    unreadCount = folder.unreadCount,
                    selected = isMailList && filterState.selectedFolder?.fullName == folder.fullName,
                    onClick = { openFolder(folder) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp))
        SectionLabel(t("Araçlar"))

        DrawerRow(
            icon = Icons.Default.NotificationsOff,
            label = t("Abonelikleri Yönet"),
            count = newsletterCount,
            selected = currentTab == MainNavTab.CLEAN_TRIM,
            onClick = {
                viewModel.setTab(MainNavTab.CLEAN_TRIM)
                onClose()
            }
        )
        DrawerRow(
            icon = Icons.Default.Tune,
            label = t("Akıllı Filtreler & Kurallar"),
            selected = currentTab == MainNavTab.SMART_RULES,
            onClick = {
                viewModel.setTab(MainNavTab.SMART_RULES)
                onClose()
            }
        )
        DrawerRow(
            icon = Icons.Default.AccountCircle,
            label = t("Hesapları Yönet & Giriş"),
            selected = false,
            onClick = {
                onClose()
                viewModel.setShowAccountsDialog(true)
            }
        )
        DrawerRow(
            icon = Icons.Default.Settings,
            label = t("Ayarlar"),
            selected = false,
            tag = "drawer_settings",
            onClick = {
                onClose()
                onOpenSettings()
            }
        )
        DrawerRow(
            icon = Icons.Default.Info,
            label = t("Yardım & Bilgi"),
            selected = false,
            onClick = {
                onClose()
                onOpenInfo()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int = 0,
    unreadCount: Int = 0,
    iconTint: Color? = null,
    tag: String? = null
) {
    NavigationDrawerItem(
        icon = {
            if (iconTint != null) {
                Icon(icon, contentDescription = null, tint = iconTint)
            } else {
                Icon(icon, contentDescription = null)
            }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        badge = {
            // Okunmamış / toplam: Gmail'in yan menüsü okunmamış sayıyı gösterir
            if (count > 0) {
                Text(
                    text = if (unreadCount > 0) "$unreadCount / $count" else "$count",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    )
}

/** "Tüm Postalar (Alle E-Mails)" gibi Türkçe adlarda parantez menüye sığmaz. */
private fun shortFolderTitle(category: MailFolderCategory): String =
    t(category.displayNameTr).substringBefore(" (")

private fun folderIcon(category: MailFolderCategory): ImageVector = when (category) {
    MailFolderCategory.INBOX -> Icons.Default.Inbox
    MailFolderCategory.STARRED -> Icons.Default.Star
    MailFolderCategory.SNOOZED -> Icons.Default.Schedule
    MailFolderCategory.IMPORTANT -> Icons.Default.LabelImportant
    MailFolderCategory.PURCHASES -> Icons.Default.ShoppingBag
    MailFolderCategory.SENT -> Icons.Default.Send
    MailFolderCategory.SCHEDULED -> Icons.Default.AccessTime
    MailFolderCategory.OUTBOX -> Icons.Default.Outbox
    MailFolderCategory.DRAFTS -> Icons.Default.Drafts
    MailFolderCategory.ALL_MAIL -> Icons.Default.Mail
    MailFolderCategory.SPAM -> Icons.Default.Report
    MailFolderCategory.TRASH -> Icons.Default.Delete
    MailFolderCategory.HOLD -> Icons.Default.DeleteSweep
    else -> Icons.Default.Folder
}
