package dev.tricked.solidverdant.ui.tile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.local.CacheDataStore
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.repository.AuthRepository
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
    val tasks: List<Task> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for project selection.
 * Only handles loading projects/tasks - actual tracking is done by TileService.
 */
@HiltViewModel
class ProjectSelectionViewModel @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val authRepository: AuthRepository,
    private val cacheDataStore: CacheDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSelectionUiState())
    val uiState: StateFlow<ProjectSelectionUiState> = _uiState.asStateFlow()

    fun loadProjects(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val memberships = authRepository.getMyMemberships().getOrNull()
                val organizationId = memberships?.firstOrNull()?.organizationId

                if (organizationId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No organization found"
                    )
                    return@launch
                }

                // Try cache first
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
                        // Refresh in background
                        loadProjectsInBackground(organizationId)
                        return@launch
                    }
                }

                // Load from API
                val endpoint = authRepository.endpoint.first()
                val api = apiClientFactory.createApi(endpoint)
                val projectsResponse = api.getProjects(organizationId)
                val tasksResponse = api.getTasks(organizationId)

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

    private fun loadProjectsInBackground(organizationId: String) {
        viewModelScope.launch {
            try {
                val endpoint = authRepository.endpoint.first()
                val api = apiClientFactory.createApi(endpoint)
                val projectsResponse = api.getProjects(organizationId)
                val tasksResponse = api.getTasks(organizationId)

                cacheDataStore.cacheProjects(projectsResponse.data)
                cacheDataStore.cacheTasks(tasksResponse.data)

                _uiState.value = _uiState.value.copy(
                    projects = projectsResponse.data,
                    tasks = tasksResponse.data
                )
                Timber.d("Background refresh completed")
            } catch (e: Exception) {
                Timber.w(e, "Background refresh failed")
            }
        }
    }
}
