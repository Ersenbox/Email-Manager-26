package com.example.common.service

import java.util.Locale

/**
 * Klasör adı tanıma. Tam ad parçası karşılaştırılır; "Kabine" veya "Robin" gibi
 * adında "bin" geçen klasörler çöp kutusu sayılmaz.
 */
object MailFolderNames {

    private val trashLeafNames = setOf(
        "trash", "bin", "papierkorb", "çöp kutusu", "çöp",
        "deleted", "deleted items", "deleted messages",
        "gelöscht", "gelöschte elemente", "gelöschte objekte",
        "silinmiş öğeler", "silinenler"
    )

    private val archiveLeafNames = setOf(
        "archive", "archives", "archiv", "arşiv",
        "all mail", "alle nachrichten", "alle e-mails", "tüm postalar"
    )

    fun leafName(fullName: String): String =
        fullName.substringAfterLast('/').substringAfterLast('.').trim().lowercase(Locale.ROOT)

    /** Silinen postaların Gmail'in 30 günlük silmesinden etkilenmeden beklediği etiket. */
    const val HOLD_FOLDER = "EM26/Silinecekler"

    /** Uygulamanın oluşturduğu etiketlerin ortak öneki. */
    const val APP_LABEL_PREFIX = "EM26/"

    fun isHold(fullName: String): Boolean = fullName.equals(HOLD_FOLDER, ignoreCase = true)

    fun isTrash(fullName: String): Boolean = leafName(fullName) in trashLeafNames

    fun isArchive(fullName: String): Boolean = leafName(fullName) in archiveLeafNames
}
