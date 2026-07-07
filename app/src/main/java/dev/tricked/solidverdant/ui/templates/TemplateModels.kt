package dev.tricked.solidverdant.ui.templates

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate

/**
 * Pure, Android-free logic for favorites & templates (gap analysis #1, #9, #67, #81).
 *
 * Kept separate from the UI and repository so ordering, catalogue resolution and placeholder
 * handling can be unit-tested deterministically. Nothing here logs work data.
 */

/** Editable field set shared by the template editor and the "save as template" affordance. */
data class TemplateDraft(
    val name: String?,
    val projectId: String?,
    val taskId: String?,
    val description: String?,
    val tagIds: List<String>,
    val billable: Boolean,
    val isFavorite: Boolean,
)

/** Resolved field set applied to the tracking form when a template is started with one tap. */
data class TemplateStart(
    val description: String?,
    val projectId: String?,
    val taskId: String?,
    val tagIds: List<String>,
    val billable: Boolean,
)

/**
 * Deterministic ordering for favorites & templates. No contextual/ML ranking (out of scope, see
 * gap analysis #67): favorites keep the user's chosen order, everything else falls back to recents.
 */
object TemplateOrdering {
    /**
     * Order used by the manage screen. Mirrors [dev.tricked.solidverdant.data.local.db.TemplateDao]
     * (favorites first, then the user's [EntryTemplate.sortOrder], then creation order) so the list
     * and any persisted re-order stay consistent.
     */
    fun forManage(templates: List<EntryTemplate>): List<EntryTemplate> =
        templates.sortedWith(
            compareByDescending<EntryTemplate> { it.isFavorite }
                .thenBy { it.sortOrder }
                .thenBy { it.createdAtMs }
                .thenBy { it.id }
        )

    /**
     * Order used by the Track quick-start chip row: pinned favorites first (in the user's chosen
     * order), then non-favorite templates as most-recent-first "recents". [limit] caps the row so a
     * large catalogue does not eagerly compose off-screen chips.
     */
    fun forQuickStart(
        templates: List<EntryTemplate>,
        limit: Int = Int.MAX_VALUE,
    ): List<EntryTemplate> {
        val favorites = templates.asSequence()
            .filter { it.isFavorite }
            .sortedWith(compareBy<EntryTemplate> { it.sortOrder }.thenBy { it.createdAtMs }.thenBy { it.id })
        val recents = templates.asSequence()
            .filterNot { it.isFavorite }
            .sortedWith(compareByDescending<EntryTemplate> { it.createdAtMs }.thenBy { it.id })
        return (favorites + recents).take(limit.coerceAtLeast(0)).toList()
    }
}

/** State of a catalogue reference stored in a template relative to the latest cached catalogue. */
enum class RefStatus {
    /** The template does not reference this field. */
    NONE,

    /** The referenced item exists and is usable. */
    OK,

    /** The item still exists but is archived (project) or done (task) and can no longer be started. */
    ARCHIVED,

    /** The item is no longer in the cached catalogue (deleted or access revoked). */
    MISSING,
}

/**
 * Result of mapping a template onto the current catalogue (gap analysis #81). A template stores IDs
 * only; names, colours and availability are resolved from the latest cached server catalogue. When
 * a referenced item is unavailable the field is dropped from [TemplateStart] so the timer can still
 * be started partially, and the reason is surfaced to the user rather than silently substituted.
 */
data class TemplateResolution(
    val projectId: String?,
    val taskId: String?,
    val tagIds: List<String>,
    val description: String?,
    val billable: Boolean,
    val projectStatus: RefStatus,
    val taskStatus: RefStatus,
    val droppedTagCount: Int,
) {
    /** True when at least one referenced item could not be applied as stored. */
    val hasIssues: Boolean
        get() = projectStatus == RefStatus.ARCHIVED ||
            projectStatus == RefStatus.MISSING ||
            taskStatus == RefStatus.ARCHIVED ||
            taskStatus == RefStatus.MISSING ||
            droppedTagCount > 0

    fun toStart(description: String? = this.description): TemplateStart = TemplateStart(
        description = description,
        projectId = projectId,
        taskId = taskId,
        tagIds = tagIds,
        billable = billable,
    )
}

object TemplateResolver {
    /**
     * Map [template] onto the provided catalogue. [projects] and [tasks] should include archived /
     * done items so this can distinguish "archived" from "deleted"; the caller filters them out of
     * pickers separately.
     */
    fun resolve(
        template: EntryTemplate,
        projects: List<Project>,
        tasks: List<Task>,
        tags: List<Tag>,
    ): TemplateResolution {
        val project = template.projectId?.let { id -> projects.firstOrNull { it.id == id } }
        val projectStatus = when {
            template.projectId == null -> RefStatus.NONE
            project == null -> RefStatus.MISSING
            project.isArchived -> RefStatus.ARCHIVED
            else -> RefStatus.OK
        }
        val resolvedProjectId = if (projectStatus == RefStatus.OK) template.projectId else null

        val task = template.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
        val taskStatus = when {
            template.taskId == null -> RefStatus.NONE
            task == null -> RefStatus.MISSING
            task.isDone -> RefStatus.ARCHIVED
            // A task can only apply when its project resolved cleanly and still matches.
            resolvedProjectId == null || task.projectId != resolvedProjectId -> RefStatus.MISSING
            else -> RefStatus.OK
        }
        val resolvedTaskId = if (taskStatus == RefStatus.OK) template.taskId else null

        val existingTagIds = tags.mapTo(HashSet()) { it.id }
        val keptTagIds = template.tagIds.filter { it in existingTagIds }
        val droppedTagCount = template.tagIds.size - keptTagIds.size

        return TemplateResolution(
            projectId = resolvedProjectId,
            taskId = resolvedTaskId,
            tagIds = keptTagIds,
            description = template.description,
            billable = template.billable,
            projectStatus = projectStatus,
            taskStatus = taskStatus,
            droppedTagCount = droppedTagCount,
        )
    }
}

/**
 * Placeholder substitution for description templates such as "Review: {topic}" (gap analysis #9).
 * Tokens are `{name}` segments; the missing values are requested before a timer starts so the
 * template never submits an unfilled placeholder silently.
 */
object TemplatePlaceholders {
    private val TOKEN = Regex("\\{([^{}]+)}")

    /** Distinct placeholder names in first-seen order (trimmed, blanks ignored). */
    fun extract(description: String?): List<String> {
        if (description.isNullOrEmpty()) return emptyList()
        return TOKEN.findAll(description)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    /**
     * Replace `{name}` tokens with [values]. A token with no (or blank) value is left verbatim so
     * the user still sees which field was skipped rather than an empty gap.
     */
    fun fill(description: String?, values: Map<String, String>): String? {
        if (description.isNullOrEmpty()) return description
        return TOKEN.replace(description) { match ->
            val key = match.groupValues[1].trim()
            val value = values[key]
            if (value.isNullOrBlank()) match.value else value
        }
    }
}
