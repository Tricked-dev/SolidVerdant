package dev.tricked.solidverdant.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cacheDataStore by preferencesDataStore(name = "cache_prefs")

/**
 * Simple cache using DataStore for projects and tasks
 * Stores JSON serialized data with timestamps
 */
@Singleton
class CacheDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private val PROJECTS_JSON = stringPreferencesKey("projects_json")
        private val PROJECTS_TIMESTAMP = longPreferencesKey("projects_timestamp")
        private val TASKS_JSON = stringPreferencesKey("tasks_json")
        private val TASKS_TIMESTAMP = longPreferencesKey("tasks_timestamp")

        // Cache TTL: 5 minutes
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    /**
     * Cache projects to DataStore
     */
    suspend fun cacheProjects(projects: List<Project>) {
        try {
            val jsonString = json.encodeToString(projects)
            context.cacheDataStore.edit { prefs ->
                prefs[PROJECTS_JSON] = jsonString
                prefs[PROJECTS_TIMESTAMP] = System.currentTimeMillis()
            }
            Timber.d("Cached ${projects.size} projects")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache projects")
        }
    }

    /**
     * Get cached projects if not expired
     * Returns null if cache is expired or doesn't exist
     */
    suspend fun getCachedProjects(): List<Project>? {
        return try {
            val prefs = context.cacheDataStore.data.first()
            val timestamp = prefs[PROJECTS_TIMESTAMP] ?: return null
            val jsonString = prefs[PROJECTS_JSON] ?: return null

            // Check if cache is expired
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                Timber.d("Projects cache expired")
                return null
            }

            val projects = json.decodeFromString<List<Project>>(jsonString)
            Timber.d("Retrieved ${projects.size} cached projects")
            projects
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve cached projects")
            null
        }
    }

    /**
     * Cache tasks to DataStore
     */
    suspend fun cacheTasks(tasks: List<Task>) {
        try {
            val jsonString = json.encodeToString(tasks)
            context.cacheDataStore.edit { prefs ->
                prefs[TASKS_JSON] = jsonString
                prefs[TASKS_TIMESTAMP] = System.currentTimeMillis()
            }
            Timber.d("Cached ${tasks.size} tasks")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache tasks")
        }
    }

    /**
     * Get cached tasks if not expired
     * Returns null if cache is expired or doesn't exist
     */
    suspend fun getCachedTasks(): List<Task>? {
        return try {
            val prefs = context.cacheDataStore.data.first()
            val timestamp = prefs[TASKS_TIMESTAMP] ?: return null
            val jsonString = prefs[TASKS_JSON] ?: return null

            // Check if cache is expired
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                Timber.d("Tasks cache expired")
                return null
            }

            val tasks = json.decodeFromString<List<Task>>(jsonString)
            Timber.d("Retrieved ${tasks.size} cached tasks")
            tasks
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve cached tasks")
            null
        }
    }

    /**
     * Check if cache is still valid (not expired)
     */
    suspend fun isCacheValid(): Boolean {
        return try {
            val prefs = context.cacheDataStore.data.first()
            val projectsTimestamp = prefs[PROJECTS_TIMESTAMP] ?: return false
            val tasksTimestamp = prefs[TASKS_TIMESTAMP] ?: return false

            val now = System.currentTimeMillis()
            val projectsValid = (now - projectsTimestamp) <= CACHE_TTL_MS
            val tasksValid = (now - tasksTimestamp) <= CACHE_TTL_MS

            projectsValid && tasksValid
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all cached data
     */
    suspend fun clearCache() {
        try {
            context.cacheDataStore.edit { prefs ->
                prefs.remove(PROJECTS_JSON)
                prefs.remove(PROJECTS_TIMESTAMP)
                prefs.remove(TASKS_JSON)
                prefs.remove(TASKS_TIMESTAMP)
            }
            Timber.d("Cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cache")
        }
    }
}
