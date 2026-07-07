/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.db.CatalogDao
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.data.repository.TemplateRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for the manage-templates screen and the Track quick-start row. Templates are Room-backed
 * so this is fully available offline; [projects]/[tasks]/[tags] come from the cached catalogue and
 * intentionally include archived/done items so availability can be resolved (see [TemplateResolver]).
 */
data class ManageTemplatesUiState(
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val organizationId: String? = null,
    val templates: List<EntryTemplate> = emptyList(),
    val quickStart: List<EntryTemplate> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && !error && templates.isEmpty()
    val activeProjects: List<Project> get() = projects.filterNot { it.isArchived }
    val activeTasks: List<Task> get() = tasks.filterNot { it.isDone }
}

/**
 * Backs [ManageTemplatesScreen] and the Track favorites row. The selected organization is resolved
 * internally from the persisted membership id and the cached memberships, so both screens work
 * without any organization being threaded in from the caller and survive an organization switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManageTemplatesViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val timeEntryRepository: TimeEntryRepository,
    catalogDao: CatalogDao,
    authDataStore: AuthDataStore,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    private val organizationId: StateFlow<String?> = combine(
        authDataStore.currentMembershipId,
        catalogDao.observeMemberships(),
    ) { selectedId, memberships ->
        val selected = memberships.firstOrNull { it.id == selectedId } ?: memberships.firstOrNull()
        selected?.organizationId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<ManageTemplatesUiState> =
        combine(organizationId, retryTrigger) { org, _ -> org }
            .flatMapLatest { org ->
                if (org == null) {
                    flowOf(ManageTemplatesUiState(isLoading = false, organizationId = null))
                } else {
                    combine(
                        templateRepository.observeTemplates(org),
                        timeEntryRepository.observeProjects(org),
                        timeEntryRepository.observeTasks(org),
                        timeEntryRepository.observeTags(org),
                    ) { templates, projects, tasks, tags ->
                        ManageTemplatesUiState(
                            isLoading = false,
                            error = false,
                            organizationId = org,
                            templates = TemplateOrdering.forManage(templates),
                            quickStart = TemplateOrdering.forQuickStart(templates, limit = QUICK_START_LIMIT),
                            projects = projects,
                            tasks = tasks,
                            tags = tags,
                        )
                    }.catch { throwable ->
                        Timber.e(throwable, "Failed to load templates")
                        emit(ManageTemplatesUiState(isLoading = false, error = true, organizationId = org))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageTemplatesUiState())

    /** Ordered quick-start list for the Track row, isolated so it recomposes only on real changes. */
    val quickStart: StateFlow<List<EntryTemplate>> = uiState
        .map { it.quickStart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() {
        retryTrigger.value += 1
    }

    fun saveNewTemplate(draft: TemplateDraft) {
        val org = organizationId.value ?: return
        viewModelScope.launch {
            templateRepository.createTemplate(
                organizationId = org,
                name = draft.name,
                projectId = draft.projectId,
                taskId = draft.taskId,
                description = draft.description,
                tagIds = draft.tagIds,
                billable = draft.billable,
                isFavorite = draft.isFavorite,
            )
        }
    }

    fun updateTemplate(template: EntryTemplate) {
        viewModelScope.launch { templateRepository.updateTemplate(template) }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch { templateRepository.deleteTemplate(id) }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { templateRepository.setFavorite(id, favorite) }
    }

    /**
     * Move a template one step within its favorite bucket. Reordering never crosses the
     * favorite/non-favorite boundary because favorites always sort first.
     */
    fun moveTemplate(id: String, up: Boolean) {
        val ordered = TemplateOrdering.forManage(uiState.value.templates)
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in ordered.indices) return
        if (ordered[index].isFavorite != ordered[target].isFavorite) return
        val reordered = ordered.toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
        viewModelScope.launch { templateRepository.persistOrder(reordered) }
    }

    private companion object {
        const val QUICK_START_LIMIT = 12
    }
}
