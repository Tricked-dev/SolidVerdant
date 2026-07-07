package dev.tricked.solidverdant.e2e

import dev.tricked.solidverdant.ui.tracking.TrackingTestTags

/**
 * Central registry of stable Compose testTags used by the E2E robots.
 *
 * Prefer matching on these tags over localized text so tests survive copy/translation changes.
 * The values are owned by production code ([TrackingTestTags]) and re-exported here so tests have a
 * single import; the UI applies the same constants via `Modifier.testTag(...)`.
 */
object TestTags {
    const val TRACK_HISTORY_LIST = TrackingTestTags.HISTORY_LIST
    const val TRACK_ENTRY_ROW = TrackingTestTags.ENTRY_ROW
    const val TRACK_START_BUTTON = TrackingTestTags.START_BUTTON
    const val TRACK_STOP_BUTTON = TrackingTestTags.STOP_BUTTON
}
