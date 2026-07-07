/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

// Formatting/licensing guardrail: MPL license headers (templates in spotless/), ktlint
// formatting, and whitespace hygiene. Run `./gradlew spotlessCheck` to verify,
// `./gradlew spotlessApply` to fix.
spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.5.0").editorConfigOverride(
            mapOf(
                // Composables are PascalCase functions by convention.
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                // Compose + Hilt code regularly exceeds strict line limits; keep the hard
                // wrap generous and let reviewers judge readability.
                "max_line_length" to "140",
                // Naming/file-layout opinions that would force churn on existing code
                // (widget INSTANCE holders, route constants, parser file names).
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_standard_backing-property-naming" to "disabled",
                "ktlint_standard_filename" to "disabled",
            ),
        )
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
