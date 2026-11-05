package com.example.desktop

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.common.bridge.HapticType
import com.example.common.bridge.PlatformBridge
import com.example.common.bridge.PlatformInfo
import com.example.common.bridge.SoundType
import com.example.common.data.AccountDao
import com.example.common.data.CustomLabelEntity
import com.example.common.data.DefaultData
import com.example.common.data.EmailAccountEntity
import com.example.common.data.EmailDao
import com.example.common.data.EmailEntity
import com.example.common.data.EmailFolderLink
import com.example.common.data.RuleEntity
import com.example.common.data.SenderOverride
import com.example.common.i18n.I18n
import com.example.common.model.PlatformType
import com.example.common.repository.EmailRepository
import com.example.common.security.CredentialCodec
import com.example.common.security.Credentials
import com.example.common.settings.AppSettings
import com.example.common.settings.SettingsStore
import com.sun.jna.platform.win32.Crypt32Util
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        EmailEntity::class, RuleEntity::class, CustomLabelEntity::class,
        EmailAccountEntity::class, EmailFolderLink::class, SenderOverride::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DesktopDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao
    abstract fun accountDao(): AccountDao
}

/** Şifreleri Windows'un kullanıcı hesabına bağlı DPAPI sistemiyle şifreler. */
object WindowsCredentialCodec : CredentialCodec {
    private const val PREFIX = "dpapi1:"

    override fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    override fun encrypt(plain: String): String {
        if (plain.isEmpty() || isEncrypted(plain)) return plain
        val protected = Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(protected)
    }

    override fun decryptOrPlain(stored: String): String {
        if (!isEncrypted(stored)) return stored
        val protected = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        return String(Crypt32Util.cryptUnprotectData(protected), Charsets.UTF_8)
    }
}

/** Ayarları kullanıcı klasöründeki dosyada saklar. */
class FileSettingsStore(private val file: File) : SettingsStore {
    private val values = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.outputStream().use { values.store(it, "Email-Manager 26") }
    }

    @Synchronized
    private fun put(key: String, value: String) {
        values.setProperty(key, value)
        save()
    }

    override fun getString(key: String, default: String?): String? = values.getProperty(key) ?: default
    override fun getBoolean(key: String, default: Boolean) = values.getProperty(key)?.toBoolean() ?: default
    override fun getInt(key: String, default: Int) = values.getProperty(key)?.toIntOrNull() ?: default
    override fun getLong(key: String, default: Long) = values.getProperty(key)?.toLongOrNull() ?: default
    override fun putString(key: String, value: String) = put(key, value)
    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())
    override fun putInt(key: String, value: Int) = put(key, value.toString())
    override fun putLong(key: String, value: Long) = put(key, value.toString())
}

class DesktopPlatformBridge(
    private val dataDir: File,
    private val onMessage: (String) -> Unit = {}
) : PlatformBridge {
    private val deletedFile = File(dataDir, "deleted-ids.txt")
    private val deleted = java.util.Collections.synchronizedSet(
        if (deletedFile.exists()) deletedFile.readLines().toMutableSet() else mutableSetOf()
    )

    private fun saveDeleted() {
        dataDir.mkdirs()
        deletedFile.writeText(deleted.toList().joinToString("\n"))
    }

    override fun playSound(soundType: SoundType) = Unit
    override fun triggerHaptic(hapticType: HapticType) = Unit
    override fun showToastNotification(message: String) = onMessage(message)

    override fun getPlatformInfo() = PlatformInfo(
        PlatformType.DESKTOP,
        System.getProperty("os.name").orEmpty(),
        System.getProperty("os.version").orEmpty(),
        System.getProperty("os.arch").orEmpty(),
        "Desktop"
    )

    override fun openUrl(url: String) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }

    override fun shareContent(subject: String, body: String) {
        val mail = "mailto:?subject=" + java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
        openUrl(mail)
    }

    override fun addDeletedIdentifier(id: String) {
        if (deleted.add(id)) saveDeleted()
    }

    override fun isIdentifierDeleted(id: String): Boolean = id in deleted

    override fun removeDeletedIdentifier(id: String) {
        if (deleted.remove(id)) saveDeleted()
    }

    override fun attachmentDirectory(emailId: Long): File = File(File(dataDir, "attachments"), emailId.toString())

    override fun openFile(file: File, mimeType: String) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
    }
}

/** Masaüstü uygulamasının bağımlılıkları. */
class DesktopApp private constructor(
    val database: DesktopDatabase,
    val repository: EmailRepository,
    val bridge: PlatformBridge
) {
    fun close() = database.close()

    companion object {
        fun defaultDataDir(): File {
            val base = System.getenv("APPDATA") ?: System.getProperty("user.home")
            return File(base, "Email-Manager-26")
        }

        suspend fun start(
            dataDir: File = defaultDataDir(),
            onMessage: (String) -> Unit = {},
            codec: CredentialCodec = WindowsCredentialCodec
        ): DesktopApp {
            dataDir.mkdirs()
            Credentials.codec = codec
            DesktopApp::class.java.getResourceAsStream("/" + I18n.CATALOG_FILE)?.use { I18n.load(it) }
            AppSettings.attach(FileSettingsStore(File(dataDir, "settings.properties")))

            val database = Room.databaseBuilder<DesktopDatabase>(File(dataDir, "email_manager_26.db").absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            if (!AppSettings.isInitialDataSeeded()) {
                DefaultData.seed(database.emailDao())
                AppSettings.markInitialDataSeeded()
            }
            val bridge = DesktopPlatformBridge(dataDir, onMessage)
            val repository = EmailRepository(database.emailDao(), database.accountDao(), bridge)
            repository.migrateStoredPasswords()
            return DesktopApp(database, repository, bridge)
        }
    }
}
