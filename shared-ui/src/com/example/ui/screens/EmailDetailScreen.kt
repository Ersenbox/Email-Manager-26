package com.example.ui.screens

import com.example.common.i18n.I18n
import com.example.common.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.common.filter.AutoFolder
import com.example.common.model.SmartCategory
import com.example.ui.components.LabelBadge
import com.example.ui.components.SenderAvatar
import com.example.ui.components.SmartCategoryPill
import com.example.ui.viewmodel.EmailManagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailDetailScreen(
    email: EmailEntity,
    viewModel: EmailManagerViewModel,
    onBack: () -> Unit,
    isSplitPane: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", I18n.locale)
    var showFolderDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(t("Göndereni Engelle")) },
            text = { Text(t("{0} adresinden gelen tüm postalar silinecek ve yenileri otomatik silinir. Çöp sekmesinden geri yükleyebilirsiniz.", email.senderEmail)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        viewModel.blockSender(email.senderEmail, email.senderName)
                        onBack()
                    },
                    modifier = Modifier.testTag("confirm_block_sender")
                ) { Text(t("Engelle")) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text(t("İptal")) }
            }
        )
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text(t("Klasörü Değiştir")) },
            text = {
                Column {
                    Text(t("{0} için seçim sonraki postalarda da geçerli olur.", email.senderEmail), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    for (folder in AutoFolder.entries) {
                        TextButton(
                            onClick = {
                                viewModel.setSenderFolder(email.senderEmail, folder)
                                showFolderDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(t(folder.titleTr)) }
                    }
                    TextButton(
                        onClick = {
                            viewModel.setSenderFolder(email.senderEmail, null)
                            showFolderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(t("Otomatik klasör kullanma")) }
                    TextButton(
                        onClick = {
                            showFolderDialog = false
                            showBlockConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("block_sender_btn")
                    ) { Text(t("Göndereni Engelle"), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text(t("İptal")) }
            }
        )
    }
    val dateStr = dateFormat.format(Date(email.timestamp))
    val category = SmartCategory.fromId(email.categoryId)

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val attachmentChoices by viewModel.attachmentChoices.collectAsStateWithLifecycle()
    val isDownloadingAttachment by viewModel.isDownloadingAttachment.collectAsStateWithLifecycle()

    if (attachmentChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAttachmentChoices() },
            title = { Text(t("Ek Dosyalar"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    for (attachment in attachmentChoices) {
                        TextButton(
                            onClick = { viewModel.openSavedAttachment(attachment) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(attachment.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAttachmentChoices() }) { Text(t("Kapat")) }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
    ) {
        // Top Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(
                            imageVector = if (isSplitPane) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = t("Geri")
                        )
                    }
                    Text(
                        text = if (isSplitPane) t("Önizleme") else t("E-posta Oku"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleStar(email.id) },
                        modifier = Modifier.testTag("detail_star_btn")
                    ) {
                        Icon(
                            imageVector = if (email.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = t("Yıldızla"),
                            tint = if (email.isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(
                        onClick = { showFolderDialog = true },
                        modifier = Modifier.testTag("detail_folder_btn")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = t("Klasörü Değiştir"))
                    }

                    IconButton(
                        onClick = {
                            viewModel.bridge.shareContent(email.subject, email.body)
                        },
                        modifier = Modifier.testTag("detail_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = t("Paylaş"))
                    }

                    IconButton(
                        onClick = {
                            viewModel.archiveEmail(email.id)
                            onBack()
                        },
                        modifier = Modifier.testTag("detail_archive_btn")
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = t("Arşivle"))
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = t("Çöpe At"), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        HorizontalDivider()

        // Content Body (Scrollable)
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Subject Title
            Text(
                text = email.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Category & Labels
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                SmartCategoryPill(category = category)

                val labels = email.labelsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (lbl in labels) {
                    LabelBadge(label = lbl)
                }
            }

            // Sender Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SenderAvatar(name = email.senderName)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = email.senderName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = email.senderEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "${email.storageSizeKb} KB",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Edison Mail Privacy & Spy Tracker Protection Badge
            if (email.trackingPixelBlocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                        .clickable { showPrivacyDialog = true }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Gizlilik Koruması Aktif"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                        Text(
                            text = t("Görünmez takip pikseli bulundu, yüklenmedi (Detay için dokunun)"),
                            fontSize = 11.sp,
                            color = Color(0xFF047857)
                        )
                    }
                }
            }

            // Trimbox 1-Tap Unsubscribe Banner
            if (email.isMailingList) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (email.isUnsubscribed) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (email.isUnsubscribed) Color(0xFF059669) else Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (email.isUnsubscribed) t("Abonelik İptal Edildi") else t("Otomatik Dağıtım / Bülten Listesi"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (email.isUnsubscribed) Color(0xFF059669) else Color(0xFFDC2626)
                                )
                                Text(
                                    text = if (email.isUnsubscribed) t("Abonelikten çıkma isteği gönderildi.") else t("Tek tıkla bülten listesinden çıkabilirsiniz."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!email.isUnsubscribed) {
                            FilledTonalButton(
                                onClick = { viewModel.unsubscribeSender(email.senderEmail) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("detail_unsub_action_btn")
                            ) {
                                Text(t("Abonelikten Çık"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Attachment Card
            if (email.hasAttachment) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = email.attachmentName ?: "Ek_Belge.pdf",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = t("E-posta boyutu: {0} KB", email.storageSizeKb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.openAttachments(email) },
                            enabled = !isDownloadingAttachment,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.testTag("detail_open_attachment_btn")
                        ) {
                            Text(if (isDownloadingAttachment) t("İndiriliyor...") else t("Aç"), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Body text
            Text(
                text = email.body,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Smart Quick Reply Suggestions (1-Tap from Reading View)
            Text(
                text = t("Hızlı Yanıtla:"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickSuggestions = listOf(
                    t("Teşekkür ederim, aldım! 👍"),
                    t("İnceleyip en kısa sürede dönüş yapacağım. ⏳"),
                    t("Onaylıyorum, uygun. ✅"),
                    t("Toplantıya katılım sağlayacağım. 📅")
                )
                for (sug in quickSuggestions) {
                    SuggestionChip(
                        onClick = {
                            viewModel.openReplyDialog(
                                email.copy(
                                    body = t("{0}\n\n--- Orijinal İleti ---\n{1}", sug, email.body)
                                )
                            )
                        },
                        label = { Text(sug, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reply & Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.openReplyDialog(email)
                    },
                    modifier = Modifier.weight(1f).testTag("reply_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("Yanıtla"))
                }

                OutlinedButton(
                    onClick = {
                        viewModel.openReplyDialog(
                            email.copy(
                                subject = "Fwd: ${email.subject}",
                                body = t("\n\n---------- İletilen İleti ----------\nGönderen: {0} <{1}>\nKonu: {2}\n\n{3}", email.senderName, email.senderEmail, email.subject, email.body)
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("forward_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t("İlet"))
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF059669))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(t("Gizlilik Kalkanı Raporu"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = t("Bu e-postada gizli bir takip pikseli bulundu. Gönderen bununla açılma saatinizi, IP adresinizi ve yaklaşık konumunuzu öğrenebilir. Uygulama uzak görselleri yüklemediği için piksel çalışmadı."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = t("• Gönderen: {0}\n• Durum: Piksel yüklenmedi ✅", email.senderName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text(t("Anladım"))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
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
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_detail_delete_btn")
                ) {
                    Text(t("Çöp Kutusuna Aktar"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(t("İptal")) }
            }
        )
    }
}
