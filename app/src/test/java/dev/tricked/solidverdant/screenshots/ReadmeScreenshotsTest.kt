package dev.tricked.solidverdant.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.data.calendar.DeviceCalendarEvent
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.domain.inbox.InboxIssue
import dev.tricked.solidverdant.domain.inbox.InboxIssueType
import dev.tricked.solidverdant.domain.inbox.MissingField
import dev.tricked.solidverdant.ui.calendar.CalendarUiState
import dev.tricked.solidverdant.ui.calendar.CalendarViewMode
import dev.tricked.solidverdant.ui.calendar.DayBucket
import dev.tricked.solidverdant.ui.calendar.MonthCalendarView
import dev.tricked.solidverdant.ui.calendar.WeekCalendarView
import dev.tricked.solidverdant.ui.navigation.Screen as NavScreen
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog
import dev.tricked.solidverdant.ui.review.InboxHeader
import dev.tricked.solidverdant.ui.review.InboxIssueCard
import dev.tricked.solidverdant.ui.review.ReviewContent
import dev.tricked.solidverdant.ui.review.ReviewDayUiState
import dev.tricked.solidverdant.ui.review.ReviewItem
import dev.tricked.solidverdant.ui.review.ReviewItemType
import dev.tricked.solidverdant.ui.review.ReviewProject
import dev.tricked.solidverdant.ui.statistics.KpiGrid
import dev.tricked.solidverdant.ui.statistics.ProjectTotal
import dev.tricked.solidverdant.ui.statistics.StatCatalog
import dev.tricked.solidverdant.ui.statistics.StatFilterBar
import dev.tricked.solidverdant.ui.statistics.StatFilters
import dev.tricked.solidverdant.ui.statistics.StatisticsSummary
import dev.tricked.solidverdant.ui.statistics.TrendBucket
import dev.tricked.solidverdant.ui.statistics.charts.DonutChart
import dev.tricked.solidverdant.ui.statistics.hexToColor
import dev.tricked.solidverdant.ui.statistics.InteractiveBarChart
import dev.tricked.solidverdant.ui.templates.TemplateResolver
import dev.tricked.solidverdant.ui.templates.TemplateRow
import dev.tricked.solidverdant.ui.templates.templateDisplayLabel
import dev.tricked.solidverdant.ui.templates.templateProjectTaskSummary
import dev.tricked.solidverdant.ui.tracking.TrackingControls
import dev.tricked.solidverdant.ui.tracking.TrackingUiState
import dev.tricked.solidverdant.ui.tracking.trackingHistoryItems
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.DayOfWeek

