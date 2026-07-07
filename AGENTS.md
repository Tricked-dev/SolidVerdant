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
