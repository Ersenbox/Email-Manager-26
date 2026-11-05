package com.example.common.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE isTrash = 0 ORDER BY timestamp DESC")
    fun getAllActiveEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE isTrash = 1 ORDER BY timestamp DESC")
    fun getTrashEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE folderName = :folderName AND isTrash = 0 ORDER BY timestamp DESC")
    fun getEmailsByFolder(folderName: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE senderEmail = :senderEmail")
    suspend fun getEmailsBySender(senderEmail: String): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE accountEmail = :accountEmail")
    suspend fun getEmailsByAccount(accountEmail: String): List<EmailEntity>

    @Query("DELETE FROM emails WHERE id = :id")
    suspend fun deleteEmailById(id: Long)

    @Query("SELECT * FROM emails WHERE id = :id")
    suspend fun getEmailById(id: Long): EmailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity): Long

    @Update
    suspend fun updateEmail(email: EmailEntity)

    @Query("UPDATE emails SET isStarred = NOT isStarred WHERE id = :id")
    suspend fun toggleStar(id: Long)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: Long, isRead: Boolean)

    @Query("UPDATE emails SET isTrash = 1 WHERE id = :id")
    suspend fun moveToTrash(id: Long)

    @Query("UPDATE emails SET isTrash = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("UPDATE emails SET isTrash = 1 WHERE id IN (:ids)")
    suspend fun moveToTrashBatch(ids: List<Long>)

    @Query("UPDATE emails SET isRead = :isRead WHERE id IN (:ids)")
    suspend fun setReadBatch(ids: List<Long>, isRead: Boolean)

    @Query("SELECT * FROM emails WHERE id IN (:ids)")
    suspend fun getEmailsByIds(ids: List<Long>): List<EmailEntity>

    @Query("DELETE FROM emails WHERE id IN (:ids)")
    suspend fun deletePermanentlyBatch(ids: List<Long>)

    @Query("UPDATE emails SET isArchived = 1 WHERE id = :id")
    suspend fun archiveEmail(id: Long)

    @Query("UPDATE emails SET isUnsubscribed = 1 WHERE senderEmail = :senderEmail")
    suspend fun unsubscribeSender(senderEmail: String)

    @Query("UPDATE emails SET isTrash = 1 WHERE senderEmail = :senderEmail")
    suspend fun bulkTrashSender(senderEmail: String): Int

    @Query("DELETE FROM emails WHERE isTrash = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM emails WHERE senderEmail = :senderEmail")
    suspend fun permanentlyDeleteSender(senderEmail: String): Int

    @Query("DELETE FROM emails WHERE accountEmail = :accountEmail")
    suspend fun deleteEmailsByAccount(accountEmail: String): Int

    @Query("SELECT COUNT(*) FROM emails")
    suspend fun getEmailCount(): Int

    // Rules
    @Query("SELECT COUNT(*) FROM smart_rules")
    suspend fun getRuleCount(): Int

    @Query("SELECT * FROM smart_rules")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM smart_rules WHERE isEnabled = 1")
    suspend fun getEnabledRulesList(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Query("UPDATE smart_rules SET isEnabled = NOT isEnabled WHERE id = :id")
    suspend fun toggleRule(id: Long)

    @Query("DELETE FROM smart_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    // Custom Labels
    @Query("SELECT * FROM custom_labels")
    fun getAllLabels(): Flow<List<CustomLabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: CustomLabelEntity)

    @Query("DELETE FROM custom_labels WHERE id = :id")
    suspend fun deleteLabel(id: String)
}
