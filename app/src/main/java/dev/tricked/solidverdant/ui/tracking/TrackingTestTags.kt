package dev.tricked.solidverdant.ui.tracking

/**
 * Stable Compose testTag constants for the Track screen, defined in production code so that both the
 * UI (via `Modifier.testTag(...)`) and the androidTest robots reference the exact same values.
 *
 * These tags carry no user-facing text and add no visible chrome, so they are inert in production.
 */
object TrackingTestTags {
    const val HISTORY_LIST = "track_history_list"
    const val ENTRY_ROW = "track_entry_row"
    const val START_BUTTON = "track_start_button"
    const val STOP_BUTTON = "track_stop_button"
}
