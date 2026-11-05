package com.example.common.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [EmailEntity::class, RuleEntity::class, CustomLabelEntity::class, EmailAccountEntity::class, EmailFolderLink::class, SenderOverride::class],
    version = 5,
    exportSchema = true
)
abstract class EmailDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: EmailDatabase? = null

        // Varsayılan kuralların iki yerden aynı anda eklenmesini önler
        private val seedMutex = Mutex()

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `email_folder_links` (" +
                        "`accountEmail` TEXT NOT NULL, `folderName` TEXT NOT NULL, " +
                        "`emailId` INTEGER NOT NULL, `serverUid` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accountEmail`, `folderName`, `emailId`))"
                )
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN photoPath TEXT")
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN verifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN googleVerifiedAt INTEGER NOT NULL DEFAULT 0")
                // Birebir aynı kuralların fazla kopyaları kaldırılır, en eski kayıt kalır
                db.execSQL(
                    "DELETE FROM smart_rules WHERE id NOT IN (SELECT MIN(id) FROM smart_rules " +
                        "GROUP BY name, conditionField, keyword, targetCategoryId, targetLabel, autoArchive, isEnabled)"
                )
                // Değiştirilmemiş varsayılan kurallara diğer diller eklenir
                for ((name, oldKeyword, newKeyword) in DEFAULT_RULE_KEYWORDS) {
                    db.execSQL(
                        "UPDATE smart_rules SET keyword = ? WHERE name = ? AND keyword = ?",
                        arrayOf<Any>(newKeyword, name, oldKeyword)
                    )
                }
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emails ADD COLUMN autoFolder TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sender_overrides` (" +
                        "`senderEmail` TEXT NOT NULL, `folder` TEXT NOT NULL, PRIMARY KEY(`senderEmail`))"
                )
            }
        }

        private val DEFAULT_RULE_KEYWORDS = listOf(
            Triple("Faturaları Otomatik Etiketle", "fatura", "fatura, rechnung, invoice, facture"),
            Triple("Kargo Bildirimlerini Grupla", "kargo", "kargo, paket, lieferung, versand, shipping, colis"),
            Triple("Bültenleri Aboneliklere Aktar", "unsubscribe", "unsubscribe, abbestellen, abmelden, désabonner, abonelikten çık")
        )

        fun getInstance(context: Context): EmailDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EmailDatabase::class.java,
                    "email_manager_26.db"
                )
                // Şema değişirse Migration eklenmelidir; hesaplar ve postalar sessizce silinmez.
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed database in background
                        CoroutineScope(Dispatchers.IO).launch {
                            getInstance(context).seedInitialData()
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun seedInitialData() = seedMutex.withLock {
        DefaultData.seed(emailDao())
    }
}
