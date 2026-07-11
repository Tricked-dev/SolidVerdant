/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
}

val appVersionName: String = project.findProperty("app.versionName") as String
val appVersionCode: Int = appVersionName.split(".").let { (major, minor, patch) ->
    major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

android {
    namespace = "dev.tricked.solidverdant"
    compileSdk = libs.versions.compileSdk.get().toInt()

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "my-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "dev.tricked.solidverdant"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "dev.tricked.solidverdant.HiltTestRunner"
        versionCode = appVersionCode
        versionName = appVersionName

        // Add package name as string resource for release
        resValue("string", "app_package_name", "dev.tricked.solidverdant")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

            // Override package name for debug builds
            resValue("string", "app_package_name", "dev.tricked.solidverdant.dev")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            // Override package name for release builds
            resValue("string", "app_package_name", "dev.tricked.solidverdant")
        }

        // Release-signed, separately-installable test build for quick device testing of a
        // branch. Named "qa" (not "test") to avoid colliding with the unit-test `src/test`
        // source set; the app id is dev.tricked.solidverdant.test so it sits alongside the
        // release (.) and debug (.dev) installs. Minify is left off for fast, reliable test
        // builds — flip isMinifyEnabled/isShrinkResources on to mirror the exact release artifact.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")

            resValue("string", "app_package_name", "dev.tricked.solidverdant.test")
        }

        // Release-representative build for performance measurement (perf/run_perf.sh): full R8 +
        // resource shrinking like release, but debug-signed so it builds without the release
        // keystore and installs alongside the real app as dev.tricked.solidverdant.bench.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench"
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")

            resValue("string", "app_package_name", "dev.tricked.solidverdant.bench")
        }

        // Instrumentable release-type build for frame-timing measurement. Release Compose (no
        // debug composition overhead) and non-debuggable (so ART honors AOT compilation — the
        // GrapheneOS test phone has no JIT), but unminified so the Hilt/e2e test harness can
        // reach app internals without keep-rule surgery. Built and used only via
        //   ./gradlew -Pperf.testBuildType=perftest assemblePerftest assemblePerftestAndroidTest
        create("perftest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perftest"
            versionNameSuffix = "-perftest"
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")

            resValue("string", "app_package_name", "dev.tricked.solidverdant.perftest")
        }
    }

    // Which build type `assemble<X>AndroidTest`/connected tests target. Default stays debug for
    // normal development; perf/run_perf.sh flips it to perftest for release-like frame timing.
    testBuildType = (project.findProperty("perf.testBuildType") as? String) ?: "debug"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Robolectric needs the merged Android resources/assets/manifest on the JVM classpath so it
    // can inflate themes and load string/plural resources. Required by the Roborazzi screenshot
    // suite in src/test (dev.tricked.solidverdant.screenshots).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("en", "nl", "ja")
    }
    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.RequiresOptIn")
    }
}

// Export Room schemas so migrations can be validated and tested (exportSchema = true).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Detekt: design-drift / static-analysis guardrail.
//
// IMPORTANT: detekt is intentionally NOT wired into the default build path. Applying the
// plugin normally binds the `detekt` task to `check`; we detach it below so `assembleDebug`,
// `check`, and every other routine task stay unaffected. Run it explicitly on demand:
//
//     ./gradlew detekt
//
// The baseline (config/detekt/baseline.xml) is generated separately and is NOT hand-written:
//
//     ./gradlew detektBaseline
//
// It is referenced only when present so a fresh checkout without a baseline still configures.
detekt {
    // Layer our overrides on top of detekt's shipped defaults instead of replacing the ruleset.
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))

    // Guard the baseline: a missing baseline file would otherwise fail configuration.
    val detektBaseline = file("$rootDir/config/detekt/baseline.xml")
    if (detektBaseline.exists()) {
        baseline = detektBaseline
    }
}

// Keep detekt off the default verification path: remove the `detekt` dependency that the
// plugin adds to `check`. (assembleDebug does not depend on check regardless, so it is
// unaffected either way — this just keeps `check` fast and detekt strictly opt-in.)
tasks.named("check").configure {
    setDependsOn(
        dependsOn.filterNot { dependency ->
            val name = when (dependency) {
                is TaskProvider<*> -> dependency.name
                is Task -> dependency.name
                is String -> dependency
                else -> ""
            }
            name.startsWith("detekt")
        },
    )
}

/*
 Dependency versions are defined in the top level build.gradle file. This helps keeping track of
 all versions in a single place. This improves readability and helps managing project complexity.
 */
dependencies {

    // App dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile (app/src/main/baseline-prof.txt) so ART AOT-compiles the
    // startup/scroll hot paths instead of interpreting them on first runs.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    // Architecture Components
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)

    // Networking
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.coil.kt.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DataStore for preferences
    implementation(libs.androidx.dataStore.preferences)

    // Room (offline cache + outbox)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager (background sync)
    implementation(libs.androidx.work.ktx)

    // Hilt WorkManager integration
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.ext.compiler)

    // Browser for Custom Tabs (OAuth)
    implementation(libs.androidx.browser)

    // Hilt
    implementation(libs.hilt.android.core)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(libs.androidx.activity.compose)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation.core)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.accompanist.appcompat.theme)
    implementation(libs.accompanist.swiperefresh)

    debugImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core.ktx)

    // Roborazzi + Compose screenshot testing on the JVM (no device/emulator).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // On-device (instrumented) E2E test harness.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
