package dev.tricked.solidverdant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic colour tokens layered on top of the M3 [ColorScheme].
 *
 * These are extension vals so any composable can read them as
 * `MaterialTheme.colorScheme.positive`, and they resolve correctly for BOTH the
 * light Verdant scheme and the dark "Neo" scheme. Theme-derived tokens
 * ([negative], [neutral], [syncPending], [syncFailed]) simply re-map to the
 * scheme's existing roles (error / onSurfaceVariant / tertiary) so they stay
 * legible on either ground. [positive] needs a dedicated green — the primary
 * accent is green in light and cyan in Neo, so success must not reuse it — and
 * is picked per ground via [isLight].
 */

/** True when the active scheme is a light ground (used to pick contrast-safe hues). */
val ColorScheme.isLight: Boolean
    get() = background.luminance() > 0.5f

/** Readable success green, distinct from the primary/cyan accent, on both grounds. */
val ColorScheme.positive: Color
    get() = if (isLight) Color(0xFF2E7D32) else Color(0xFF3DFF88)

/** Negative / decrease. Maps to the scheme error role. */
val ColorScheme.negative: Color
    get() = error

/** Neutral / unchanged. Maps to onSurfaceVariant. */
val ColorScheme.neutral: Color
    get() = onSurfaceVariant

/** Sync PENDING / RETRYING accent. Tertiary family — never the primary accent. */
val ColorScheme.syncPending: Color
    get() = tertiary

/** Sync FAILED accent. Error family. */
val ColorScheme.syncFailed: Color
    get() = error

/**
 * Convenience accessor bundling the semantic tokens, for call sites that want a
 * single value to destructure. Equivalent to reading the extension vals above.
 */
data class SemanticColors(
    val positive: Color,
    val negative: Color,
    val neutral: Color,
    val syncPending: Color,
    val syncFailed: Color,
)

val semanticColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = with(MaterialTheme.colorScheme) {
        SemanticColors(
            positive = positive,
            negative = negative,
            neutral = neutral,
            syncPending = syncPending,
            syncFailed = syncFailed,
        )
    }
