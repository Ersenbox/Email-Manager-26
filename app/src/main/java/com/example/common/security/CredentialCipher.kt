package com.example.common.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hesap şifrelerini Android Keystore'daki AES-GCM anahtarıyla şifreler.
 * Anahtar cihazdan çıkarılamaz. Şifreli değerler "enc1:" önekiyle saklanır;
 * önceki sürümlerden kalan düz metin değerler okunabilir ve ilk açılışta şifrelenir.
 */
object CredentialCipher : CredentialCodec {

    private const val PREFIX = "enc1:"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "email_manager_credentials_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    override fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    override fun encrypt(plain: String): String {
        if (plain.isEmpty() || isEncrypted(plain)) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + encode(cipher.iv) + ":" + encode(encrypted)
    }

    /** Çözülemeyen değer için boş metin döner; kullanıcı şifreyi yeniden girmelidir. */
    override fun decryptOrPlain(stored: String): String {
        if (!isEncrypted(stored)) return stored
        return try {
            val parts = stored.removePrefix(PREFIX).split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, decode(parts[0])))
            String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
