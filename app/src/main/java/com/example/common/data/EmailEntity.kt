package com.example.common.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.common.model.RuleConditionField
import com.example.common.model.SmartCategory

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val preview: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val isArchived: Boolean = false,
    val isTrash: Boolean = false,
    val categoryId: String = SmartCategory.PRIMARY.id,
    val labelsCsv: String = "", // Comma-separated labels e.g. "Acil,İş"
    val storageSizeKb: Long = 24,
    val hasAttachment: Boolean = false,
    val attachmentName: String? = null,
    val isMailingList: Boolean = false,
    val unsubscribeUrl: String? = null,
    val isUnsubscribed: Boolean = false,
    val trackingPixelBlocked: Boolean = false,
    val accountEmail: String = "",
    val folderName: String = "INBOX",
    val serverUid: Long = 0L,
    val messageId: String? = null
)

@Entity(tableName = "smart_rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val conditionField: String, // "SENDER", "SUBJECT", "BODY"
    val keyword: String,
    val targetCategoryId: String? = null,
    val targetLabel: String? = null,
    val autoArchive: Boolean = false,
    val isEnabled: Boolean = true
)

@Entity(tableName = "custom_labels")
data class CustomLabelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val colorHex: Long
)
