package dev.tricked.solidverdant.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SecretKeyProvider {
    fun getOrCreate(): SecretKey
}

internal class AndroidKeystoreKeyProvider : SecretKeyProvider {
    @Synchronized
    override fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "solidverdant_auth_secrets_v1"
    }
}

/** AES-GCM envelope. A fresh IV and authentication tag are generated for every write. */
internal class AuthSecretCipher(
    private val keyProvider: SecretKeyProvider = AndroidKeystoreKeyProvider()
) {
    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    /** Converts legacy plaintext while leaving an already migrated envelope untouched. */
    fun encryptIfNeeded(value: String): String = if (isEncrypted(value)) value else encrypt(value)

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate())
        val payload = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(value: String): String {
        require(isEncrypted(value)) { "Authentication secret is not encrypted" }
        val payload = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        require(payload.size > IV_LENGTH_BYTES) { "Invalid encrypted authentication secret" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider.getOrCreate(),
            GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOfRange(0, IV_LENGTH_BYTES))
        )
        return cipher.doFinal(payload.copyOfRange(IV_LENGTH_BYTES, payload.size))
            .toString(Charsets.UTF_8)
    }

    private companion object {
        const val PREFIX = "enc:v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
