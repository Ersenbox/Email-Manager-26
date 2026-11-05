package com.example.ui.components

import com.example.common.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.common.data.EmailEntity

@Composable
fun EmailReplyDialog(
    targetEmail: EmailEntity,
    activeSenderAccountEmail: String?,
    onDismiss: () -> Unit,
    onOpenAccounts: () -> Unit = {},
    onSendReply: (to: String, subject: String, body: String, onComplete: (Boolean, String?) -> Unit) -> Unit
) {
    var toRecipient by remember { mutableStateOf(targetEmail.senderEmail) }
    var replySubject by remember {
        mutableStateOf(if (targetEmail.subject.startsWith("Re:", ignoreCase = true)) targetEmail.subject else "Re: ${targetEmail.subject}")
    }
    var replyBody by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val submitReply: () -> Unit = {
        if (toRecipient.isNotBlank() && replyBody.isNotBlank()) {
            isSending = true
            errorMessage = null
            val fullBody = buildString {
                append(replyBody)
                append(t("\n\n--- Orijinal İleti ---\nGönderen: "))
                append(targetEmail.senderName)
                append(" <")
                append(targetEmail.senderEmail)
                append(">\n")
                append(targetEmail.body)
            }
            onSendReply(toRecipient, replySubject, fullBody) { success, err ->
                isSending = false
                if (success) {
                    onDismiss()
                } else {
                    errorMessage = err ?: t("Gönderilemedi")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
                .testTag("reply_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = t("E-postayı Yanıtla"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeSenderAccountEmail?.let { t("Gönderen: {0}", it) } ?: t("Aktif e-posta hesabınızdan gönderilecek"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Klavye alttaki düğmeyi kapatsa da gönderme her zaman erişilebilir kalır
                        IconButton(
                            onClick = submitReply,
                            enabled = !isSending && toRecipient.isNotBlank() && replyBody.isNotBlank(),
                            modifier = Modifier.testTag("send_reply_top_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = t("Yanıtı Gönder"))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = t("Kapat"))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recipient To
                OutlinedTextField(
                    value = toRecipient,
                    onValueChange = { toRecipient = it },
                    label = { Text(t("Kime")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reply_to_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subject
                OutlinedTextField(
                    value = replySubject,
                    onValueChange = { replySubject = it },
                    label = { Text(t("Konu")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reply_subject_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Smart Quick Replies (1-Tap Templates)
                Text(
                    text = t("Akıllı Hızlı Yanıtlar:"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val suggestions = listOf(
                        t("Teşekkür ederim, aldım! 👍"),
                        t("İnceleyip en kısa sürede dönüş yapacağım. ⏳"),
                        t("Onaylıyorum, harika. ✅"),
                        t("Toplantıya katılım sağlayacağım. 📅"),
                        t("Bilgilendirme için teşekkürler. 🤝")
                    )
                    for (sug in suggestions) {
                        SuggestionChip(
                            onClick = {
                                replyBody = if (replyBody.isBlank()) sug else "$replyBody\n$sug"
                            },
                            label = { Text(sug, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reply Body Input
                OutlinedTextField(
                    value = replyBody,
                    onValueChange = { replyBody = it },
                    label = { Text(t("Yanıtınız...")) },
                    placeholder = { Text(t("Buraya yanıtınızı yazın. Gerçek SMTP ile iletilecektir.")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("reply_body_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Original Quoted Message Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = t("Orijinal İleti Özeti ({0}):", targetEmail.senderName),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = targetEmail.body.take(250) + if (targetEmail.body.length > 250) "..." else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 4
                        )
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
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

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSending
                    ) {
                        Text(t("İptal"))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            submitReply()
                        },
                        enabled = !isSending && toRecipient.isNotBlank() && replyBody.isNotBlank(),
                        modifier = Modifier.testTag("send_reply_button")
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
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(t("Yanıtı Gönder"))
                        }
                    }
                }
            }
        }
    }
}
