package dev.tricked.solidverdant.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.repository.InboxRepository
import dev.tricked.solidverdant.data.local.SettingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Review bottom-nav count badge. Observes [InboxRepository.observeOpenIssueCount] for the
 * currently selected organization; the host sets the organization via [setOrganization] whenever
 * membership changes. Emits 0 when no organization is selected so the badge stays hidden.
 */
@HiltViewModel
class ReviewBadgeViewModel @Inject constructor(
    private val inboxRepository: InboxRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private var organizationId: String? = null
    private var badgeJob: Job? = null
    private val _openIssueCount = MutableStateFlow(0)
    val openIssueCount: StateFlow<Int> = _openIssueCount.asStateFlow()

    fun setOrganization(id: String?) {
        if (organizationId == id && badgeJob?.isActive == true) return
        organizationId = id
        badgeJob?.cancel()
        if (id.isNullOrBlank()) {
            _openIssueCount.value = 0
            return
        }
        _openIssueCount.value = settingsDataStore.getCachedReviewBadgeCount(id)
        badgeJob = viewModelScope.launch {
            inboxRepository.observeOpenIssueCount(id).collect { count ->
                _openIssueCount.value = count
                settingsDataStore.cacheReviewBadgeCount(id, count)
            }
        }
    }
}
