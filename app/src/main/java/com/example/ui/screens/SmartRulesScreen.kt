package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
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
                            text = "Cross-Platform Çekirdek ve Köprü Mimarisi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Ortak Veri Modelleri, Akıllı Filtreleme Motoru ve UI tek bir merkezden yönetilmektedir. Donanım özellikleri PlatformBridge arabirimiyle bağlanır.",
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
                                text = "Aktif: ${platformInfo.platformType.displayName}",
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
                                text = "iOS / Web Köprüsü Hazır",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF059669),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Otomasyon ve Filtreleme Kuralları (${rules.size})",
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
                    Text("Kural Ekle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                onToggle = { viewModel.toggleRule(rule.id) },
                onDelete = { viewModel.deleteRule(rule.id) }
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
                        text = "Özel Etiketler (${customLabels.size})",
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
                    Text("Etiket Ekle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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

@Composable
fun RuleCard(
    rule: RuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
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
                        text = "Eğer ${rule.conditionField} içeriğinde '${rule.keyword}' varsa:",
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
                        val cat = SmartCategory.fromId(rule.targetCategoryId)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(cat.colorHex).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Kategori: ${cat.titleTr}",
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
                                text = "Etiket: #${rule.targetLabel}",
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
                                text = "Otomatik Arşivle",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("delete_rule_btn_${rule.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Kuralı Sil",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    customLabels: List<CustomLabelEntity>,
    onDismiss: () -> Unit,
    onSave: (RuleEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var conditionField by remember { mutableStateOf("SUBJECT") }
    var keyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SmartCategory.BILLS) }
    var selectedLabel by remember { mutableStateOf(customLabels.firstOrNull()?.name ?: "") }
    var autoArchive by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Akıllı Filtre Kuralı Oluştur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kural Başlığı") },
                    placeholder = { Text("Örn: Dekontları Finans Yap") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_rule_name")
                )

                // Condition field
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SUBJECT" to "Konu", "SENDER" to "Gönderen", "BODY" to "İçerik").forEach { (code, label) ->
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
                    label = { Text("Aranacak Anahtar Kelime") },
                    placeholder = { Text("Örn: fatura, dekont, kargo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_rule_keyword")
                )

                Text("Hedef Kategori:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(SmartCategory.BILLS, SmartCategory.SUBSCRIPTIONS, SmartCategory.PACKAGES, SmartCategory.WORK).forEach { cat ->
                        val isSel = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) Color(cat.colorHex) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = cat.titleTr,
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
                    Text("Gelen kutusuna düşmeden otomatik arşivle", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && keyword.isNotBlank()) {
                        onSave(
                            RuleEntity(
                                name = name,
                                conditionField = conditionField,
                                keyword = keyword,
                                targetCategoryId = selectedCategory.id,
                                targetLabel = selectedLabel.ifBlank { null },
                                autoArchive = autoArchive,
                                isEnabled = true
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_save_rule")
            ) {
                Text("Kuralı Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
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
        title = { Text("Yeni Özel Etiket Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = labelName,
                    onValueChange = { labelName = it },
                    label = { Text("Etiket Adı") },
                    placeholder = { Text("Örn: Muhasebe, VIP, Yöneticiler") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_label_name")
                )

                Text("Renk Seçin:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text("Etiketi Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
