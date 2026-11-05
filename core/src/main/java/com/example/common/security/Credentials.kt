package com.example.common.security

/** Hesap şifrelerini saklamak için platforma özel şifreleme. */
interface CredentialCodec {
    fun isEncrypted(value: String): Boolean
    fun encrypt(plain: String): String
    fun decryptOrPlain(stored: String): String
}

object Credentials {
    /** Platform başlarken gerçek şifreleyiciyi atar. */
    var codec: CredentialCodec = object : CredentialCodec {
        override fun isEncrypted(value: String) = false
        override fun encrypt(plain: String) = plain
        override fun decryptOrPlain(stored: String) = stored
    }
}