/**
 * Generates the README screenshot set on the JVM — no emulator/device.
 *
 * Run with:  ./gradlew :app:recordRoborazziDebug
 *
 * Every screen is rendered across the [ScreenshotMatrix] (theme x device) into
 * .github/screenshots/generated/, and the Neo-dark + phone variant is additionally written to
 * .github/screenshots/readme/ as the cohesive hero set referenced by README.md.
 *
 * Every feature is rendered inside the production app scaffold and bottom navigation, using its
 * real top-level (or content) composable with deterministic state and no-op callbacks. Hilt and
 * ViewModels are intentionally not booted so captures remain deterministic and offline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class ReadmeScreenshotsTest {

    private class Screen(val name: String, val content: @Composable () -> Unit)

    @Test
    fun captureReadmeAndMatrix() {
        val screens = buildScreens()
        for (device in ScreenshotMatrix.devices) {
            for (theme in ScreenshotMatrix.themes) {
                for (screen in screens) {
                    ScreenshotHost.capture(
                        theme = theme,
                        device = device,
                        filePath = ScreenshotHost.outputPath(
                            ".github", "screenshots", "generated", "${screen.name}-${theme.id}-${device.id}.png",
                        ),
                        content = {
                            ScreenshotHost.AppShell(
                                destination = destinationFor(screen.name),
                                inboxBadgeCount = if (screen.name == "inbox") 4 else 0,
                                content = screen.content,
                            )
                        },
                    )
                    if (theme == ScreenshotMatrix.readmeTheme && device == ScreenshotMatrix.readmeDevice) {
                        ScreenshotHost.capture(
                            theme = theme,
                            device = device,
                            filePath = ScreenshotHost.outputPath(
                                ".github", "screenshots", "readme", "${screen.name}.png",
                            ),
                            content = {
                                ScreenshotHost.AppShell(
                                    destination = destinationFor(screen.name),
                                    inboxBadgeCount = if (screen.name == "inbox") 4 else 0,
                                    content = screen.content,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun destinationFor(screenName: String): NavScreen = when (screenName) {
        "calendar-month", "calendar-week" -> NavScreen.Calendar
        "statistics" -> NavScreen.Stats
        "inbox", "review", "templates" -> NavScreen.Review
        else -> NavScreen.Track
    }

    // ---------------------------------------------------------------------------------------------
    // Fabricated realistic sample data. Dates are pinned to June 2026 (never "today") so the
    // calendar's live current-time marker stays out of the golden images.
    // ---------------------------------------------------------------------------------------------

    private val zone = ZoneId.of("UTC")

    private val projects = listOf(
        Project(id = "p1", name = "Website Redesign", color = "#386A20"),
        Project(id = "p2", name = "Internal Tools", color = "#386666"),
        Project(id = "p3", name = "Client — Acme", color = "#8A5A00", isArchived = true),
    )
    private val tasks = listOf(
        Task(id = "t1", name = "Landing page", projectId = "p1", createdAt = "", updatedAt = ""),
        Task(id = "t2", name = "Design system", projectId = "p1", createdAt = "", updatedAt = ""),
    )
    private val tags = listOf(Tag("tag1", "focus"), Tag("tag2", "meeting"))
    private val clients = listOf(Client(id = "c1", name = "Acme Corp"))

    private fun entry(
        id: String,
        description: String?,
        startIso: String,
        endIso: String?,
        durationSeconds: Int,
        projectId: String? = "p1",
        taskId: String? = null,
        billable: Boolean = true,
        entryTags: List<Tag> = emptyList(),
    ) = TimeEntry(
        id = id,
        description = description,
        userId = "u1",
        start = startIso,
        end = endIso,
        duration = durationSeconds,
        taskId = taskId,
        projectId = projectId,
        tags = entryTags,
        billable = billable,
        organizationId = "org1",
    )

    private val historyEntries = listOf(
        entry("e1", "Design review", "2026-06-10T09:00:00Z", "2026-06-10T10:15:00Z", 4500, taskId = "t2", entryTags = listOf(tags[1])),
        entry("e2", "Landing page build", "2026-06-10T10:30:00Z", "2026-06-10T12:45:00Z", 8100, taskId = "t1", entryTags = listOf(tags[0])),
        entry("e3", "Standup", "2026-06-10T13:15:00Z", "2026-06-10T13:35:00Z", 1200, projectId = "p2", billable = false),
        entry("e4", "Bug triage", "2026-06-09T14:00:00Z", "2026-06-09T15:30:00Z", 5400, projectId = "p2", billable = false),
        entry("e5", "Client call — Acme", "2026-06-09T16:00:00Z", "2026-06-09T16:45:00Z", 2700, projectId = "p1", entryTags = listOf(tags[1])),
    )

    private val syncOperations = listOf(
        TimeEntryRepository.SyncOperation(
            entryId = "e3",
            type = OutboxOpType.CREATE,
            status = TimeEntryRepository.EntrySyncStatus.PENDING,
            attemptCount = 0,
            error = null,
        ),
        TimeEntryRepository.SyncOperation(
            entryId = "e4",
            type = OutboxOpType.UPDATE,
            status = TimeEntryRepository.EntrySyncStatus.FAILED,
            attemptCount = 3,
            error = "429 rate limited",
        ),
    )

    private fun groupedHistory(): Map<LocalDate, List<TimeEntry>> =
        historyEntries.groupBy { LocalDate.parse(it.start.substring(0, 10)) }
            .toSortedMap(compareByDescending { it })

    private fun buildScreens(): List<Screen> = listOf(
        // 1. Track — active timer running + history rows with sync chips.
        Screen("track") {
            val state = TrackingUiState(
                isTracking = true,
                elapsedSeconds = 5_112,
                currentTimeEntry = entry("running", "Landing page build", "2026-06-10T14:00:00Z", null, 0, taskId = "t1"),
                projects = projects,
                tasks = tasks,
                tags = tags,
                clients = clients,
                timeEntries = historyEntries,
                hasLoadedTimeEntries = true,
                editingDescription = "Landing page build",
                editingProjectId = "p1",
                editingTaskId = "t1",
                editingTags = listOf("tag1"),
                editingBillable = true,
                syncOperations = syncOperations,
            )
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    TrackingControls(
                        uiState = state,
                        onDescriptionChange = {},
                        onProjectChange = {},
                        onTaskChange = {},
                        onTagsChange = {},
                        onBillableChange = {},
                        onStart = {},
                        onStop = {},
                        onPause = {},
                        onResume = {},
                        onUpdate = {},
                    )
                }
                trackingHistoryItems(
                    uiState = state,
                    groupedEntries = groupedHistory(),
                    onEdit = {},
                    onDelete = {},
                    onDateClick = {},
                )
            }
        },
        // 2. History list — several entries grouped by day, with sync chips.
        Screen("history") {
            val state = TrackingUiState(
                projects = projects,
                tasks = tasks,
                tags = tags,
                timeEntries = historyEntries,
                hasLoadedTimeEntries = true,
                syncOperations = syncOperations,
            )
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                trackingHistoryItems(
                    uiState = state,
                    groupedEntries = groupedHistory(),
                    onEdit = {},
                    onDelete = {},
                    onDateClick = {},
                )
            }
        },
        // 3. Calendar — month view with sample entries.
        Screen("calendar-month") {
            val d10 = LocalDate.of(2026, 6, 10)
            val d09 = LocalDate.of(2026, 6, 9)
            val state = CalendarUiState(
                viewMode = CalendarViewMode.MONTH,
                visibleMonth = YearMonth.of(2026, 6),
                selectedDate = d10,
                isLoading = false,
                bucketsByDate = mapOf(
                    d10 to DayBucket(d10, historyEntries.filter { it.start.startsWith("2026-06-10") }, 13_800),
                    d09 to DayBucket(d09, historyEntries.filter { it.start.startsWith("2026-06-09") }, 8_100),
                ),
            )
            MonthCalendarView(
                state = state,
                onSelectDate = {},
                onPreviousMonth = {},
                onNextMonth = {},
                onEntryClick = {},
                projects = projects,
                tasks = tasks,
                tags = tags,
            )
        },
        // 4. Calendar — week view with a couple of overlay calendar events.
        Screen("calendar-week") {
            val week = (8..14).map { LocalDate.of(2026, 6, it) } // Mon..Sun
            val mon = week[0]
            val tue = week[1]
            val state = CalendarUiState(
                viewMode = CalendarViewMode.WEEK,
                selectedDate = mon,
                weekStart = DayOfWeek.MONDAY,
                dayCount = 7,
                visibleDays = week,
                isLoading = false,
                overlayEnabled = true,
                bucketsByDate = mapOf(
                    mon to DayBucket(
                        mon,
                        listOf(
                            entry("w1", "Deep work", "2026-06-08T09:00:00Z", "2026-06-08T12:00:00Z", 10_800, taskId = "t1"),
                            entry("w3", "Design review", "2026-06-08T10:00:00Z", "2026-06-08T11:30:00Z", 5_400, taskId = "t2"),
                        ),
                        16_200,
                    ),
                    tue to DayBucket(tue, listOf(entry("w2", "Design system", "2026-06-09T10:00:00Z", "2026-06-09T11:30:00Z", 5_400, taskId = "t2")), 5_400),
                ),
                overlayEvents = listOf(
                    DeviceCalendarEvent(
                        instanceId = 1, eventId = 1, calendarId = "1", title = "1:1 with Alex",
                        startUtcMs = Instant.parse("2026-06-08T13:00:00Z").toEpochMilli(),
                        endUtcMs = Instant.parse("2026-06-08T14:00:00Z").toEpochMilli(),
                        allDay = false, colorArgb = 0xFF3F51B5.toInt(),
                    ),
                    DeviceCalendarEvent(
                        instanceId = 2, eventId = 2, calendarId = "1", title = "Sprint planning",
                        startUtcMs = Instant.parse("2026-06-09T15:00:00Z").toEpochMilli(),
                        endUtcMs = Instant.parse("2026-06-09T16:00:00Z").toEpochMilli(),
                        allDay = false, colorArgb = 0xFFE91E63.toInt(),
                    ),
                ),
            )
            WeekCalendarView(
                state = state,
                onSelectDate = {},
                onEntryClick = {},
                onPrevious = {},
                onNext = {},
                onToday = {},
                projects = projects,
            )
        },
        // 5. Statistics — filter bar + KPI grid + charts.
        Screen("statistics") {
            val summary = StatisticsSummary(
                totalSeconds = 5 * 3600 + 45 * 60,
                entryCount = 18,
                avgSecondsPerDay = 4600,
                billableSeconds = 4 * 3600 + 10 * 60,
                nonBillableSeconds = 1 * 3600 + 35 * 60,
                perProject = listOf(
                    ProjectTotal("p1", "Website Redesign", "#386A20", 12_600),
                    ProjectTotal("p2", "Internal Tools", "#386666", 5_400),
                    ProjectTotal(null, "No project", "#8298AE", 2_700),
                ),
                trend = listOf(
                    TrendBucket("Mon", LocalDate.of(2026, 6, 8), 12_600),
                    TrendBucket("Tue", LocalDate.of(2026, 6, 9), 8_100),
                    TrendBucket("Wed", LocalDate.of(2026, 6, 10), 13_800),
                    TrendBucket("Thu", LocalDate.of(2026, 6, 11), 6_300),
                    TrendBucket("Fri", LocalDate.of(2026, 6, 12), 9_900),
                ),
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatFilterBar(
                    filters = StatFilters(projectIds = setOf("p1")),
                    catalog = StatCatalog(projects = projects, clients = clients, tasks = tasks, tags = tags),
                    onFiltersChange = {},
                    onClearFilters = {},
                )
                KpiGrid(summary)
                DonutChart(
                    slices = summary.perProject.map { hexToColor(it.colorHex) to it.seconds.toFloat() },
                    modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally),
                )
                InteractiveBarChart(
                    bars = summary.trend,
                    barColor = MaterialTheme.colorScheme.primary,
                    onBarClick = {},
                )
            }
        },
        // 6. Time Inbox — a few review issue cards.
        Screen("inbox") {
            val projectsById = projects.associateBy { it.id }
            val issues = listOf(
                InboxIssue(
                    key = "missing:e3",
                    type = InboxIssueType.MISSING_METADATA,
                    startMs = Instant.parse("2026-06-10T13:15:00Z").toEpochMilli(),
                    endMs = Instant.parse("2026-06-10T13:35:00Z").toEpochMilli(),
                    primaryEntry = historyEntries[2],
                    missingFields = setOf(MissingField.PROJECT, MissingField.TAGS),
                ),
                InboxIssue(
                    key = "overlap:e1:e2",
                    type = InboxIssueType.OVERLAP,
                    startMs = Instant.parse("2026-06-10T10:00:00Z").toEpochMilli(),
                    endMs = Instant.parse("2026-06-10T10:30:00Z").toEpochMilli(),
                    primaryEntry = historyEntries[0],
                    secondaryEntry = historyEntries[1],
                ),
                InboxIssue(
                    key = "gap:1",
                    type = InboxIssueType.GAP,
                    startMs = Instant.parse("2026-06-10T12:45:00Z").toEpochMilli(),
                    endMs = Instant.parse("2026-06-10T13:15:00Z").toEpochMilli(),
                ),
                InboxIssue(
                    key = "long:e2",
                    type = InboxIssueType.LONG_DURATION,
                    startMs = Instant.parse("2026-06-09T08:00:00Z").toEpochMilli(),
                    endMs = Instant.parse("2026-06-09T17:00:00Z").toEpochMilli(),
                    primaryEntry = historyEntries[1],
                ),
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InboxHeader(issueCount = issues.size, isRefreshing = false, onRefresh = {}, onOpenSettings = {})
                issues.forEach { issue ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        InboxIssueCard(
                            issue = issue,
                            preventOverlap = true,
                            projectsById = projectsById,
                            zone = zone,
                            onQuickFix = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        },
        // 7. End-of-day review — the guided pane.
        Screen("review") {
            val state = ReviewDayUiState(
                loading = false,
                hasOrganization = true,
                dateEpochDay = LocalDate.of(2026, 6, 10).toEpochDay(),
                totalTrackedSeconds = 6 * 3600 + 30 * 60,
                billableSeconds = 5 * 3600,
                entryCount = 7,
                largestGapSeconds = 45 * 60,
                uncategorizedCount = 1,
                failedSyncCount = 1,
                items = listOf(
                    ReviewItem("running:e1", ReviewItemType.RUNNING_TIMER, "e1", description = "Landing page build", startIso = "2026-06-10T14:00:00Z"),
                    ReviewItem("sync:e4", ReviewItemType.FAILED_SYNC, "e4", detail = "429 rate limited"),
                    ReviewItem("uncat:e3", ReviewItemType.UNCATEGORIZED, "e3", description = "Standup", startIso = "2026-06-10T13:15:00Z", endIso = "2026-06-10T13:35:00Z"),
                ),
                handledIds = emptySet(),
                projects = listOf(
                    ReviewProject("p1", "Website Redesign", "#386A20"),
                    ReviewProject("p2", "Internal Tools", "#386666"),
                ),
            )
            ReviewContent(
                state = state,
                onStop = {},
                onKeepRunning = {},
                onAdjustEnd = {},
                onRetry = {},
                onKeepAsIs = {},
                onAssign = {},
                onReviewAgain = {},
            )
        },
        // 8. Edit/create entry sheet.
        Screen("edit-entry") {
            val editing = entry(
                id = "e2",
                description = "Landing page build",
                startIso = "2026-06-10T10:30:00+00:00",
                endIso = "2026-06-10T12:45:00+00:00",
                durationSeconds = 8100,
                projectId = "p1",
                taskId = "t1",
                entryTags = listOf(tags[0]),
            )
            val backgroundState = TrackingUiState(
                projects = projects,
                tasks = tasks,
                tags = tags,
                timeEntries = historyEntries,
                hasLoadedTimeEntries = true,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    trackingHistoryItems(
                        uiState = backgroundState,
                        groupedEntries = groupedHistory(),
                        onEdit = {},
                        onDelete = {},
                        onDateClick = {},
                    )
                }
                EditTimeEntryDialog(
                    entry = editing,
                    projects = projects,
                    tasks = tasks,
                    tags = tags,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _ -> },
                    existingEntries = historyEntries,
                    preventOverlap = true,
                    inlinePresentation = true,
                )
            }
        },
        // 9. Templates / favorites.
        Screen("templates") {
            val templates = listOf(
                EntryTemplate("tm1", "org1", "Deep work", "p1", "t1", "Focus block", listOf("tag1"), true, true, 0, 0L),
                EntryTemplate("tm2", "org1", null, "p1", null, "Daily standup", emptyList(), false, false, 1, 0L),
                EntryTemplate("tm3", "org1", "Client call", "p3", null, null, listOf("missing-tag"), true, false, 2, 0L),
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                templates.forEachIndexed { index, template ->
                    val resolution = TemplateResolver.resolve(template, projects, tasks, tags)
                    TemplateRow(
                        template = template,
                        resolution = resolution,
                        projectTaskSummary = templateProjectTaskSummary(template, projects, tasks),
                        label = templateDisplayLabel(template, projects),
                        canMoveUp = index > 0,
                        canMoveDown = index < templates.lastIndex,
                        onToggleFavorite = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onEdit = {},
                        onDelete = {},
                    )
                }
            }
        },
    )
}
