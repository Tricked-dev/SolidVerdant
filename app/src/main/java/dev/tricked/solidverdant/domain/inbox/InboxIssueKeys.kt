/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.inbox

private const val KEY_ID_INDEX = 2
private const val OVERLAP_SECOND_ID_INDEX = 3
private const val OVERLAP_TIME_TOKENS_START = 4
private const val OVERLAP_TIME_TOKENS_PER_ENTRY = 2

/**
 * Rewrite an [InboxIssue.key] after a time entry changed id (START/CREATE reconciliation
 * replaces a `local-` id with the server id). Keys embed entry ids, so a dismissal recorded
 * against the optimistic id would otherwise stop matching the very same issue after sync.
 *
 * Overlap keys order the pair (and their four time tokens) by id, so replacing an id can flip
 * that order; the rewrite re-sorts to what [InboxAnalyzer] would produce for the new ids.
 * Keys that do not reference [oldId] come back unchanged.
 */
fun rekeyInboxIssueKey(key: String, oldId: String, newId: String): String {
    val parts = key.split(":")
    if (parts.size <= KEY_ID_INDEX) return key
    return if (parts[0] == "overlap") rekeyOverlap(parts, oldId, newId) else rekeySingle(parts, oldId, newId)
}

private fun rekeySingle(parts: List<String>, oldId: String, newId: String): String {
    if (parts[KEY_ID_INDEX] != oldId) return parts.joinToString(":")
    return parts.toMutableList().also { it[KEY_ID_INDEX] = newId }.joinToString(":")
}

private fun rekeyOverlap(parts: List<String>, oldId: String, newId: String): String {
    val timeTokensEnd = OVERLAP_TIME_TOKENS_START + OVERLAP_TIME_TOKENS_PER_ENTRY * 2
    if (parts.size < timeTokensEnd) return parts.joinToString(":")
    val firstId = parts[KEY_ID_INDEX]
    val secondId = parts[OVERLAP_SECOND_ID_INDEX]
    if (firstId != oldId && secondId != oldId) return parts.joinToString(":")
    val firstTimes = parts.subList(OVERLAP_TIME_TOKENS_START, OVERLAP_TIME_TOKENS_START + OVERLAP_TIME_TOKENS_PER_ENTRY)
    val secondTimes = parts.subList(OVERLAP_TIME_TOKENS_START + OVERLAP_TIME_TOKENS_PER_ENTRY, timeTokensEnd)
    val first = (if (firstId == oldId) newId else firstId) to firstTimes
    val second = (if (secondId == oldId) newId else secondId) to secondTimes
    val (lower, upper) = if (first.first <= second.first) first to second else second to first
    return (parts.take(KEY_ID_INDEX) + lower.first + upper.first + lower.second + upper.second + parts.drop(timeTokensEnd))
        .joinToString(":")
}
