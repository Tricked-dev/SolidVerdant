# Android test subtree guidance

Read the repository-root `AGENTS.md` first. These rules add device and E2E-specific constraints for
files under this directory.

- Use `E2eRule` with the real app/Hilt graph. Call `prepare(...)` before `launchApp()` and use the
  deterministic WorkManager controls provided by the rule.
- Drive synchronization with `runPendingSync()` and bounded polling or `waitUntil`; never use
  `Thread.sleep` or unbounded waits.
- Use production-owned test tags re-exported through `e2e/TestTags.kt` and the existing robots.
  Prefer scoped and scrolling selectors over localized text or incidental layout structure.
- Prove durable behavior with recorded server requests or backend snapshots, not only optimistic UI
  state. Invalid-input tests must verify both the UI guard and unchanged server state.
- Use generated catalogue fixtures and IDs from the backend helpers. Never hardcode UUIDs or use a
  personal or production account. Mark a test `@BackendPortable` only when it runs unchanged against
  both mock and isolated live backends.
- Keep test output free of tokens, descriptions, catalogue names, calendar content, and raw remote
  responses. Report instrumentation failures, incomplete runs, and environment blockers honestly.
