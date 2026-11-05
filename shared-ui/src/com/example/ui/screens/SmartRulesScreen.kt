package com.example.ui.screens

import com.example.common.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.common.data.CustomLabelEntity
import com.example.common.data.RuleEntity
import com.example.common.model.RuleConditionField
import com.example.common.model.SmartCategory
import com.example.ui.viewmodel.EmailManagerViewModel

@Composable
fun SmartRulesScreen(
    viewModel: EmailManagerViewModel,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val customLabels by viewModel.customLabels.collectAsStateWithLifecycle()
    val platformInfo = remember { viewModel.bridge.getPlatformInfo() }

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<RuleEntity?>(null) }
    var showAddLabelDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Architecture & Platform Bridge Overview (Kullanıcı Şartı 1, 2, 4)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("architecture_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = t("Kurallar nasıl çalışır?"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = t("Kurallar eşitlenen tüm postalara uygulanır ve kategori veya etiket ekler. Gmail hesabınızdaki postalar değişmez."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = t("Aktif: {0}", platformInfo.platformType.displayName),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = t("Kurallar sadece bu uygulamada çalışır"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Smart Automation Rules
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("Otomasyon ve Filtreleme Kuralları ({0})", rules.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = { showAddRuleDialog = true },
                    modifier = Modifier.height(34.dp).testTag("add_rule_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(t("Kural Ekle"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                onToggle = { viewModel.toggleRule(rule.id) },
                onDelete = { viewModel.deleteRule(rule.id) },
                onEdit = { ruleToEdit = rule }
            )
        }

        // Section: Custom Labels
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("Özel Etiketler ({0})", customLabels.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = { showAddLabelDialog = true },
                    modifier = Modifier.height(34.dp).testTag("add_label_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(t("Etiket Ekle"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (lbl in customLabels) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("label_card_${lbl.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(lbl.colorHex))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "#${lbl.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteLabel(lbl.id) },
                                modifier = Modifier.size(28.dp).testTag("delete_label_${lbl.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = t("Sil"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            customLabels = customLabels,
            onDismiss = { showAddRuleDialog = false },
            onSave = { rule ->
                viewModel.addRule(rule)
                showAddRuleDialog = false
            }
        )
    }

    ruleToEdit?.let { rule ->
        AddRuleDialog(
            customLabels = customLabels,
            initialRule = rule,
            onDismiss = { ruleToEdit = null },
            onSave = { updated ->
                viewModel.updateRule(updated)
                ruleToEdit = null
            }
        )
    }

    if (showAddLabelDialog) {
        AddLabelDialog(
            onDismiss = { showAddLabelDialog = false },
            onSave = { label ->
                viewModel.addCustomLabel(label)
                showAddLabelDialog = false
            }
        )
    }
}

private fun ruleFieldTitle(field: String): String = when (field.uppercase()) {
    "SENDER" -> t("Gönderen")
    "BODY" -> t("İçerik")
    else -> t("Konu")
}

@Composable
fun RuleCard(
    rule: RuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("rule_card_${rule.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("{0} içinde şunlardan biri varsa: {1}", ruleFieldTitle(rule.conditionField), rule.keyword),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("rule_toggle_${rule.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (rule.targetCategoryId != null) {
                        val cat = SmartCategory.fromId(rule.targetCategoryId!!)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(cat.colorHex).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = t("Kategori: {0}", t(cat.titleTr)),
                                fontSize = 10.sp,
                                color = Color(cat.colorHex),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (rule.targetLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = t("Etiket: #{0}", rule.targetLabel),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (rule.autoArchive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = t("Otomatik Arşivle"),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp).testTag("edit_rule_btn_${rule.id}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = t("Kuralı Düzenle"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("delete_rule_btn_${rule.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = t("Kuralı Sil"),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRuleDialog(
    customLabels: List<CustomLabelEntity>,
    onDismiss: () -> Unit,
    onSave: (RuleEntity) -> Unit,
    initialRule: RuleEntity? = null
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var conditionField by remember { mutableStateOf(initialRule?.conditionField ?: "SUBJECT") }
    var keyword by remember { mutableStateOf(initialRule?.keyword ?: "") }
    var selectedCategory by remember {
        mutableStateOf(initialRule?.targetCategoryId?.let { SmartCategory.fromId(it) } ?: SmartCategory.BILLS)
    }
    var selectedLabel by remember { mutableStateOf(initialRule?.targetLabel ?: customLabels.firstOrNull()?.name ?: "") }
    var autoArchive by remember { mutableStateOf(initialRule?.autoArchive ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule != null) t("Kuralı Düzenle") else t("Yeni Akıllı Filtre Kuralı Oluştur")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("Kural Başlığı")) },
                    placeholder = { Text(t("Örn: Dekontları Finans Yap")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_rule_name")
                )

                // Condition field
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SUBJECT" to t("Konu"), "SENDER" to t("Gönderen"), "BODY" to t("İçerik")).forEach { (code, label) ->
                        val isSel = conditionField == code
                        Surface(
                            onClick = { conditionField = code },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text(t("Anahtar kelimeler (virgülle ayırın)")) },
                    placeholder = { Text(t("Örn: fatura, dekont, kargo")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_rule_keyword")
                )

                Text(t("Hedef Kategori:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(SmartCategory.BILLS, SmartCategory.SUBSCRIPTIONS, SmartCategory.PACKAGES, SmartCategory.TRAVEL, SmartCategory.WORK, SmartCategory.PRIMARY).forEach { cat ->
                        val isSel = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) Color(cat.colorHex) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = t(cat.titleTr),
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(t("Etiket:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    customLabels.forEach { label ->
                        val isSel = selectedLabel == label.name
                        Surface(
                            onClick = { selectedLabel = if (isSel) "" else label.name },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) Color(label.colorHex) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "#${label.name}",
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoArchive,
                        onCheckedChange = { autoArchive = it },
                        modifier = Modifier.testTag("add_rule_auto_archive")
                    )
                    Text(t("Sadece bu uygulamada arşivle (Gmail değişmez)"), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && keyword.isNotBlank()) {
                        onSave(
                            RuleEntity(
                                id = initialRule?.id ?: 0L,
                                name = name,
                                conditionField = conditionField,
                                keyword = keyword,
                                targetCategoryId = selectedCategory.id,
                                targetLabel = selectedLabel.ifBlank { null },
                                autoArchive = autoArchive,
                                isEnabled = initialRule?.isEnabled ?: true
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_save_rule")
            ) {
                Text(t("Kuralı Kaydet"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("İptal")) }
        }
    )
}

@Composable
fun AddLabelDialog(
    onDismiss: () -> Unit,
    onSave: (CustomLabelEntity) -> Unit
) {
    var labelName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFF2563EB) }

    val presetColors = listOf(
        0xFF2563EB, // Blue
        0xFF10B981, // Emerald
        0xFFEF4444, // Red
        0xFFF59E0B, // Amber
        0xFF8B5CF6, // Purple
        0xFF06B6D4  // Cyan
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Yeni Özel Etiket Ekle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = labelName,
                    onValueChange = { labelName = it },
                    label = { Text(t("Etiket Adı")) },
                    placeholder = { Text(t("Örn: Muhasebe, VIP, Yöneticiler")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_label_name")
                )

                Text(t("Renk Seçin:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in presetColors) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .padding(if (selectedColor == c) 2.dp else 0.dp)
                        ) {
                            Surface(
                                onClick = { selectedColor = c },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (labelName.isNotBlank()) {
                        val id = labelName.lowercase().replace(" ", "_")
                        onSave(CustomLabelEntity(id = id, name = labelName.trim(), colorHex = selectedColor))
                    }
                },
                modifier = Modifier.testTag("confirm_save_label")
            ) {
                Text(t("Etiketi Ekle"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("İptal")) }
        }
    )
}
