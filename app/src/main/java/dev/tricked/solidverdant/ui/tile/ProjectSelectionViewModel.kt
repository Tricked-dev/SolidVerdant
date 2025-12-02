package dev.tricked.solidverdant.ui.tile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectSelectionUiState(
    val isLoading: Boolean = false,
    val projects: List<Project> = emptyList(),
    val tasks: List<dev.tricked.solidverdant.data.model.Task> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ProjectSelectionViewModel @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val authRepository: dev.tricked.solidverdant.data.repository.AuthRepository,
    private val cacheDataStore: dev.tricked.solidverdant.data.local.CacheDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSelectionUiState())
    val uiState: StateFlow<ProjectSelectionUiState> = _uiState.asStateFlow()

    fun loadProjects(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Get organization ID from current membership
                val memberships = authRepository.getMyMemberships().getOrNull()
                val organizationId = memberships?.firstOrNull()?.organizationId

                if (organizationId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No organization found"
                    )
                    return@launch
                }

                // Try to load from cache first if not forcing refresh
                if (!forceRefresh) {
                    val cachedProjects = cacheDataStore.getCachedProjects()
                    val cachedTasks = cacheDataStore.getCachedTasks()

                    if (cachedProjects != null && cachedTasks != null) {
                        Timber.d("Using cached projects and tasks")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            projects = cachedProjects,
                            tasks = cachedTasks
                        )
                        // Load fresh data in background
                        loadProjectsInBackground(organizationId)
                        return@launch
                    }
                }

                // Load projects and tasks from API
                val endpoint = authRepository.endpoint.first()
                val api = apiClientFactory.createApi(endpoint)
                val projectsResponse = api.getProjects(organizationId)
                val tasksResponse = api.getTasks(organizationId)

                // Cache the results
                cacheDataStore.cacheProjects(projectsResponse.data)
                cacheDataStore.cacheTasks(tasksResponse.data)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    projects = projectsResponse.data,
                    tasks = tasksResponse.data
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load projects and tasks")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load projects"
                )
            }
        }
    }

    /**
     * Load projects in background without showing loading state
     */
    private fun loadProjectsInBackground(organizationId: String) {
        viewModelScope.launch {
            try {
                val endpoint = authRepository.endpoint.first()
                val api = apiClientFactory.createApi(endpoint)
                val projectsResponse = api.getProjects(organizationId)
                val tasksResponse = api.getTasks(organizationId)

                // Update cache
                cacheDataStore.cacheProjects(projectsResponse.data)
                cacheDataStore.cacheTasks(tasksResponse.data)

                // Update UI silently if data changed
                _uiState.value = _uiState.value.copy(
                    projects = projectsResponse.data,
                    tasks = tasksResponse.data
                )
                Timber.d("Background refresh completed")
            } catch (e: Exception) {
                Timber.w(e, "Background refresh failed, using cached data")
            }
        }
    }

    fun startTracking(projectId: String?, taskId: String?, description: String) {
        viewModelScope.launch {
            try {
                val memberships = authRepository.getMyMemberships().getOrNull()
                val membership = memberships?.firstOrNull()
                val user = authRepository.getCurrentUser().getOrNull()

                if (membership == null || user == null) {
                    Timber.w("Cannot start tracking: missing membership or user")
                    return@launch
                }

                authRepository.startTimeEntry(
                    organizationId = membership.organizationId,
                    memberId = membership.id,
                    userId = user.id,
                    projectId = projectId,
                    taskId = taskId,
                    description = description
                )
                    .onSuccess {
                        Timber.d("Time tracking started from tile")
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to start tracking from tile")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error starting tracking")
            }
        }
    }
}
