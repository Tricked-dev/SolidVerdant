package dev.tricked.solidverdant.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.repository.InboxRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the Review bottom-nav count badge. Observes [InboxRepository.observeOpenIssueCount] for the
 * currently selected organization; the host sets the organization via [setOrganization] whenever
 * membership changes. Emits 0 when no organization is selected so the badge stays hidden.
 */
@HiltViewModel
class ReviewBadgeViewModel @Inject constructor(
    private val inboxRepository: InboxRepository,
) : ViewModel() {

    private val organizationId = MutableStateFlow<String?>(null)

    fun setOrganization(id: String?) {
        organizationId.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val openIssueCount: StateFlow<Int> = organizationId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(0) else inboxRepository.observeOpenIssueCount(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)
}
