---
name: solidverdant-android-gate
description: Run and report SolidVerdant's pinned Android verification, clean-build, APK installation, and instrumentation workflows. Use when validating a Solid Time change, preparing a commit or PR, testing on the connected device, checking Hilt or Room changes, or reporting whether verification is green, failed, partial, or blocked.
---

# SolidVerdant Android Gate

Run the repository's pinned verification commands and preserve the distinction between a passing gate, a scoped failure, an incomplete run, and an environment blocker. Do not replace the pinned environment with an unverified local JDK, SDK, emulator, or Gradle invocation.

## Before verification

1. Work from the repository root and inspect `git status --short --branch`. Preserve unrelated tracked and untracked work.
2. Read `AGENTS.md` and the current `devenv.nix` task definitions before choosing a command. Treat those files as the source of truth for the environment and package names.
3. Classify the request:
   - Use the normal gate for source, resource, or test changes.
   - Add a clean build when Hilt modules, generated bindings, the Room database provider, migrations, or database providers changed.
   - Add device instrumentation only when the user requests connected-device proof or the feature's contract requires it.
4. Never print tokens, OAuth configuration, work descriptions, project names, calendar content, or raw sensitive responses while collecting evidence.
5. Do not stop or relaunch an active user process without explicit permission. Installing an APK can alter the app process; state that boundary before doing it when the device is in active use.

## Pinned repository gate

Run:

```sh
devenv tasks run android:gate
```

The task currently runs the equivalent of:

```sh
env -u LD_LIBRARY_PATH ./gradlew --no-daemon \
  spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Do not call the gate green from compilation alone. Confirm that every task completed successfully and inspect the final Gradle result.

`detekt` is intentionally opt-in and is not part of `android:gate`. Run it separately when requested or when the change is specifically a static-analysis cleanup; report that result separately.

## Clean-build rule

When Hilt modules, generated components, Room providers, schemas, migrations, or database construction changed, run a clean build inside the pinned environment before device testing:

```sh
devenv shell -- env -u LD_LIBRARY_PATH ./gradlew --no-daemon \
  clean assembleDebug assembleDebugAndroidTest
```

Then rerun `devenv tasks run android:gate`. Do not use a stale successful build as device proof after generated classes or database providers changed.

## APK installation and device instrumentation

Prefer the repository task because it validates device selection, builds both APKs, installs them, and runs the mock-backend suite:

```sh
devenv tasks run android:e2e:mock
```

The development package is `dev.tricked.solidverdant.dev`, and the instrumentation package is `dev.tricked.solidverdant.dev.test`. For a direct, already-authorized device run, install `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, then run:

```sh
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w \
  dev.tricked.solidverdant.dev.test/dev.tricked.solidverdant.HiltTestRunner
```

Use a physical, authorized device. The repository's `devenv.nix` rejects emulators for these E2E tasks and requires `ANDROID_SERIAL` when more than one physical device is connected. Do not silently substitute an emulator or another device.

For the local Solidtime backend, use the real-backend task only when requested and when the local container/database workflow is in scope:

```sh
devenv tasks run solidtime:test
```

`solidtime:test` starts the official `solidtime/solidtime:latest` API with an isolated PostgreSQL
database, resets the disposable account, builds and installs both debug APKs, and runs only
`@BackendPortable` tests. The reset also creates the disposable `Live Test Project`, `Live Test
Task`, and `Live Test Tag` catalogue used by metadata-edit flows. The generated project, task, tag,
and time-entry IDs are not stable; discover them through the E2E backend instead of hardcoding
UUIDs. The API uses its testing environment so the production per-user request throttle does not
turn a rapid portable suite into a false failure; this does not replace focused client tests for
429 handling.

The live session is written with restrictive permissions under `.devenv/state/solidtime`, copied
through temporary device storage into the app's private files, and removed from the device during
cleanup. Never print that file or pass its token through instrumentation arguments. The task uses
`adb reverse` for the local API at `127.0.0.1:18080` and requires one authorized physical device;
it rejects emulators.

Portable Track-flow authors should call `prepare(...)` before `launchApp()`, use
`catalogSnapshot()`/`catalogFixture()` for generated metadata IDs, and assert mutations through
`awaitServer(..., driveSync = true)`. Use production-owned test tags and scoped/scrolling robots;
invalid-input flows must verify both the UI guard and unchanged server state. Keep mock PUT handling
in sync with Solidtime, including tags.

## Instrumentation result parsing

Treat the JUnit/instrumentation report as authoritative. A zero shell exit code is not sufficient if the output contains test failures.

1. Capture the complete `adb shell am instrument -w` output instead of relying on a truncated terminal view.
2. Check the JUnit summary and instrumentation status for failed tests, errors, incomplete execution, or an instrumentation failure marker.
3. If output is truncated, the device disconnects, or the result cannot establish that all requested tests ran, classify the result as partial or blocked rather than green.
4. Report the first failing test and its relevant failure text, while redacting sensitive data. Do not paste an entire log when a short evidence excerpt is enough.

When the task wrapper hides the instrumentation transcript, run the same already-authorized
instrumentation command directly so the JUnit summary is visible. Do not call the run green from
the wrapper's exit code alone.

Common environment-only workarounds:

- A restricted sandbox may make Podman's `/run/user` state read-only. Rerun the normal `devenv`
  task with approved elevated container permission.
- A restricted sandbox may also block the Gradle distribution lock or cache. Rerun the pinned
  `devenv` task with approved elevated build permission; do not switch JDK, SDK, emulator, or
  Gradle versions.
- If catalogue lookup fails, reset the isolated Solidtime server and retry. Never use hardcoded
  IDs or a personal account to make a live test pass.

## Status reporting

Use exactly one top-level status for each requested verification scope:

- **Green**: every required command completed, all declared tests passed, and any requested device proof was parsed successfully.
- **Failed**: the scoped command reached the relevant code and reported a failure. Name the failing task or test.
- **Partial**: some requested checks passed, but the run was interrupted or a required check was not performed. Do not describe this as a passing gate.
- **Blocked**: the environment prevented the check from meaningfully running, such as an unauthorized device, missing pinned tooling, unavailable container, or unrelated pre-existing failure that prevents the scoped result. Name the blocker and the next actionable check.

Finish with the commands actually run, the status for each scope, and any scope that remains unverified. Leave the working tree otherwise unchanged.
