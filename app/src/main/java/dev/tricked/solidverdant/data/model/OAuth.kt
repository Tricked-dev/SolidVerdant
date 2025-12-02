package dev.tricked.solidverdant.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth2 token response from the server
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("expires_in")
    val expiresIn: Int? = null
)

/**
 * OAuth2 configuration for the Solidtime instance
 */
data class OAuthConfig(
    val endpoint: String = "https://app.solidtime.io",
    val clientId: String = "9c994748-c593-4a6d-951b-6849c829bc4e"
)

/**
 * PKCE (Proof Key for Code Exchange) data for OAuth2 flow
 */
data class PKCEData(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String
)
