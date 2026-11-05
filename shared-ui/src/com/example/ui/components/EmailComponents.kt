package com.example.ui.components

import com.example.common.i18n.I18n
import com.example.common.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.common.data.EmailEntity
import com.example.common.model.SmartCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SenderAvatar(name: String, modifier: Modifier = Modifier) {
    val initial = if (name.isNotEmpty()) name.first().uppercase() else "E"
    val colorIndex = (name.hashCode().coerceAtLeast(0)) % 6
    val colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF7C3AED),
        Color(0xFF059669),
        Color(0xFFD97706),
        Color(0xFFDC2626),
        Color(0xFF0891B2)
    )
    val bgColor = colors[colorIndex]

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
    }
}

@Composable
fun SmartCategoryPill(category: SmartCategory, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(category.colorHex).copy(alpha = 0.12f)
    ) {
        Text(
            text = t(category.titleTr),
            color = Color(category.colorHex),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun LabelBadge(label: String, colorHex: Long = 0xFF475569, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(colorHex).copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(colorHex).copy(alpha = 0.3f))
    ) {
        Text(
            text = "#$label",
            color = Color(colorHex),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailListItem(
    email: EmailEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onUnsubscribe: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", I18n.locale)
    val dateStr = dateFormat.format(Date(email.timestamp))
    val category = SmartCategory.fromId(email.categoryId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .testTag("email_item_${email.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else if (!email.isRead) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!email.isRead) 2.dp else 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Avatar, Sender Name, Unread dot, Date, Star
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SenderAvatar(name = email.senderName)

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = email.senderName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!email.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    Text(
                        text = email.senderEmail,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier.size(32.dp).testTag("star_btn_${email.id}")
                    ) {
                        Icon(
                            imageVector = if (email.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = t("Yıldızla"),
                            tint = if (email.isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subject
            Text(
                text = email.subject,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Snippet Preview
            Text(
                text = email.preview,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tags, Categories & 1-Tap Unsubscribe Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmartCategoryPill(category = category)

                val labelList = email.labelsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (lbl in labelList) {
                    LabelBadge(label = lbl)
                }

                if (email.hasAttachment) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = t("Ek Dosya"),
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = email.attachmentName ?: t("Ek"),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (email.trackingPixelBlocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = t("İzleyici Engellendi"),
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF059669)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = t("Piksel Engellendi"),
                            fontSize = 10.sp,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 1-Tap Unsubscribe Action Bar (Edison Mail & Trimbox feature)
            if (email.isMailingList) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (email.isUnsubscribed) Color(0xFF10B981).copy(alpha = 0.12f)
                            else Color(0xFFEF4444).copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (email.isUnsubscribed) t("Abonelikten Çıkma İsteği Gönderildi") else t("Bülten / Pazarlama Postası"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (email.isUnsubscribed) Color(0xFF059669) else Color(0xFFDC2626)
                    )

                    if (!email.isUnsubscribed) {
                        FilledTonalButton(
                            onClick = onUnsubscribe,
                            modifier = Modifier.height(30.dp).testTag("unsub_btn_${email.id}"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("1-Tık Abonelikten Çık"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Bottom action icons
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${email.storageSizeKb} KB",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                )

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onArchive,
                    modifier = Modifier.size(28.dp).testTag("archive_btn_${email.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = t("Arşivle"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("trash_btn_${email.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = t("Çöpe At"),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
