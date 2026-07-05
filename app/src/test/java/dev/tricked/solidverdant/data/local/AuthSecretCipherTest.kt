package dev.tricked.solidverdant.data.local

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSecretCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AuthSecretCipher(object : SecretKeyProvider {
        override fun getOrCreate(): SecretKey = key
    })

    @Test
    fun `encrypted value contains no plaintext and decrypts`() {
        val plaintext = "access-token-secret"
        val encrypted = cipher.encrypt(plaintext)

        assertTrue(cipher.isEncrypted(encrypted))
        assertFalse(encrypted.contains(plaintext))
        assertEquals(plaintext, cipher.decrypt(encrypted))
    }

    @Test
    fun `each encryption uses a fresh nonce`() {
        assertNotEquals(cipher.encrypt("same-token"), cipher.encrypt("same-token"))
    }

    @Test
    fun `migration encrypts plaintext and is idempotent`() {
        val migrated = cipher.encryptIfNeeded("legacy-plaintext-token")

        assertTrue(cipher.isEncrypted(migrated))
        assertEquals("legacy-plaintext-token", cipher.decrypt(migrated))
        assertEquals(migrated, cipher.encryptIfNeeded(migrated))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `plaintext cannot be read as ciphertext`() {
        cipher.decrypt("legacy-plaintext-token")
    }
}
