# SolidVerdant contributor guide

## Project shape

- Native Android app written in Kotlin and Jetpack Compose.
- Application code lives in `app/src/main/java/dev/tricked/solidverdant`.
- Unit tests live in `app/src/test`; device tests live in `app/src/androidTest`.
- Room is the local source of truth. Writes should update Room and enqueue an outbox operation.
- DataStore is for small preferences, not growing collections.
- OAuth secrets remain in the existing encrypted authentication storage.

## Product rules

- Optimize for fast, trustworthy time capture and correction.
- Server policy is authoritative; local overlap and duration checks are warnings unless the server says otherwise.
- Suggestions and corrections must remain deterministic and user-confirmed.
- Never log tokens, descriptions, project names, calendar content, or other work data.
- Account-owned caches and presentation data must be cleared on logout.

## Feature completeness

- Build new features as complete, production-quality product workflows, including interaction, state, accessibility, offline behavior, and edge-case handling. Do not intentionally stop at an MVP, demo, placeholder, or happy-path-only implementation.
- Use `FEATURE_GAP_ANALYSIS.md` as the product contract for the intended scope and behavior of roadmap features. Implementing the shortest visible interpretation of a heading is not sufficient when its supporting paragraphs describe a larger workflow.
- A feature is complete only when the user can discover it, understand its current state, finish the primary workflow, recover from failure, and safely use it with offline or stale data. It must include appropriate loading, empty, error, retry, disabled, and success states rather than only the happy path.
- Finish the workflow across layers. UI controls must be backed by durable state and repository behavior; Room/outbox changes must surface useful status and recovery in the UI; background or notification features must navigate to or perform the promised action.
- Preserve user context and make state legible. Show active filters and selections, provide clear/reset paths, retain relevant state across navigation or recreation, and explain why an action is unavailable or failed.
- Design the real interaction, not a demo control. Check narrow phones, large fonts, long translated strings, large datasets, keyboard and TalkBack traversal, confirmation and cancellation, and accidental repeated taps.
- Handle boundaries explicitly: timezone and date boundaries, running and incomplete entries, archived or deleted catalogue items, account and organization changes, process death, network loss, retries, conflicts, and partial sync.
- Do not call a feature complete based only on compilation or a unit test for its core calculation. Add focused domain tests and Compose or device tests for the user workflow and important failure/recovery paths, then exercise the feature on the connected device when practical.

Concrete examples from the current roadmap:

- Search and advanced history filters are not complete with a search box and several chips. Complete behavior covers every promised field and filter, visibly summarizes active conditions, offers easy clearing and useful presets, handles large catalogues without eager composition, and produces correct results for cached/offline and incomplete server history.
- Custom statistics ranges are not complete when a date picker changes locally cached charts. Complete behavior includes all documented shortcuts, inclusive and timezone-correct boundaries, invalid/empty-range handling, a clear active scope, and server-backed filtered or aggregate data when Room does not contain the requested period.
- Per-entry sync state is not complete with a global pending/failed count. Each affected entry needs a synced, pending, retrying, or failed state, a plain-language detail surface, and a working item-specific retry path; the sync center additionally needs last-success and retry context.
- Undo delete is not complete when it only cancels an unsent outbox operation. It also needs defined behavior after synchronization has begun or completed, recreation from cached data where safe, clear feedback when restoration is impossible, and tests for each outbox state.
- Forgotten-timer protection is not complete with an in-screen warning. The configured threshold must drive reliable background notification behavior, and Stop, Keep Running, and Adjust End Time must work from the promised surfaces and remain correct after restart, timezone change, logout, and notification-permission changes.
- A connection test is not complete when it merely accepts or rejects an endpoint. It needs progress and repeat-tap protection, safe actionable failure categories, successful capability/authentication validation at the intended level, and must never expose credentials or raw sensitive responses.

If the intended behavior, product policy, or acceptance criteria needed for a complete implementation are missing or materially ambiguous, ask the user for the required details before choosing a reduced scope. Do not silently substitute an MVP, leave placeholder behavior, or remove or weaken roadmap requirements to make an implementation appear finished.

## UI and accessibility

- Keep the Track screen cheap to recompose; its elapsed timer updates every second.
- Memoize history filtering and aggregation using only the data they depend on.
- Avoid eagerly composing large project, task, or tag collections.
- Use string resources for every user-facing label and update English, Dutch, and Japanese together.
- Interactive controls need stable content descriptions and at least a 48 dp touch target.
- Layouts must remain usable with large font/display scaling and keyboard or TalkBack navigation.

