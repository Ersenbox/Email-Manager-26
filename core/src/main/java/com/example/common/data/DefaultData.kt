package com.example.common.data

import com.example.common.model.SmartCategory

/** Android ve masaüstünde ortak ilk veriler. */
object DefaultData {
    suspend fun seed(dao: EmailDao) {

        // Seed default rules only if not already seeded
        if (dao.getRuleCount() == 0) {
            val defaultRules = listOf(
                RuleEntity(
                    name = "Faturaları Otomatik Etiketle",
                    conditionField = "SUBJECT",
                    keyword = "fatura, rechnung, invoice, facture",
                    targetCategoryId = SmartCategory.BILLS.id,
                    targetLabel = "Finans",
                    autoArchive = false,
                    isEnabled = true
                ),
                RuleEntity(
                    name = "Kargo Bildirimlerini Grupla",
                    conditionField = "SUBJECT",
                    keyword = "kargo, paket, lieferung, versand, shipping, colis",
                    targetCategoryId = SmartCategory.PACKAGES.id,
                    targetLabel = "Kargo",
                    autoArchive = false,
                    isEnabled = true
                ),
                RuleEntity(
                    name = "Bültenleri Aboneliklere Aktar",
                    conditionField = "BODY",
                    keyword = "unsubscribe, abbestellen, abmelden, désabonner, abonelikten çık",
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
