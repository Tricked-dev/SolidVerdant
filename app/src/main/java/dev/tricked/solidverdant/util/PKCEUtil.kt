package dev.tricked.solidverdant.util

import android.util.Base64
import dev.tricked.solidverdant.data.model.PKCEData
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utility object for PKCE (Proof Key for Code Exchange) operations
 * Used in OAuth2 authorization flow for enhanced security
 */
object PKCEUtil {
    private const val CODE_VERIFIER_LENGTH = 128
    private const val STATE_LENGTH = 40
    private const val REDIRECT_URI = "solidtime://oauth/callback"

    /**
     * Generates a random code verifier string
     * @param length Length of the code verifier (default: 128)
     * @return Random alphanumeric string
     */
    fun generateCodeVerifier(length: Int = CODE_VERIFIER_LENGTH): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Generates a random state parameter for CSRF protection
     * @param length Length of the state string (default: 40)
     * @return Random alphanumeric string
     */
    fun generateState(length: Int = STATE_LENGTH): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Generates a code challenge from a code verifier using S256 method
     * @param codeVerifier The code verifier to hash
     * @return Base64 URL-encoded SHA-256 hash of the verifier
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return base64UrlEncode(digest)
    }

    /**
     * Generates complete PKCE data (verifier, challenge, state)
     * @return PKCEData object with all required parameters
     */
    fun generatePKCEData(): PKCEData {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateState()
        return PKCEData(codeVerifier, codeChallenge, state)
    }

    /**
     * Builds the OAuth2 authorization URL with PKCE parameters
     * @param endpoint The Solidtime instance endpoint
     * @param clientId The OAuth2 client ID
     * @param codeChallenge The PKCE code challenge
     * @param state The state parameter for CSRF protection
     * @param redirectUri The redirect URI (default: solidtime://oauth/callback)
     * @return Complete authorization URL
     */
    fun buildAuthorizationUrl(
        endpoint: String,
        clientId: String,
        codeChallenge: String,
        state: String,
        redirectUri: String = REDIRECT_URI
    ): String {
        val cleanEndpoint = endpoint.removeSuffix("/")
        return buildString {
            append("$cleanEndpoint/oauth/authorize")
            append("?client_id=").append(urlEncode(clientId))
            append("&redirect_uri=").append(urlEncode(redirectUri))
            append("&response_type=code")
            append("&state=").append(urlEncode(state))
            append("&code_challenge=").append(urlEncode(codeChallenge))
            append("&code_challenge_method=S256")
            append("&scope=*")
        }
    }

    /**
     * Encodes bytes to Base64 URL-safe format without padding
     * @param bytes Bytes to encode
     * @return Base64 URL-encoded string without padding
     */
    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * URL-encodes a string
     * @param value String to encode
     * @return URL-encoded string
     */
    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
}
