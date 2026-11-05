package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.common.data.EmailAccountEntity
import com.example.common.i18n.I18n
import com.example.common.i18n.t
import com.example.common.settings.AppSettings
import com.example.common.settings.ThemeMode
import com.example.ui.platform.rememberBackgroundSettingApplier
import com.example.ui.platform.rememberImagePicker
import com.example.ui.viewmodel.EmailManagerViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsDialog(
    viewModel: EmailManagerViewModel,
    account: EmailAccountEntity?,
    onDismiss: () -> Unit
) {
    val themeMode by AppSettings.themeMode.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ayarlar"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectionTitle(t("Dil"))
                I18n.languages.forEach { language ->
                    ChoiceRow(
                        title = language.title,
                        selected = I18n.language == language.code,
                        tag = "language_${language.code}",
                        onClick = { AppSettings.setLanguage(language.code) }
                    )
                }

                SectionTitle(t("Görünüm"))
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = t(mode.titleTr),
                        selected = themeMode == mode,
                        tag = "theme_${mode.id}",
                        onClick = { AppSettings.setThemeMode(mode) }
                    )
                }

                SectionTitle(t("Otomasyon"))
                var autoLabels by remember { mutableStateOf(AppSettings.autoLabels()) }
                SwitchRow(t("Postaları Gmail'de otomatik etiketle"), autoLabels, "setting_auto_labels") {
                    autoLabels = it
                    AppSettings.setAutoLabels(it)
                }
                var keepDeleted by remember { mutableStateOf(AppSettings.keepDeletedEmails()) }
                SwitchRow(t("Silinenleri siz silene kadar Silinecekler'de tut"), keepDeleted, "setting_keep_deleted") {
                    keepDeleted = it
                    AppSettings.setKeepDeletedEmails(it)
                }
                var spamCleanup by remember { mutableStateOf(AppSettings.spamAutoDeleteDays() > 0) }
                SwitchRow(t("7 günden eski spam'i kalıcı sil (geri alınamaz)"), spamCleanup, "setting_spam_cleanup") {
                    spamCleanup = it
                    AppSettings.setSpamAutoDeleteDays(if (it) AppSettings.SPAM_DAYS else 0)
                }

                var adCleanup by remember { mutableStateOf(AppSettings.adCleanupDays() > 0) }
                SwitchRow(t("30 günden eski reklamları Silinecekler'e taşı"), adCleanup, "setting_ad_cleanup") {
                    adCleanup = it
                    AppSettings.setAdCleanupDays(if (it) AppSettings.AD_DAYS else 0)
                }
                val applyBackgroundSetting = rememberBackgroundSettingApplier()
                var background by remember { mutableStateOf(AppSettings.backgroundSync()) }
                SwitchRow(t("Uygulama kapalıyken de eşitle"), background, "setting_background") {
                    background = it
                    AppSettings.setBackgroundSync(it)
                    applyBackgroundSetting()
                }
                var notify by remember { mutableStateOf(AppSettings.notifyImportant()) }
                SwitchRow(t("Önemli yeni postalarda bildirim göster"), notify, "setting_notify") {
                    notify = it
                    AppSettings.setNotifyImportant(it)
                }
                var weekly by remember { mutableStateOf(AppSettings.weeklyReport()) }
                SwitchRow(t("Haftalık temizlik raporu"), weekly, "setting_weekly") {
                    weekly = it
                    AppSettings.setWeeklyReport(it)
                }

                val blocked by viewModel.blockedSenders.collectAsState()
                if (blocked.isNotEmpty()) {
                    SectionTitle(t("Engellenen Gönderenler"))
                    blocked.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.senderEmail, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.unblockSender(entry.senderEmail) }) {
                                Text(t("Engeli Kaldır"))
                            }
                        }
                    }
                }

                if (account != null) {
                    SectionTitle(t("Hesap"))
                    AccountSection(viewModel, account)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings_close")) {
                Text(t("Kapat"))
            }
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag)
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(title)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag(tag)
    ) {
        Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccountSection(viewModel: EmailManagerViewModel, account: EmailAccountEntity) {
    val pickImage = rememberImagePicker { bytes -> viewModel.setAccountPhoto(account, bytes) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccountAvatar(account, 56.dp)
        Column {
            Text(account.emailAddress, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(verificationText(account), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    OutlinedButton(
        onClick = pickImage,
        modifier = Modifier.fillMaxWidth().testTag("pick_account_photo")
    ) {
        Text(t("Galeriden resim seç"))
    }

    if (account.photoPath != null) {
        TextButton(onClick = { viewModel.removeAccountPhoto(account) }, modifier = Modifier.fillMaxWidth()) {
            Text(t("Resmi kaldır"))
        }
    }

}

private fun verificationText(account: EmailAccountEntity): String {
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, I18n.locale)
    return when {
        account.verifiedAt > 0L -> t("Sunucu girişi doğrulandı: {0}", format.format(Date(account.verifiedAt)))
        else -> t("Henüz doğrulanmadı")
    }
}
