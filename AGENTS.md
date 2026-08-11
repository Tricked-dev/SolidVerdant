# SolidVerdant contributor guide

## Project

- Native Android app in `:app`, built with Kotlin, Compose Material 3, Hilt, Room, WorkManager,
  Retrofit, coroutines, and Flow.
- Production code: `app/src/main/java/dev/tricked/solidverdant`.
- JVM/Robolectric/Roborazzi tests: `app/src/test`; device/E2E tests: `app/src/androidTest`.
- Shared UI and tokens: `ui/components` and `ui/theme`. Data and sync: `data/local/db`,
  `data/repository`, `data/remote`, and `sync`.
- Main destinations are Track, Calendar, Statistics, and Review. Tracking also appears in
  notifications, reminders, the Quick Settings tile, widget, shortcuts, and boot handling.

## Invariants

- Optimize for fast, trustworthy time capture and correction, including offline use.
- Room is the local source of truth. A time-entry mutation and its outbox operation must commit in
  one Room transaction through `TimeEntryRepository`; never upload directly from UI or system
  surfaces.
- The server is authoritative. Local overlap, duration, and boundary checks may warn and request
  confirmation but must not invent policy or silently rewrite user data.
- Preserve optimistic state, idempotency keys, bounded retry, visible dead-letter failures, conflict
  snapshots, and explicit conflict resolution. Pulls must not overwrite pending edits or conflicts.
- Scope data by account and organization. Logout uses the established cleanup flow; templates
  intentionally survive and remain isolated by endpoint/user ownership.
- Use DataStore only for bounded preferences and small first-frame caches; collections belong in
  Room. Schema changes require a migration, an updated `app/schemas` export, and migration tests.
- Keep OAuth and PKCE secrets in `AuthDataStore` using `AuthSecretCipher`. Never log or export
  tokens, descriptions, catalogue names, calendar content, or other user work data.
- Suggestions and corrections must be deterministic, explainable, and user-confirmed.

## Implementation

- Complete changes across affected layers and cover applicable loading, empty, error, retry,
  disabled, stale/offline, and success states. Ask when product policy is materially ambiguous.
- Handle relevant timezone/local-day boundaries, running and multi-day entries, archived catalogue
  items, process recreation, account changes, repeated taps, network loss, retries, and conflicts.
- Reuse `ui/components` and `ui/theme`. Feature code uses `MaterialTheme`, `SemanticColors`, and
  `Dimens`; do not add literal colors, raw `sp`, or unexplained `dp` values.
- Put every user-facing string in resources and update English, Dutch, and Japanese together.
- Controls need useful semantics, stable test tags where automation relies on them, and at least a
  48 dp touch target. Check large fonts, long translations, TalkBack/keyboard order, narrow phones,
  and tablets.
- Keep Track cheap to recompose. Memoize derived work, use lazy collections for large data, avoid
  parsing/formatting in list rows, and use `IsoTimes` plus the domain temporal policy.
- After changing Hilt modules, workers, database providers, or schemas, clean-build before trusting
  device results.

## Tests

- Add focused tests for changed behavior and its important failure or recovery path; compilation is
  not sufficient.
- Screenshot tests live under `app/src/test/.../screenshots`; regenerate intentional baselines with
  `./gradlew :app:recordRoborazziDebug`.
- E2E flows use `E2eRule`, the real app/Hilt graph, deterministic WorkManager, and a mock or isolated
  live backend. Call `prepare(...)` before `launchApp()`; drive sync with `runPendingSync()` or
  bounded polling, never `Thread.sleep`.
- Prefer production-owned test tags and screen robots. Prove server writes with recorded requests or
  server snapshots, not only optimistic UI state.
- Mark tests `@BackendPortable` only when they run unchanged against mock and live backends.

## Verification

Run the pinned host gate before handing off code changes:

```sh
devenv tasks run android:gate
```

For connected-device work, run the relevant gate:

```sh
devenv tasks run android:gate:instrumentation
devenv tasks run android:e2e:mock
```

Portable live-backend flows use the disposable local environment only:

```sh
devenv up -d
devenv tasks run solidtime:test
```

Never point the live task at a personal or production account. Device setup and troubleshooting are
in `.agents/skills/solidverdant-android-gate/SKILL.md`; performance procedures are in
`perf/run_perf.sh` and `README.md`. Use `./gradlew spotlessApply` to fix formatting and
`./gradlew detekt` for optional design-drift checks. Report unrun or blocked verification.

## Working tree

- Preserve unrelated modified and untracked files; inspect `git status` and the relevant diff first.
- Keep patches scoped. Do not discard, overwrite, or broadly reformat another contributor's work.
- Do not edit generated build output. Commit required Room schemas and intentional screenshot
  baselines.
