package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.InboxDismissalDao
import dev.tricked.solidverdant.domain.inbox.InboxAnalyzer
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.tricked.solidverdant.domain.inbox.InboxSettingsDataStore
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Seam for the Time Inbox open-issue count. The Review bottom-nav badge and the Inbox pane observe
 * this. The count is the number of not-yet-dismissed items needing attention for [organizationId]
 * (see gap analysis #16/#17). Kept minimal so the bottom-nav badge has a stable dependency while
 * the Inbox feature agent implements the real rules in [InboxRepositoryImpl].
 */
interface InboxRepository {
    /**
     * Emits the current count of open (not dismissed, not resolved) inbox items for the given
     * organization. Emits 0 when there is nothing to review ("all caught up").
     */
    fun observeOpenIssueCount(organizationId: String): Flow<Int>
}

/**
 * Real implementation of the Time Inbox count. Derives the same deterministic checks the Inbox pane
 * shows ([InboxAnalyzer]) from cached time entries, the local inbox configuration, and the shared
 * long-timer threshold, minus any dismissals that are still within their retention window. Because
 * it reuses the analyzer, the badge and the pane can never disagree about what is open.
 */
class InboxRepositoryImpl @Inject constructor(
    private val timeEntryRepository: TimeEntryRepository,
    private val inboxSettingsDataStore: InboxSettingsDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val dismissalDao: InboxDismissalDao,
    private val clock: Clock,
) : InboxRepository {

    override fun observeOpenIssueCount(organizationId: String): Flow<Int> = combine(
        timeEntryRepository.observeTimeEntries(organizationId),
        inboxSettingsDataStore.settings,
        settingsDataStore.longTimerHours,
        dismissalDao.observeDismissals(organizationId),
    ) { entries, settings, longTimerHours, dismissals ->
        val now = clock.nowMs()
        val retentionMs = TimeUnit.DAYS.toMillis(InboxAnalyzer.DISMISSAL_RETENTION_DAYS)
        val activeDismissedKeys = dismissals
            .filter { now - it.dismissedAtMs <= retentionMs }
            .map { it.issueKey }
            .toSet()
        InboxAnalyzer.count(
            entries = entries,
            config = settings.toConfig(longTimerHours),
            dismissedKeys = activeDismissedKeys,
            nowMs = now,
            zone = ZoneId.systemDefault(),
        )
    }.distinctUntilChanged()
}
