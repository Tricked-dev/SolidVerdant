/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.sync

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.EntrySyncStatus
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.SyncOperation
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the dedicated Sync Center screen (#33). Read/display surface plus the app's existing
 * sync actions (retry-all / per-entry retry / discard / manual sync) — it never reimplements sync
 * control flow, only re-scopes and exposes it. Current organization is resolved reactively from
 * cached auth (selected membership -> its organization), mirroring how [TrackingViewModel] resolves
 * the active org, so the screen re-scopes on org switch and works offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncCenterViewModel @Inject constructor(
    private val repository: TimeEntryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncTrigger: SyncTrigger,
    private val clock: Clock,
) : ViewModel() {

    private val orgIdFlow: Flow<String?> = settingsDataStore.observeCachedAuth()
        .map { auth -> resolveOrgId(auth) }
        .distinctUntilChanged()

    val uiState: StateFlow<SyncCenterUiState> = orgIdFlow.flatMapLatest { orgId ->
        if (orgId == null) {
            flowOf(SyncCenterUiState(isLoading = false))
        } else {
            combine(
                repository.observeSyncOperations(orgId),
                repository.observeSyncMeta(orgId),
            ) { operations, meta ->
                val failed = operations.filter { it.status == EntrySyncStatus.FAILED }
                val conflicts = operations.filter { it.status == EntrySyncStatus.CONFLICT }
                val pending = operations.filter {
                    it.status == EntrySyncStatus.PENDING || it.status == EntrySyncStatus.RETRYING
                }
                SyncCenterUiState(
                    isLoading = false,
                    organizationId = orgId,
                    // The DAO seeds a sentinel pull timestamp of 0 when a row is created for a
                    // push-only stamp; treat 0 as "never pulled" so freshness reads honestly.
                    lastFullSyncAtMs = meta?.lastFullSyncAtMs?.takeIf { it > 0L },
                    lastPushAtMs = meta?.lastPushAtMs,
                    pending = pending,
                    failed = failed,
                    conflicts = conflicts,
                    topLine = when {
                        failed.isNotEmpty() -> SyncCenterUiState.TopLine.FAILURES
                        conflicts.isNotEmpty() -> SyncCenterUiState.TopLine.CONFLICTS
                        pending.isNotEmpty() -> SyncCenterUiState.TopLine.PENDING
                        else -> SyncCenterUiState.TopLine.SYNCED
                    },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MS), seedState())

    /** First-frame seed: resolve the last selected org synchronously from cached auth. */
    private fun seedState(): SyncCenterUiState {
        val orgId = resolveOrgId(settingsDataStore.getCachedAuth())
        return SyncCenterUiState(isLoading = orgId != null, organizationId = orgId)
    }

    private fun resolveOrgId(auth: SettingsDataStore.CachedAuth?): String? = auth?.let {
        (it.memberships.firstOrNull { m -> m.id == it.currentMembershipId } ?: it.memberships.firstOrNull())
            ?.organizationId
    }

    /** Retry every queued/failed change by requesting a sync drain (reuses the outbox worker). */
    fun retryAll() = syncTrigger.requestSync()

    /** Retry a single failed change: clear its dead-letter/attempt state, then request a sync. */
    fun retry(entryId: String) {
        viewModelScope.launch {
            if (repository.prepareRetry(entryId)) syncTrigger.requestSync()
        }
    }

    /** Permanently drop a failed change's queued operation so it stops appearing. */
    fun discard(entryId: String) {
        viewModelScope.launch { repository.discardFailedSync(entryId) }
    }

    /** Manual "Sync now": kick the same background sync the app enqueues after every local edit. */
    fun syncNow() = syncTrigger.requestSync()

    /** Current wall-clock, for the screen's relative-time freshness labels. */
    fun nowMs(): Long = clock.nowMs()

    /**
     * Cancels [viewModelScope] for unit tests that install a test Main dispatcher; mirrors the
     * statistics VM's teardown so no Main-bound collector straggles past `Dispatchers.resetMain()`.
     * Not used in production.
     */
    @VisibleForTesting
    internal fun cancelScopeForTest() {
        viewModelScope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        const val STATE_STOP_TIMEOUT_MS = 5_000L
    }
}

/** Immutable UI state for the Sync Center screen. */
data class SyncCenterUiState(
    val isLoading: Boolean = true,
    val organizationId: String? = null,
    val lastFullSyncAtMs: Long? = null,
    val lastPushAtMs: Long? = null,
    val pending: List<SyncOperation> = emptyList(),
    val failed: List<SyncOperation> = emptyList(),
    val conflicts: List<SyncOperation> = emptyList(),
    val topLine: TopLine = TopLine.SYNCED,
) {
    val pendingCount: Int get() = pending.size
    val failedCount: Int get() = failed.size
    val conflictCount: Int get() = conflicts.size

    /** Plain-language headline bucket; the screen maps each to a localized sentence + icon. */
    enum class TopLine { SYNCED, PENDING, FAILURES, CONFLICTS }
}