### Anti-drift rules for feature UI

These keep feature screens consistent with the shared design system. They are enforced best-effort by detekt (`./gradlew detekt`, opt-in and outside the default build).

- Compose from the shared kit. Build screens from the components in `ui/theme` and `ui/components`; do not fork bespoke variants of things the kit already provides.
- No hardcoded design values in feature code. Never write `Color(0x..)`, raw `fontSize = ..sp`, or magic `..dp` in feature packages. Pull spacing and sizing from `Dimens`, colors from `MaterialTheme.colorScheme` / `SemanticColors`, and type from `MaterialTheme.typography`. Raw literals belong only in `ui/theme`, where the tokens are defined.
- Reuse before you build. Check `ui/components` first; when a component genuinely does not exist, add the new shared piece to `ui/components` rather than inlining it in a screen.
- Progressive disclosure. The default, healthy state shows nothing extra — no always-on chrome, counters, or banners that bloat the screen. Power features live behind an affordance (icon, toggle, or sheet) and appear only when they apply.
- Use the shared status views. Render loading, empty, and error states with the shared `LoadingState`, `EmptyState`, and `ErrorState` from `ui/components` instead of re-inventing per-screen placeholders.

## Formatting

Spotless (ktlint + MPL license headers from `spotless/`, max line length 140) is the formatting
authority. Run `./gradlew spotlessCheck` to verify and `./gradlew spotlessApply` to fix before
committing. Composables are exempt from function-naming; everything else follows ktlint defaults.
Renovate (`.github/workflows/renovate.yml` + `renovate.json`) keeps dependencies current with the
built-in `GITHUB_TOKEN` and explicit contents/issues/pull-request write permissions. GitHub may
hold CI runs created by that token for maintainer approval; use an App or PAT token only when
fully automatic, no-approval CI triggering is required.

## Verification

Use the pinned development environment:

```sh
nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The flake pins JDK 21 plus SDK platforms 36/37 and build-tools 35, so the JVM test and Roborazzi
screenshot suites run on any machine with nix (on NixOS, point
`android.aapt2FromMavenOverride` in `~/.gradle/gradle.properties` at the nix SDK's aapt2).

For the connected development device, install `app-debug.apk`; its package is `dev.tricked.solidverdant.dev`. Run instrumentation with:

```sh
adb shell am instrument -w dev.tricked.solidverdant.dev.test/dev.tricked.solidverdant.HiltTestRunner
```

When Hilt modules or database providers change, perform at least one clean build before device testing to avoid stale generated classes.

## On-device E2E tests

- Flows live in `app/src/androidTest/.../e2e/flows`, built on `E2eRule` (mock solidtime backend +
  seeded login + deterministic WorkManager), screen robots in `e2e/robots`, and stable testTags
  re-exported through `e2e/TestTags` (tag constants are owned by production code, e.g.
  `TrackingTestTags`). Match on tags or entry data, never on localized chrome; resolve localized
  labels through resources when text matching is unavoidable (e.g. the undo snackbar action).
- Drive sync deterministically with `E2eRule.runPendingSync()` inside a `waitUntil`; never
  `Thread.sleep`. `MockSolidtimeServer` honors `limit`/`offset` paging and records every request
  (`callsMatching`) for payload assertions — assert what reached the server, not just what the UI
  shows.
- New user-visible flows need an e2e test covering the happy path plus the failure/recovery edge
  that AGENTS.md's feature-completeness rules promise (undo, offline, recreation, cross-device
  state).

## Performance

- `./perf/run_perf.sh <label>` (run on the build machine with the test phone attached) measures
  cold startup of the minified `benchmark` build and frame timing of the instrumented stress world
  against the `perftest` build; results land in `perf-results/`. Compare medians across its
  repeated passes.
- The GrapheneOS test phone has no ART JIT: uncompiled code runs interpreted forever, so keep the
  baseline profile (`app/src/main/baseline-prof.txt`) covering app code, and AOT-compile sideloaded
  builds (`cmd package compile -m speed -f <pkg>`).
- Keep per-emission work off the main thread (the tracking pipeline runs on Dispatchers.Default),
  batch multi-row Room writes into one transaction (`applyServerEntries`), and avoid per-row
  `ZonedDateTime.parse`/formatter construction in list rows (use `IsoTimes`).

## Working-tree care

- Preserve unrelated user changes and untracked roadmap/specification files.
- Do not introduce destructive database migrations without an explicit product decision.
- Add focused tests for date boundaries, interval arithmetic, outbox behavior, and serialization changes.
