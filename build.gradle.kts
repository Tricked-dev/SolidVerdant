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
            ),
        )
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
        trimTrailingWhitespace()
        endWithNewline()
        // OBTAINIUM_ADD_APP_URL is a 400+ char URL-encoded deep link that cannot be wrapped;
        // @Suppress annotations are not honored by the spotless ktlint step, so scope the
        // exemption here instead.
        suppressLintsFor {
            step = "ktlint"
            shortCode = "standard:max-line-length"
            path = "app/src/main/java/dev/tricked/solidverdant/ui/tracking/TrackingScreen.kt"
        }
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
