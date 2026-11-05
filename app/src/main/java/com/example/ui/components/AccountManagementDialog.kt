package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.common.data.EmailAccountEntity
import com.example.common.service.MailService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountManagementDialog(
    accounts: List<EmailAccountEntity>,
    activeAccount: EmailAccountEntity?,
    onDismiss: () -> Unit,
    onSetActive: (Long) -> Unit,
    onDeleteAccount: (EmailAccountEntity) -> Unit,
    onSaveAccount: (EmailAccountEntity) -> Unit,
    onTestConnection: (EmailAccountEntity, (Boolean, String) -> Unit) -> Unit,
    onSyncNow: () -> Unit,
    onOpenGoogleAppPasswords: () -> Unit = {}
) {
    var showAddAccountForm by remember { mutableStateOf(accounts.isEmpty()) }
    var editingAccount by remember { mutableStateOf<EmailAccountEntity?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .testTag("account_management_dialog"),
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "E-posta Hesapları",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gerçek IMAP/SMTP ile senkronizasyon",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_accounts_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Existing Accounts List
                if (accounts.isNotEmpty()) {
                    Text(
                        text = "Kayıtlı Hesaplar (${accounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    accounts.forEach { account ->
                        val isActive = account.id == activeAccount?.id || account.isActive
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSetActive(account.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = account.accountName.ifBlank { account.emailAddress },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "AKTİF",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = account.emailAddress,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val lastSyncStr = if (account.lastSyncTime > 0) {
                                        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(account.lastSyncTime))
                                    } else "Henüz senkronize edilmedi"
                                    Text(
                                        text = "Son Eşitleme: $lastSyncStr",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    if (account.emailAddress.endsWith("@gmail.com", ignoreCase = true)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clickable { editingAccount = account }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Gmail: Google normal şifreyi engeller. 16 haneli Uygulama Şifresini düzenlemek için dokunun.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingAccount = account },
                                        modifier = Modifier.testTag("edit_account_${account.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Şifreyi Düzenle",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteAccount(account) },
                                        modifier = Modifier.testTag("delete_account_${account.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hesabı Sil",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { showAddAccountForm = !showAddAccountForm },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (showAddAccountForm) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showAddAccountForm) "Formu Gizle" else "Başka Hesap Ekle")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSyncNow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Şimdi Eşitle")
                        }
                    }
                }

                // Add Account Form
                AnimatedVisibility(visible = showAddAccountForm || accounts.isEmpty()) {
                    AddAccountFormView(
                        onCancel = { showAddAccountForm = false },
                        onSave = { newAccount ->
                            onSaveAccount(newAccount)
                            showAddAccountForm = false
                        },
                        onTest = onTestConnection,
                        onOpenGoogleAppPasswords = onOpenGoogleAppPasswords
                    )
                }
            }
        }
    }

    // Edit Account Dialog
    if (editingAccount != null) {
        EditAccountDialog(
            account = editingAccount!!,
            onDismiss = { editingAccount = null },
            onOpenGoogleAppPasswords = onOpenGoogleAppPasswords,
            onSave = { updatedAccount ->
                onSaveAccount(updatedAccount)
                editingAccount = null
                onSyncNow()
            },
            onTest = onTestConnection
        )
    }
}

