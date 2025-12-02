package dev.tricked.solidverdant.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Retrofit API instances with dynamic base URLs
 */
@Singleton
class ApiClientFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    /**
     * Creates a Solidtime API instance with the given base URL
     * @param baseUrl The base URL for the API (e.g., "https://app.solidtime.io")
     * @return SolidtimeApi instance
     */
    fun createApi(baseUrl: String): SolidtimeApi {
        val contentType = "application/json".toMediaType()
        val cleanBaseUrl = baseUrl.removeSuffix("/") + "/"

        val retrofit = Retrofit.Builder()
            .baseUrl(cleanBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(SolidtimeApi::class.java)
    }
}
