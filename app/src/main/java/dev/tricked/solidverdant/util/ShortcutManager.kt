/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.service.TimeTrackingTileService
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity

/**
 * Manages dynamic shortcuts for quick access to recent projects
 */
object ShortcutManager {

    private const val MAX_SHORTCUTS = 4
    private const val SHORTCUT_ID_PREFIX = "project_"

    const val EXTRA_PROJECT_ID = TimeTrackingTileService.EXTRA_PROJECT_ID
    const val EXTRA_PROJECT_NAME = TimeTrackingTileService.EXTRA_PROJECT_NAME

    /**
     * Update dynamic shortcuts with recent projects
     */
    fun updateRecentProjects(context: Context, recentProjects: List<Project>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return // Dynamic shortcuts require API 25+
        }

        val shortcuts = recentProjects
            .take(MAX_SHORTCUTS)
            .map { project ->
                createProjectShortcut(context, project)
            }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * Create a shortcut for a specific project
     */
    private fun createProjectShortcut(
        context: Context,
        project: Project
    ): ShortcutInfoCompat {
        val intent = Intent(context, ProjectSelectionActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_PROJECT_ID, project.id)
            putExtra(EXTRA_PROJECT_NAME, project.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfoCompat.Builder(context, "$SHORTCUT_ID_PREFIX${project.id}")
            .setShortLabel(project.name)
            .setLongLabel("Start tracking: ${project.name}")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_timer))
            .setIntent(intent)
            .build()
    }

    /**
     * Clear all dynamic shortcuts
     */
    fun clearShortcuts(context: Context) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    /**
     * Push a shortcut for immediate pinning (Android O+)
     */
    fun pushShortcut(context: Context, project: Project) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcut = createProjectShortcut(context, project)
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }
}
