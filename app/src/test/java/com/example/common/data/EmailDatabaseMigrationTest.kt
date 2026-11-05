package com.example.common.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.3 veritabanı gerçek şemasıyla kurulur, Room açılışında geçiş ve şema doğrulanır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmailDatabaseMigrationTest {

    private val schemaFile = listOf("schemas", "app/schemas")
        .map { File(it, "com.example.common.data.EmailDatabase/3.json") }
        .first { it.exists() }

    private fun createVersion3(file: File) {
        val database = JSONObject(schemaFile.readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        val setup = database.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) {
            db.execSQL(setup.getString(i))
        }
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
            arrayOf(database.getString("identityHash"))
        )
        val rule = "INSERT INTO smart_rules (name, conditionField, keyword, targetCategoryId, targetLabel, autoArchive, isEnabled) " +
            "VALUES ('Faturaları Otomatik Etiketle', 'SUBJECT', 'fatura', 'bills', 'Finans', 0, 1)"
        db.execSQL(rule)
        db.execSQL(rule)
        db.execSQL(
            "INSERT INTO smart_rules (name, conditionField, keyword, targetCategoryId, targetLabel, autoArchive, isEnabled) " +
                "VALUES ('Benim', 'BODY', 'ozel', NULL, NULL, 0, 1)"
        )
        db.execSQL(
            "INSERT INTO email_accounts (accountName, emailAddress, password, imapHost, imapPort, imapSsl, smtpHost, smtpPort, smtpSsl, isActive, lastSyncTime, providerType) " +
                "VALUES ('A', 'a@gmail.com', 'enc1:x', 'imap.gmail.com', 993, 1, 'smtp.gmail.com', 465, 1, 1, 5, 'GMAIL')"
        )
        db.version = 3
        db.close()
    }

    @Test
    fun `version 3 data survives and duplicate rules are removed`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration_test.db").apply { parentFile?.mkdirs(); delete() }
        createVersion3(file)

        val room = Room.databaseBuilder(context, EmailDatabase::class.java, file.absolutePath)
            .addMigrations(EmailDatabase.MIGRATION_3_4, EmailDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val rules = room.emailDao().getEnabledRulesList()
        val account = room.accountDao().getAllAccountsList().single()
        room.close()

        assertEquals(2, rules.size)
        assertEquals("fatura, rechnung, invoice, facture", rules.first { it.name.startsWith("Fatura") }.keyword)
        assertTrue(rules.any { it.keyword == "ozel" })
        assertEquals("a@gmail.com", account.emailAddress)
        assertEquals(0L, account.verifiedAt)
    }
}
