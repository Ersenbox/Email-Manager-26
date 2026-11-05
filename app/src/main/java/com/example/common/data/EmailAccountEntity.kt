package com.example.common.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "email_accounts")
data class EmailAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountName: String,
    val emailAddress: String,
    val password: String, // App Password or IMAP/SMTP password
    val imapHost: String = "imap.gmail.com",
    val imapPort: Int = 993,
    val imapSsl: Boolean = true,
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 465,
    val smtpSsl: Boolean = true,
    val isActive: Boolean = true,
    val lastSyncTime: Long = 0L,
    val providerType: String = "GMAIL" // GMAIL, OUTLOOK, YAHOO, CUSTOM
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM email_accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<EmailAccountEntity>>

    @Query("SELECT * FROM email_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccount(): EmailAccountEntity?

    @Query("SELECT * FROM email_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): EmailAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: EmailAccountEntity): Long

    @Update
    suspend fun updateAccount(account: EmailAccountEntity)

    @Delete
    suspend fun deleteAccount(account: EmailAccountEntity)

    @Query("UPDATE email_accounts SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActiveAccount(id: Long)

    @Query("UPDATE email_accounts SET lastSyncTime = :time WHERE id = :id")
    suspend fun updateLastSyncTime(id: Long, time: Long)

    @Query("SELECT COUNT(*) FROM email_accounts")
    suspend fun getAccountCount(): Int

    @Query("SELECT * FROM email_accounts ORDER BY id ASC")
    suspend fun getAllAccountsList(): List<EmailAccountEntity>
}
