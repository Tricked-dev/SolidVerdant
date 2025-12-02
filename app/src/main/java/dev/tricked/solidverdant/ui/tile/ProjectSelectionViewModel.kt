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
    private val authRepository: dev.tricked.solidverdant.data.repository.AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSelectionUiState())
    val uiState: StateFlow<ProjectSelectionUiState> = _uiState.asStateFlow()

    fun loadProjects() {
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

                // Load projects and tasks
                val endpoint = authRepository.endpoint.first()
                val api = apiClientFactory.createApi(endpoint)
                val projectsResponse = api.getProjects(organizationId)
                val tasksResponse = api.getTasks(organizationId)

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
