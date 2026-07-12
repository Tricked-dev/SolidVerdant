/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.privacy

import java.util.Locale

/**
 * Formats a byte count into a short, human-readable size (binary units). Kept as a pure,
 * locale-stable helper so the storage-usage labels on the privacy screen stay testable without a
 * running framework. Values under 1 KB are shown as whole bytes; larger values use one decimal.
 */
object ByteSizeFormatter {
    private const val UNIT = 1024.0
    private val SUFFIXES = arrayOf("KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes < UNIT) return "$bytes B"
        var value = bytes / UNIT
        var index = 0
        while (value >= UNIT && index < SUFFIXES.lastIndex) {
            value /= UNIT
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, SUFFIXES[index])
    }
}
