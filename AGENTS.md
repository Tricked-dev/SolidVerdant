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

## Verification

Use the pinned development environment:

```sh
nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

For the connected development device, install `app-debug.apk`; its package is `dev.tricked.solidverdant.dev`. Run instrumentation with:

```sh
adb shell am instrument -w dev.tricked.solidverdant.dev.test/androidx.test.runner.AndroidJUnitRunner
```

When Hilt modules or database providers change, perform at least one clean build before device testing to avoid stale generated classes.

## Working-tree care

- Preserve unrelated user changes and untracked roadmap/specification files.
- Do not introduce destructive database migrations without an explicit product decision.
- Add focused tests for date boundaries, interval arithmetic, outbox behavior, and serialization changes.
