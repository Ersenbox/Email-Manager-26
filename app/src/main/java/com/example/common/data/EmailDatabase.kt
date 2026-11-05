package com.example.common.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.common.model.SmartCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [EmailEntity::class, RuleEntity::class, CustomLabelEntity::class, EmailAccountEntity::class],
    version = 3,
    exportSchema = false
)
abstract class EmailDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: EmailDatabase? = null

        fun getInstance(context: Context): EmailDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EmailDatabase::class.java,
                    "email_manager_26.db"
                ).fallbackToDestructiveMigration()
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

    suspend fun seedInitialData() {
        val dao = emailDao()

        // Seed default rules only if not already seeded
        if (dao.getRuleCount() == 0) {
            val defaultRules = listOf(
                RuleEntity(
                    name = "Faturaları Otomatik Etiketle",
                    conditionField = "SUBJECT",
                    keyword = "fatura",
                    targetCategoryId = SmartCategory.BILLS.id,
                    targetLabel = "Finans",
                    autoArchive = false,
                    isEnabled = true
                ),
                RuleEntity(
                    name = "Kargo Bildirimlerini Grupla",
                    conditionField = "SUBJECT",
                    keyword = "kargo",
                    targetCategoryId = SmartCategory.PACKAGES.id,
                    targetLabel = "Kargo",
                    autoArchive = false,
                    isEnabled = true
                ),
                RuleEntity(
                    name = "Bültenleri Aboneliklere Aktar",
                    conditionField = "BODY",
                    keyword = "unsubscribe",
                    targetCategoryId = SmartCategory.SUBSCRIPTIONS.id,
                    targetLabel = "Bülten",
                    autoArchive = false,
                    isEnabled = true
                )
            )
            for (r in defaultRules) {
                dao.insertRule(r)
            }
        }

        // Seed custom labels
        val defaultLabels = listOf(
            CustomLabelEntity("is", "İş", 0xFF2563EB),
            CustomLabelEntity("finans", "Finans", 0xFF10B981),
            CustomLabelEntity("acil", "Acil", 0xFFEF4444),
            CustomLabelEntity("kisisel", "Kişisel", 0xFF8B5CF6),
            CustomLabelEntity("bulten", "Bülten", 0xFFF59E0B)
        )
        for (l in defaultLabels) {
            dao.insertLabel(l)
        }
    }
}
