---
name: solidverdant-feature-completeness
description: Audit and implement complete SolidVerdant user workflows across Compose, Room, repositories, outbox sync, notifications, and device tests. Use for new roadmap features, feature-complete claims, review findings, or changes that must cover loading, empty, error, retry, offline, recreation, accessibility, localization, and failure recovery.
---

# SolidVerdant Feature Completeness

Treat a feature as a user workflow across layers, not as a visible control or an isolated calculation. Require evidence that the user can discover the feature, understand its state, complete the primary action, and recover safely from expected failure and stale or offline data.

## Establish the contract

1. Read `AGENTS.md` before exploring or changing the feature.
2. Locate `FEATURE_GAP_ANALYSIS.md` with `rg --files -uu`. Read it when present; it is the roadmap contract. If it is absent, record that fact and use the relevant source, tests, and `AGENTS.md` rules without inventing a reduced scope.
3. Inspect the full vertical slice: navigation and screen entry points, ViewModels, repositories, Room entities and DAOs, outbox or WorkManager behavior, remote calls, notifications/widgets/tiles, resource files, and existing unit, screenshot, and device tests.
4. Preserve the user's account, organization, filters, selections, and current entry across navigation and recreation unless the product contract explicitly says otherwise.

If the intended behavior, policy, or acceptance criteria are materially ambiguous, ask for the missing decision before choosing a smaller implementation. Never turn an unclear roadmap requirement into an MVP silently.

## Completeness matrix

Check each applicable row and record evidence from code, tests, or a connected device:

| Area | Required questions |
| --- | --- |
| Discovery | Can a user find the feature from the promised surface, including first use and deep links or notifications? |
| Understanding | Is the active scope, filter, selection, status, stale-data condition, and reason for unavailable actions visible? |
| Primary workflow | Can the user complete the intended action, confirm or cancel it, and safely handle repeated taps? |
| Loading | Is the initial load distinct from refresh, with controls disabled only when necessary and progress explained? |
| Empty | Are first-use, no-data, no-results, invalid-range, and filtered-empty states useful and actionable? |
| Error and retry | Are failures classified in plain language, recoverable where possible, and paired with a working retry path? |
| Success | Does durable success produce clear feedback without leaving stale UI or duplicate operations? |
| Offline and stale data | Does cached data remain useful, and are pending, retrying, failed, conflict, and stale states honest? |
| Durability | Does the workflow survive navigation, recreation, process death, timezone/date boundaries, and partial sync? |
| Catalogue boundaries | Are archived, deleted, unavailable, or changed project/task/tag items handled without data loss? |
| Account boundaries | Are organization changes, logout, permission changes, and account-owned caches handled safely? |
| Accessibility and layout | Does it work with large fonts, narrow phones, tablets, long translations, keyboard navigation, and TalkBack? |

## SolidVerdant invariants

- Keep Room as the local source of truth. A user mutation must update Room and enqueue the corresponding outbox operation transactionally.
- Keep DataStore for small preferences, not growing collections. Keep OAuth secrets in the existing encrypted authentication storage.
- Treat server policy as authoritative. Local overlap and duration checks are warnings unless the server rejects the operation.
- Keep suggestions and corrections deterministic and user-confirmed.
- Clear account-owned caches and presentation data on logout.
- Never log tokens, descriptions, project names, calendar content, or other work data.
- For background and notification features, verify restart, timezone changes, logout, notification permission changes, and the promised action from the notification surface.

## Required verification

Use the smallest test set that proves the whole workflow, then expand it for risk:

1. Add focused domain tests for interval arithmetic, date boundaries, policy decisions, serialization, or state transitions.
2. Add repository/ViewModel tests for durable writes, outbox behavior, retry and conflict recovery, ownership, and error mapping.
3. Add Compose or screenshot coverage for visible states and important layout/accessibility behavior.
4. Add a device E2E test for the happy path and the failure or recovery edge promised by the feature. Build on `E2eRule`, stable production-owned `TestTags`, and `MockSolidtimeServer`.
5. Drive synchronization with `E2eRule.runPendingSync()` inside `waitUntil`; never use `Thread.sleep`. Assert recorded server requests with `callsMatching`, not only the UI projection.
6. Run the pinned Android gate. If a Hilt module or database provider changed, perform the required clean build before device testing.

For read-only review requests, report gaps without changing code. For implementation requests, carry the fix through UI, durable state, background behavior, recovery, resources, and tests; do not stop when the screen merely compiles.

## Report the result

Return a matrix with `area`, `status`, `evidence`, and `remaining gap`. Use:

- **Complete** only when the primary workflow and applicable failure/recovery paths have evidence.
- **Partial** when some layers or states are implemented but required proof is missing.
- **Blocked** when the needed contract, device, backend, or environment is unavailable.
- **Not applicable** only with a short reason tied to the feature's actual scope.

Separate focused validation from the repository-wide gate. Preserve unrelated work and state exactly what remains unverified.
