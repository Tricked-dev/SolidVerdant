package dev.tricked.solidverdant.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cacheDataStore by preferencesDataStore(name = "cache_prefs")

@Serializable
data class ScreenSnapshot(
    val version: Int = CURRENT_VERSION,
    val organizationId: String,
    val user: User,
    val memberships: List<Membership>,
    val currentMembershipId: String,
    val timeEntries: List<TimeEntry>,
    val activeEntry: TimeEntry?,
    val projects: List<Project>,
    val tasks: List<Task>,
    val tags: List<Tag>,
    val savedAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object { const val CURRENT_VERSION = 1 }
}

/** Stale-while-revalidate cache for the complete first tracking frame. */
@Singleton
class CacheDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private companion object { val SNAPSHOT = stringPreferencesKey("screen_snapshot_v1") }

    suspend fun getScreenSnapshot(): ScreenSnapshot? = try {
        val encoded = context.cacheDataStore.data.first()[SNAPSHOT] ?: return null
        json.decodeFromString<ScreenSnapshot>(encoded).takeIf {
            it.version == ScreenSnapshot.CURRENT_VERSION
        }.also { if (it == null) Timber.w("Discarding old screen snapshot") }
    } catch (error: Exception) {
        Timber.w(error, "Discarding corrupt screen snapshot")
        clearCache()
        null
    }

    suspend fun saveScreenSnapshot(snapshot: ScreenSnapshot) {
        try {
            val value = snapshot.copy(
                version = ScreenSnapshot.CURRENT_VERSION,
                savedAtEpochMs = System.currentTimeMillis()
            )
            context.cacheDataStore.edit { it[SNAPSHOT] = json.encodeToString(value) }
        } catch (error: Exception) {
            Timber.e(error, "Failed to save screen snapshot")
        }
    }

    suspend fun clearCache() = context.cacheDataStore.edit { it.clear() }
}
