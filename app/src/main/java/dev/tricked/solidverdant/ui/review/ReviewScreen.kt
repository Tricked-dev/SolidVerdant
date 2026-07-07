package dev.tricked.solidverdant.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R

/**
 * Segments of the Review tab. [Inbox] surfaces entries needing attention (Time Inbox); [ReviewDay]
 * surfaces the compact end-of-day review for a single day.
 */
enum class ReviewSegment { Inbox, ReviewDay }

/**
 * Container for the review-loop home (the "Review" bottom-nav tab). Hosts a segmented control that
 * switches between [InboxPane] and [ReviewDayPane], and an overflow menu with entry points to the
 * reminder settings and template management screens.
 *
 * This is shared scaffolding only. The Inbox agent fills in [InboxPane]; the review/reminders agent
 * fills in [ReviewDayPane] and [ReminderSettingsScreen]; the templates agent fills in the manage
 * templates screen. Navigation callbacks default to no-ops so the container renders standalone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onOpenReminderSettings: () -> Unit = {},
    onOpenManageTemplates: () -> Unit = {},
    onOpenEndOfDayReview: () -> Unit = {},
) {
    var segment by rememberSaveable { mutableStateOf(ReviewSegment.Inbox) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.review_title)) },
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.review_more_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.review_menu_end_of_day)) },
                        onClick = {
                            menuExpanded = false
                            onOpenEndOfDayReview()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.review_menu_reminder_settings)) },
                        onClick = {
                            menuExpanded = false
                            onOpenReminderSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.review_menu_manage_templates)) },
                        onClick = {
                            menuExpanded = false
                            onOpenManageTemplates()
                        },
                    )
                }
            },
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = segment == ReviewSegment.Inbox,
                onClick = { segment = ReviewSegment.Inbox },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.review_segment_inbox))
            }
            SegmentedButton(
                selected = segment == ReviewSegment.ReviewDay,
                onClick = { segment = ReviewSegment.ReviewDay },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.review_segment_review_day))
            }
        }

        when (segment) {
            ReviewSegment.Inbox -> InboxPane()
            ReviewSegment.ReviewDay -> ReviewDayPane()
        }
    }
}

/** Shared placeholder body used by the stub panes/screens until a feature agent replaces it. */
@Composable
internal fun ReviewPlaceholder(textRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
