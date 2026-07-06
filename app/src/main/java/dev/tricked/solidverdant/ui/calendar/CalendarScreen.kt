package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog

@Composable
fun CalendarScreen(
    organizationId: String,
    memberId: String,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onSaveEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    LaunchedEffect(organizationId, memberId) { viewModel.setOrganization(organizationId, memberId) }
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<TimeEntry?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        if (maxWidth >= 840.dp) {
            TimelineCalendarView(
                state = state,
                onSelectDate = viewModel::selectDate,
                onEntryClick = { editing = it },
            )
        } else {
            MonthCalendarView(
                state = state,
                onSelectDate = viewModel::selectDate,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onEntryClick = { editing = it },
                projects = projects,
                tasks = tasks,
                tags = tags,
            )
        }
    }

    editing?.let { entry ->
        EditTimeEntryDialog(
            entry = entry,
            projects = projects,
            tasks = tasks,
            tags = tags,
            onDismiss = { editing = null },
            onSave = { desc, projectId, taskId, tagIds, billable, start, end ->
                onSaveEntry(entry, desc, projectId, taskId, tagIds, billable, start, end)
                editing = null
            },
        )
    }
}