@Composable
private fun EditAccountDialog(
    account: EmailAccountEntity,
    onDismiss: () -> Unit,
    onOpenGoogleAppPasswords: () -> Unit,
    onSave: (EmailAccountEntity) -> Unit,
    onTest: (EmailAccountEntity, (Boolean, String) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf(account.password) }
    var showPassword by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    val isGmail = account.emailAddress.endsWith("@gmail.com", ignoreCase = true) ||
            account.imapHost.contains("gmail", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Hesap Şifresini Güncelle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = account.emailAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isGmail) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Uygulama Şifresi Zorunludur",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Google normal hesap şifrenizle girişi engellemektedir. 16 haneli Uygulama Şifresi (App Password) almanız gerekir:",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = onOpenGoogleAppPasswords,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google'dan Şifre Al (1-Tık)", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isGmail) "16 Haneli Uygulama Şifresi" else "Hesap Şifresi") },
                    placeholder = { Text(if (isGmail) "örnek: abcd efgh ijkl mnop" else "şifreniz") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true
                )

                if (testResultMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isTestSuccess) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isTestSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = testResultMessage!!,
                                fontSize = 11.sp,
                                color = if (isTestSuccess) Color(0xFF166534) else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResultMessage = null
                        val updatedAccount = account.copy(password = MailService.cleanPassword(password))
                        onTest(updatedAccount) { success, msg ->
                            isTesting = false
                            isTestSuccess = success
                            testResultMessage = msg
                        }
                    },
                    enabled = !isTesting && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Ediliyor...")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bağlantıyı Test Et")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanPass = MailService.cleanPassword(password)
                    onSave(account.copy(password = cleanPass))
                    onDismiss()
                },
                enabled = password.isNotBlank()
            ) {
                Text("Kaydet & Senkronize Et")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun AddAccountFormView(
    onCancel: () -> Unit,
    onSave: (EmailAccountEntity) -> Unit,
    onTest: (EmailAccountEntity, (Boolean, String) -> Unit) -> Unit,
    onOpenGoogleAppPasswords: () -> Unit = {}
) {
    var providerType by remember { mutableStateOf("GMAIL") }
    var accountName by remember { mutableStateOf("Kişisel E-posta") }
    var emailAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var imapHost by remember { mutableStateOf("imap.gmail.com") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("smtp.gmail.com") }
    var smtpPort by remember { mutableStateOf("465") }

    var isTesting by remember { mutableStateOf(false) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    fun applyProvider(provider: String) {
        providerType = provider
        when (provider) {
            "GMAIL" -> {
                imapHost = "imap.gmail.com"
                imapPort = "993"
                smtpHost = "smtp.gmail.com"
                smtpPort = "465"
            }
            "OUTLOOK" -> {
                imapHost = "outlook.office365.com"
                imapPort = "993"
                smtpHost = "smtp.office365.com"
                smtpPort = "587"
            }
            "YAHOO" -> {
                imapHost = "imap.mail.yahoo.com"
                imapPort = "993"
                smtpHost = "smtp.mail.yahoo.com"
                smtpPort = "465"
            }
            "YANDEX" -> {
                imapHost = "imap.yandex.com"
                imapPort = "993"
                smtpHost = "smtp.yandex.com"
                smtpPort = "465"
            }
            "CUSTOM" -> {}
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Yeni Gerçek Hesap Ekle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Gmail, Outlook, Yahoo veya özel IMAP/SMTP sunucunuzu bağlayın.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Provider Quick Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("GMAIL", "OUTLOOK", "YAHOO", "YANDEX", "CUSTOM").forEach { prov ->
                    FilterChip(
                        selected = providerType == prov,
                        onClick = { applyProvider(prov) },
                        label = { Text(prov, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Hesap Adı / Tanımı") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_name_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = { Text("E-posta Adresi") },
                placeholder = { Text("örnek: bakiciersen@gmail.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_email_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre veya Uygulama Şifresi") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_password_input"),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Şifreyi Göster/Gizle"
                        )
                    }
                },
                singleLine = true
            )

            // Gmail Notice Card
            if (providerType == "GMAIL" || emailAddress.endsWith("@gmail.com", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gmail 16 Haneli Uygulama Şifresi Rehberi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Google, güvenlik sebebiyle normal Gmail şifrenizle e-posta erişimini engeller. E-postalarınızı yönetebilmek için 16 haneli 'Uygulama Şifresi' (App Password) almalısınız:",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onOpenGoogleAppPasswords,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Google Uygulama Şifresi Sayfasını Aç (1-Tık)", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Adımlar: Sayfada 'Uygulama adı'na Email-Manager 26 yazıp 'Oluştur'a basın. Verilen 16 harfli şifreyi yukarıdaki kutuya yapıştırın.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advanced IMAP / SMTP settings toggle
            var showAdvanced by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showAdvanced) "Sunucu Ayarlarını Gizle" else "Gelişmiş Sunucu Ayarları (IMAP/SMTP)")
            }

            if (showAdvanced) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = imapHost,
                        onValueChange = { imapHost = it },
                        label = { Text("IMAP Sunucu") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = imapPort,
                        onValueChange = { imapPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP Sunucu") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = smtpPort,
                        onValueChange = { smtpPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // Connection Test Result message
            testResultMessage?.let { msg ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTestSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isTestSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = if (isTestSuccess) Color(0xFF047857) else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val cleanPass = MailService.cleanPassword(password)
                        val acc = EmailAccountEntity(
                            accountName = accountName,
                            emailAddress = emailAddress.trim(),
                            password = cleanPass,
                            imapHost = imapHost.trim(),
                            imapPort = imapPort.toIntOrNull() ?: 993,
                            smtpHost = smtpHost.trim(),
                            smtpPort = smtpPort.toIntOrNull() ?: 465,
                            providerType = providerType
                        )
                        isTesting = true
                        testResultMessage = null
                        onTest(acc) { success, message ->
                            isTesting = false
                            isTestSuccess = success
                            testResultMessage = message
                        }
                    },
                    enabled = !isTesting && emailAddress.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.testTag("test_connection_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Ediliyor...")
                    } else {
                        Text("Bağlantıyı Test Et")
                    }
                }

                Button(
                    onClick = {
                        val cleanPass = MailService.cleanPassword(password)
                        val acc = EmailAccountEntity(
                            accountName = accountName.ifBlank { emailAddress.substringBefore("@") },
                            emailAddress = emailAddress.trim(),
                            password = cleanPass,
                            imapHost = imapHost.trim(),
                            imapPort = imapPort.toIntOrNull() ?: 993,
                            smtpHost = smtpHost.trim(),
                            smtpPort = smtpPort.toIntOrNull() ?: 465,
                            providerType = providerType,
                            isActive = true
                        )
                        onSave(acc)
                    },
                    enabled = emailAddress.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.testTag("save_account_button")
                ) {
                    Text("Kaydet & Başla")
                }
            }
        }
    }
}
