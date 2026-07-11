# SolidVerdant continuous engineering review

Last updated: 2026-07-11 (Europe/Amsterdam) — second pass on `master` @ `443b7f7`

## Review status

This is a living review log. Findings are added as code, automated tests, and emulator workflows are exercised. A finding is not considered verified unless its evidence and reproduction surface are named.

This second pass re-verified every prior code-level finding against current `master` (`443b7f7`, "Stabilize API-29 E2E coverage and complete tracking workflows (#3)"), completed the pending sync/outbox and cache-clearing static review (SV-017..SV-028), quantified design drift with a fixed detekt config, and drove the live emulator through the previously-pending walkthrough queue. Two prior static findings — SV-017 (fabricated offline timestamps) and SV-018 (offline start+stop resurrects a running server timer) — were reproduced live on the authorized test account.

Current coverage:

- Pinned project verification suite: passed (`spotlessCheck`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`).
- Static review: authentication/logging, navigation, tracking, review, calendar, statistics, templates, localization complete; sync/outbox conflict, undo, per-entry sync status, and account cache-clearing now complete (see SV-017..SV-028).
- detekt: config repaired (invalid `ignoreExtensionFunction` → `ignoreExtensionFunctions`); the task now runs and reports **748** findings (previously it could not run at all — SV-007).
- Live Android review: on `emulator-5554` using the 8.2 MB minified benchmark build. History search/filters, add/edit/delete/undo, calendar month/week/day, statistics custom ranges/filters/CSV export, review inbox + review day, templates, rotation, large text, and offline start/stop/reconnect all exercised.
- Hosted web surface: not treated as app coverage; the Android emulator is the live-test target.
- Live account: authorized test account (`test@tricked.dev` on `https://solid.tricked.dev`, org "Tricked"). Mutations recorded below; server state left clean.

## Executive assessment

The project has unusually broad automated coverage for an Android time-tracking client and the pinned build is currently green. The architecture generally reflects the stated offline-first contract (Room plus outbox) and there are production implementations for the major Track, Calendar, Statistics, Review, reminder, and template surfaces. The live walkthrough confirmed the feature surfaces are genuinely functional: search/filter, custom-range statistics with CSV export, templates, calendar modes, and the review inbox all work as intended, and the UI reflows correctly under rotation and 1.5× font scale.

The most serious findings, however, are in the offline-sync core — the exact contract the app is built around. **SV-017 and SV-018 were reproduced live**: work captured offline is uploaded with fabricated sync-time timestamps (an 87-second task at 15:30 became a 2m03s entry at 15:33), and an offline start-then-stop cycle resurrects a *running* timer on the server after reconnect while the stop dead-letters unrecoverably. Combined with the still-present OAuth logging (SV-001) and lock-screen notification exposure (SV-013), the P0 set is now dominated by data-trust and privacy defects in the offline path rather than by cosmetics. Design-system drift is now quantified (748 detekt findings, `TrackingScreen.kt` alone carrying 108), and one prior localization finding (SV-003) is retracted as a mis-finding.

## Findings

### SV-001 — OAuth authorization code, state, and full callback URI are logged

- Severity: **Critical / P0 privacy and account security**
- Status: **Still present on `443b7f7`.** `MainActivity.kt:170` logs the full callback URI; `:176` logs 10-char prefixes of code and state; `AuthRepository.kt:75` and `AuthViewModel.kt:141` log the complete authorization URL.
- Evidence:
  - `MainActivity.handleDeepLink` logs the complete callback URI.
  - The same method logs the first ten characters of both the authorization code and state.
  - `AuthRepository` and `AuthViewModel` log the complete authorization URL, which includes OAuth request state and configuration parameters.
- Impact: Debug builds plant `Timber.DebugTree`, so callback credentials and authentication metadata are exposed through logcat. Authorization codes are short-lived but are credentials; truncation does not make them safe. This violates the explicit rule never to log tokens or other sensitive work/account data.
- Recommendation: Remove all URI, authorization URL, code, and state values from logs. Keep only event names and non-sensitive outcome categories. Add a unit/static regression check that rejects logging of OAuth URLs, codes, state, tokens, entry IDs, organization IDs, member IDs, and user IDs.

### SV-002 — Feature UI contains widespread raw design literals outside the theme layer

- Severity: **Medium / P2 maintainability and visual consistency**
- Status: **Still present and now quantified.** With the detekt config repaired (see SV-007), MagicNumber reports 207 findings and grep confirms 5 hardcoded `Color(0x…)` literals outside `ui/theme`: `TrackingScreen.kt:2984,2992,2999` (`0xFFEF4444`), `StatisticsScreen.kt:84` (`0xFF9E9E9E`), `EntryBlock.kt:114` (`0xFF386A20`). The intended color guardrail (`ForbiddenMethodCall`) reports 0 because the plain task has no type resolution — the rule is inert under the documented command.
- Evidence: Raw `dp` values and some hardcoded colors occur throughout feature packages including login, config, templates, review, calendar, statistics, tracking, and tile selection. `StatisticsScreen` declares a raw unknown-project color and multiple screens declare their own sheet shapes, spacing, and icon sizes.
- Impact: This directly conflicts with the anti-drift rules in `AGENTS.md`, makes large-scale visual tuning harder, and increases the chance of inconsistent density and touch treatment between newer and older workflows.
- Recommendation: Run the opt-in `detekt` rules in CI **with type resolution** (`detektMain`/`detektDebug`) so `ForbiddenMethodCall` actually fires, expand `Dimens`/semantic tokens only where a real token is missing, and migrate feature code in focused screen-level changes. `TrackingScreen.kt` (108 detekt findings, 14% of the total) is the priority target.

### SV-003 — ~~`widget_zero_time` is missing from Dutch and Japanese resources~~ (RETRACTED)

- Severity: ~~Low / P3~~ — **Retracted; not a defect.**
- Status: **Mis-finding.** `values/strings.xml:29` declares `widget_zero_time` with `translatable="false"` (`00:00:00`), introduced in `a8d6e1c` before the first review pass. The key is intentionally a locale-neutral, non-translatable time literal, so its absence from `values-nl`/`values-ja` is correct, not a fallback gap. No code changed; the original resource-key count simply didn't account for the `translatable="false"` attribute.
- Recommendation: No action. If a locale-parity test is added, it must exclude `translatable="false"` keys.

### SV-004 — Android Gradle Plugin is outside its tested compile-SDK range

- Severity: **Low / P3 build hygiene**
- Status: Verified during the pinned build.
- Evidence: AGP 8.13.2 reports that it was tested through compile SDK 36.1 while the project compiles against SDK 37.0.
- Impact: The build succeeds, but lint/resource/tooling behavior on SDK 37 is not covered by the plugin vendor's declared compatibility range.
- Recommendation: Upgrade AGP when a compatible pinned version is available; until then track the warning explicitly rather than suppressing it without justification.

### SV-005 — A fresh Review inbox can present a large, undifferentiated historical backlog

- Severity: **Medium / P2 usability and feature adoption**
- Status: Verified on the authorized live account.
- Evidence: The first Review visit showed a badge and heading of 88 items. The visible list begins with separate “Untracked time” cards from 23–24 June, each repeating the same explanatory copy and actions. The current date is 11 July.
- Impact: The screen is technically finite, but a first-time user is asked to process dozens of old working-hour gaps one card at a time before experiencing the intended “caught up” loop. There is no visible grouping, bulk dismissal, age scope, or onboarding explanation in the healthy top-level state. This risks teaching users to ignore the badge.
- Recommendation: On first enablement, ask how far back to scan (today / this week / custom); group consecutive historical checks by day; provide safe “dismiss before this date” and per-day review actions; and keep the active review horizon visible near the count. Preserve individual confirmation for creating entries.

### SV-006 — Track app bar can show indistinguishable user and organization names on adjacent lines

- Severity: **Low / P3 information clarity**
- Status: Verified on the authorized live account.
- Evidence: The Track title rendered “Time Tracking” followed by “Tricked” and another “Tricked” on adjacent lines. Source inspection shows these are the user name and organization name, but neither is labelled in the visual hierarchy.
- Impact: When both values match—or are merely similar—the organization selector looks like duplicated text or a rendering defect. The organization line’s clickability is communicated only by color, and it is disabled when only one membership exists.
- Recommendation: Collapse identical values, or label the organization line and add a dropdown affordance only when switching is possible. Give the selector explicit semantics such as “Organization: Tricked”.

### SV-007 — The advertised detekt anti-drift check cannot run

- Severity: **High / P1 engineering quality gate**
- Status: **Still present on `443b7f7`** (`config/detekt/detekt.yml:37` unchanged). Root cause confirmed and the fix verified in a worktree.
- Reproduction: Run `nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon detekt`.
- Actual: The task exits 1 before analysing source: `style>MagicNumber>ignoreExtensionFunction` “is misspelled or does not exist.”
- Evidence: `config/detekt/detekt.yml` configures `ignoreExtensionFunction: true` against detekt 1.23.8, whose active schema does not accept that property. The valid property is the plural `ignoreExtensionFunctions`. It is the only invalid property; changing that single line lets the task run to completion.
- Quantified once fixed: the task then reports **748 weighted findings** and fails the default `maxIssues: 0` gate. Top rules: MagicNumber 207, FunctionNaming 137 (mostly Composable PascalCase false positives from `buildUponDefaultConfig`), MaxLineLength 128, LongParameterList 60, TooGenericExceptionCaught 48, LongMethod 46. No baseline file exists (`config/detekt/baseline.xml` is referenced in comments but absent). The color anti-drift rule `ForbiddenMethodCall` needs type resolution and silently reports 0 under the plain task (see SV-002).
- Impact: `AGENTS.md` describes detekt as enforcement for the anti-drift rules, but it currently provides no enforcement at all. Because it is deliberately detached from `check`, the broken configuration also remains invisible in the otherwise-green pinned suite.
- Recommendation: Rename the property to `ignoreExtensionFunctions`, run the type-resolution task (`detektMain`) so `ForbiddenMethodCall` fires, commit a baseline to make the 748 pre-existing findings non-blocking, and add a lightweight CI job that proves detekt configuration and execution remain valid.

### SV-008 — Deleting a never-synced entry leaves its create operation queued

- Severity: **High / P1 data trust and offline correctness**
- Status: Verified by repository/outbox control flow; missing regression coverage identified.
- Evidence:
  - `TimeEntryRepository.deleteEntry` deletes a `local-*` Room row, but does not cancel its earlier START or CREATE outbox operation.
  - It then always inserts a DELETE operation for the same local ID.
  - `SyncWorker` drains the START/CREATE first, creates a real server entry, rekeys all later references, and only then attempts DELETE.
- Status update (`443b7f7`): still present at `TimeEntryRepository.kt:302-320`. `SyncWorker.kt:147-155` now short-circuits a DELETE op *still keyed* `local-*` to SUCCESS without a server call, which covers the case where the CREATE never resolved — but it does **not** close the resurrection window, because the START/CREATE has usually already been drained and rekeyed by the time DELETE is processed. **Reproduced live as part of SV-018 below**: an offline-then-deleted entry consumed its own DELETE while the server-side entry survived.
- Impact: An entry deleted while offline can briefly be uploaded despite the user's deletion. If the process stops between operations, or the DELETE is rejected/dead-lettered, the supposedly deleted entry remains on the server. This is exactly the kind of remote resurrection the offline/outbox contract is meant to prevent.
- Recommendation: Delete the unsent START/CREATE and all dependent operations transactionally when deleting a never-synced entry; enqueue no server DELETE. Add repository, worker, and E2E tests proving zero create/delete requests reach the server, including process death after local deletion.

### SV-009 — Statistics resolves membership from a one-shot network call

- Severity: **High / P1 offline correctness and organization isolation**
- Status: **Still present** at `StatisticsViewModel.kt:151` (one-shot `flow { emit(...) }` consumed via `flatMapLatest` at `:154`). Multi-organization live reproduction still requires a second membership.
- Evidence: `StatisticsViewModel.membershipFlow` is `flow { emit(authRepository.getCurrentMembership()) }`. `getCurrentMembership()` calls the network membership endpoint and the flow never reacts to later membership changes.
- Impact:
  - Opening Statistics for the first time while offline produces a false empty state even though Room and the cached auth snapshot can contain usable data.
  - Switching organizations after the Statistics ViewModel is created can leave statistics scoped to the previous organization until the ViewModel is recreated, risking misleading or cross-organization presentation.
- Recommendation: Pass the current organization/member IDs from the authenticated host, as Calendar already does, or expose a reactive account-scoped membership flow backed by cached state. Test offline first-open and live organization switching without route/process recreation.

### SV-010 — Core billable checkboxes expose unlabeled accessibility nodes

- Severity: **High / P1 accessibility**
- Status: **Still present** — re-reproduced this pass. UI Automator on Track reports the Billable `CheckBox` node with `NAF="true"`, `content-desc=""`, `text=""`. Source unchanged: `TrackingScreen.kt:1758-1772` and `:2835-2837`, `EditTimeEntryDialog.kt:272-277` still use a bare `Checkbox` beside a separate non-clickable `Text` (no `toggleable`/`role`/`mergeDescendants`).
- Evidence: UI Automator reports `NAF="true"` for the Billable checkbox on both Track and Add Time Entry. The visible “Billable” text is a separate non-clickable node rather than a merged label for the checkable control.
- Impact: TalkBack and keyboard users can encounter an unnamed checkbox and may not know which state they are changing. Billable status affects reporting and money, so an ambiguous control has higher impact than decorative-label gaps.
- Recommendation: Make the full labelled row the single 48 dp toggle target, merge descendants, expose checkbox role/state plus the localized Billable label, and add Compose accessibility assertions for both checked states in English, Dutch, and Japanese.

### SV-011 — Template retention implementation contradicts its product contract and documentation

- Severity: **Medium / P2 account continuity**
- Status: **Still present** — `TemplateRepository.kt:83-84` documents retention, `AuthViewModel.kt:319` logout → `UserCacheCleaner.kt:27` `clearAllTables()` wipes templates. Live: templates work end-to-end (saved a template from an entry, it appeared in the "Use a template" picker and as a Track "Quick start" chip), so the loss-on-logout is a real regression of working functionality.
- Evidence: `TemplateRepository` states that `entry_templates` is not cleared on logout and cites roadmap retention rule #787 (“Keep for the same account”). `AuthViewModel.logout` calls `UserCacheCleaner.clear`, which calls `database.clearAllTables()` and therefore deletes every template.
- Impact: Users lose locally created favorites/templates on every logout even though they are intended to remain available for the same account. Simply retaining the current rows would be unsafe too, because templates are scoped only by organization ID and lack server-profile/user ownership needed to prevent cross-account exposure.
- Recommendation: Add server-profile and user/account ownership to template identity, selectively clear foreign-account presentation data, and preserve only rows belonging to the returning account. Add logout/login tests for same-account survival and different-account isolation.

### SV-012 — Calendar/statistics/review day boundaries use device settings instead of authoritative account rules

- Severity: **Medium / P2 reporting correctness**
- Status: **Still present.** `ZoneId.systemDefault()` in `InboxRepository.kt:66`, `StatisticsViewModel.kt:101`, `ReviewDayViewModel.kt:55`, `InboxViewModel.kt:80`, `CalendarViewModel.kt:354`, `ReminderWorker.kt:77`, `WeekCalendarLayout.kt:47`. Week-start split unchanged: `StatRange.kt:30,37` + `StatisticsAggregator.kt:244` use `WeekFields.ISO`; `CalendarViewModel.kt:83` uses locale first-day-of-week. Live corroboration: the offline entry (SV-017) was recorded and displayed with device-derived wall-clock times.
- Evidence: Statistics, Inbox, Review Day, history grouping, reminder checks, and calendar layout repeatedly use `ZoneId.systemDefault()`. Statistics ranges and weekly aggregation use `WeekFields.ISO` (Monday) while Calendar separately uses the device locale's week start.
- Impact: A user travelling, using a device timezone different from their Solidtime profile, or using a non-Monday week can see entries assigned to different days and different week totals across screens. The roadmap explicitly calls for timezone-correct inclusive boundaries and configurable/user-owned week rules.
- Recommendation: Introduce one account-scoped temporal policy (reporting zone, first day of week, and day boundary), feed it to every aggregation/filter/reminder surface, and test DST changes, travel timezone changes, Sunday-start weeks, and entries crossing midnight.

### SV-013 — Active-timer notifications expose work descriptions and project/task names on the lock screen

- Severity: **Critical / P0 privacy**
- Status: **Still present** at `TimeTrackingNotificationService.kt:429-444` (contentText from description/project/task), `:488` set, `:498` `VISIBILITY_PUBLIC`; paused (`:546-549,:612`), widget/quick-start (`:733,:808`), and all channels (`:754,:765,:776`) are public; no `publicVersion` anywhere.
- Evidence: `TimeTrackingNotificationService.buildTrackingNotification` places the entry description, project name, and task name in `contentText`, then marks the notification `VISIBILITY_PUBLIC`. Paused and other tracking notifications use the same public visibility pattern.
- Impact: Android may show the complete notification on a secure lock screen according to device policy, exposing exactly the descriptions and project names that the contributor guide classifies as sensitive work data. This can reveal client/work context to anyone who can see the device.
- Recommendation: Use `VISIBILITY_PRIVATE` for work-bearing notifications and provide a generic redacted `publicVersion` (“Timer running”) with no organization, description, project, task, tags, or duration detail. Add notification-content tests that assert the public version is sanitized.

### SV-014 — Instrumentation harness does not handle notification permission and fails six core E2E tests

- Severity: **High / P1 release confidence**
- Status: **Worked around in CI, not fixed.** The production auto-prompt is unchanged (`TrackingScreen.kt:385-392` still requests `POST_NOTIFICATIONS` in `DisposableEffect(uiState.isTracking)`) and the androidTest harness still grants nothing (`E2eRule.kt`/`HiltTestRunner.kt` are grant-free — no `GrantPermissionRule`/`pm grant`). Instead CI now pins the E2E matrix to API 29 (`.github/workflows/build_test.yaml:18`), where `POST_NOTIFICATIONS` does not exist, so the suite is green but the clean-device API 33+ failure mode is unaddressed. Live: on the API-36 emulator the prompt fired exactly as described the moment Start was tapped (see log), intercepting the tap.
- Reproduction: Install the debug and androidTest APKs on a clean emulator and run the documented `am instrument` command without pre-granting `POST_NOTIFICATIONS`.
- Actual: 18 tests execute and 6 fail with “No compose hierarchies found” immediately after a timer becomes active. Production `TrackingScreen` requests notification permission in a `DisposableEffect(uiState.isTracking)`, placing a system permission surface above the app while robots continue querying Compose.
- Affected flows: server-side active entry, continue-last-entry, offline start/sync, start/stop lifecycle, activity recreation, and elapsed timer reactivity.
- Impact: The suite is not clean-device deterministic and the six tests do not currently validate their promised workflows under the documented command.
- Recommendation: Make notification permission an explicit E2E precondition or test dimension: grant it for unrelated timer tests, and add dedicated grant/deny/don't-ask-again tests. Prefer the existing user-invoked notification affordance over automatically prompting at the moment the user starts time capture.

### SV-015 — E2E coverage explicitly stops short of the completed-entry offline workflow

- Severity: **High / P1 test completeness**
- Status: **Still present.** `OfflineCreateSyncE2eTest.kt:29-31` retains the "Left for the next wave" comment; its only test is `startedEntryIsPostedToServerOnSync` (`:42`) and asserts only *that* a POST occurred (`:59-60`), never the timestamp — which is exactly why SV-017 escaped the suite. None of the 11 e2e flow files cover completed-entry CREATE.
- Evidence: `OfflineCreateSyncE2eTest` covers START only. Its own comment calls Add Time Entry → CREATE a “straightforward follow-up” and says it was “Left for the next wave to keep this skeleton focused.”
- Impact: This directly contradicts the repository instruction not to stop at skeleton/MVP coverage. The untested CREATE path includes date/time editing, validation, timezone serialization, completed-entry Room state, outbox payloads, sync reconciliation, and failure recovery—none of which are equivalent to START.
- Recommendation: Add completed-entry happy path, offline/restart/reconnect, server rejection with item retry, repeated-save protection, DST/midnight boundaries, and delete-before-first-sync (SV-008) to the E2E suite.

### SV-016 — Forgotten-timer warning relies on an in-process coroutine delay

- Severity: **High / P1 reliability**
- Status: **Still present** at `TimeTrackingNotificationService.kt:525-539` (`serviceScope.launch { delay(...) }`, in-memory `longWarningJob` at `:64`, `START_NOT_STICKY` at `:131`). `SettingsDataStore.kt:215` persists only the threshold, not a deadline; snooze (`:115`) is memory-only. Live: the drawer confirms "Warn after 4 hours. Stored only on this device." — the setting itself advertises the device-only, non-durable behavior.
- Evidence: `TimeTrackingNotificationService.scheduleLongTimerWarning` uses `serviceScope.launch { delay(...) }`. The service returns `START_NOT_STICKY`; there is no persisted warning deadline or WorkManager/alarm responsible for re-establishing it after process death.
- Impact: If Android kills the process/service before the configured threshold, the promised background warning can disappear until another app/boot refresh happens. “Keep Running” snooze state is also memory-only. This does not meet the roadmap requirement for reliable behavior after restart.
- Recommendation: Persist the next-warning deadline account/entry-scoped, schedule durable work appropriate to the desired timing accuracy, restore/suppress it on boot/login/logout/timezone changes, and test force-stop/process death, reboot, permission revocation, timer stopped elsewhere, and threshold changes.

## Findings — sync/outbox, undo, and account cache (second pass)

These come from the pending sync/outbox static review, traced end-to-end through `SyncWorker`, `TimeEntryRepository`, `OutboxDao`, `TimeEntryDao`, `TrackingViewModel`, `AuthViewModel`, `UserCacheCleaner`, and `TokenAuthenticator`. SV-017 and SV-018 were then reproduced live.

### SV-017 — Offline START/STOP timestamps are fabricated at sync time and overwrite the captured times

- Severity: **Critical / P0 data trust (billing correctness)**
- Status: **Confirmed live on the authorized account.**
- Evidence: `OutboxPayloads.kt:12-19,35` — `StartPayload` carries no `start`, `StopPayload` carries no `end`. `AuthRepository.kt:220-232` sends `start = ZonedDateTime.now()` at API-call time; `:305-314` sends `end = now`. `SyncWorker.kt:110-112,132-133,185-201` then upserts the server entity as `SYNCED`, replacing the locally captured `start`/`end` that `TimeEntryRepository.kt:182,207` stamped at tap time. (Manual `CreatePayload` correctly carries start/end — only START/STOP are affected.)
- Live reproduction: airplane mode ON; started a timer at 15:30:00 local (13:30:00 UTC), stopped at 15:31:27 (≈87 s of work), reconnected 15:33:58. The resulting synced entry recorded **13:33–13:36 (2m03s)** — start = the reconnect moment, end = when the resurrected timer (SV-018) was finally stopped. The real 87-second task at 15:30 was uploaded as a ~2-minute entry placed ~3.5 minutes later. Both duration and placement are wrong; even online, every START/STOP drifts by the sync latency.
- Impact: Silent, systematic corruption of the timestamps that drive reports and billing, invisible to `OfflineCreateSyncE2eTest` (which never asserts timestamps).
- Recommendation: Persist the captured `start`/`end` in the payloads at enqueue time and send them; add payload-timestamp assertions to the offline E2E tests.

### SV-018 — SyncWorker drains a stale in-memory snapshot across ID rekeying; dependent STOP/UPDATE dead-letter and DELETE silently no-ops

- Severity: **Critical / P0 data trust and offline correctness**
- Status: **Confirmed live on the authorized account.**
- Evidence: `SyncWorker.kt:47` captures `outboxDao.peekPending()` once and never re-reads. A successful START/CREATE calls `rekeyReferences(localId, serverId)` (`:185-189`) in the DB only; the in-memory copies keep `timeEntryId = "local-…"`. Subsequent STOP/UPDATE then call the API with the stale `local-…` id (`:130-155`) → 404 → FAIL; DELETE hits the `startsWith("local-")` short-circuit (`:149`) and returns SUCCESS with no server call. Retry/dead-letter write back the whole stale entity (`:61-66,:95`), reverting the rekeyed id so user-initiated retry (`OutboxDao.kt:50-51`) loops on the dead id forever.
- Live reproduction: offline start-then-stop, then reconnect → the Track screen showed a **running** timer (Update/Pause/Stop) despite the offline stop, confirmed live-counting (00:01:19 → 00:01:31 across two dumps). The offline START synced and `reconcile` upserted the server's running (end=null) entry; the STOP dead-lettered as "This change has not reached the server". Each manual Stop tapped on the resurrected timer enqueued another failing STOP (failed-change count grew 1→2→3).
- Impact: A stopped timer runs forever on the server; the failure cannot be resolved via retry; delete-after-offline-edit resurrects entries both locally and server-side (aggravates SV-008). This is the flagship offline flow.
- Recommendation: Re-read each op from the DB immediately before processing (or maintain an `oldId→newId` map applied to the in-memory batch after each rekey), and never write stale entities back in dead-letter/retry updates. Add a worker test for START→STOP→DELETE chains in a single drain.

### SV-019 — Undo delete races the SyncWorker; the undo window only gates the deleter's own sync trigger

- Severity: **High / P1 data trust**
- Status: Verified by control flow; UI-state contradiction observed live.
- Evidence: `TrackingViewModel.kt:1227-1235` enqueues the DELETE op immediately; the 5 s `DELETE_UNDO_WINDOW_MS` only defers *this coroutine's* `requestSync()`. Any other trigger (start/stop/edit at `:898,:1015,:1204`, sync-center retry, WorkManager backoff) drains inside the window. `TrackingScreen.kt:304-312` shows the snackbar with `actionLabel` and no explicit duration → Material3 defaults to `Indefinite`, so undo can be tapped minutes later. Interleavings: (a) undo lands first → `restoreDeleted` makes the entry visible, then the worker deletes it anyway (undo "succeeded" but the entry is gone); (b) worker's `deleteById` first → `undoDelete` re-upserts as `SYNCED` while the server has deleted it → permanent local ghost (SV-020).
- Live note: during the undo window the deleted entry stayed visible in the list showing a "Pending" chip while the snackbar read "Entry deleted" — a contradictory surface consistent with this race and with SV-024.
- Recommendation: Don't enqueue the DELETE until the undo window closes; make the worker re-check the op row transactionally before/after the remote call.

### SV-020 — Server-side deletions never propagate: pull reconciliation is upsert-only

- Severity: **High / P1 offline correctness**
- Status: Verified by source inspection.
- Evidence: `TimeEntryDao.kt:90-100` `applyServerEntries` only upserts; `refreshAll` (`TimeEntryRepository.kt:135-163`) and `loadMonth` (`:81-110`) never remove local `SYNCED` rows absent from the server page. `deleteById` is only ever called from the app's own DELETE op or local delete.
- Impact: An entry deleted on the web or another device persists on the Android client forever — in history, statistics, exports, overlap checks, and the review inbox. With SV-019(b) even the app's own races produce immortal ghosts.
- Recommendation: During `refreshAll`/`loadMonth`, delete `SYNCED`, non-pending local rows inside the fetched range that are absent from the response (range-scoped tombstoning).

### SV-021 — Forced credential-death "logout" leaves the entire account cache in place for the next account

- Severity: **High / P1 privacy / cross-account isolation**
- Status: Verified by source inspection.
- Evidence: `TokenAuthenticator.kt:129-133,142-146` — a dead refresh token runs only `storage.clearTokens()`. `UserCacheCleaner.clear()` has exactly one call site, the explicit `AuthViewModel.logout()` (`:319`); nothing clears Room/DataStore/tile prefs on the forced path or on the next login. `TimeEntryDao.kt:22` scopes history by `organizationId` only, not user.
- Impact: After a server-side session revocation, account A's history/descriptions/templates/inbox remain on disk; a different user B in the same organization logging in on the device sees A's cached entries (`observeVisibleEntries(orgId)`). Violates AGENTS.md's account-cache rule and gap-analysis line 591.
- Recommendation: Run the same account-cache clear on the forced-invalidation path, or fence all caches with a stored account/server identity checked at login.

### SV-022 — Logout neither cancels nor fences the SyncWorker; unsynced work is destroyed silently and an in-flight worker can write after `clearAllTables`

- Severity: **High / P1 data trust**
- Status: Verified by source inspection; live-corroborated logout UX.
- Evidence: No caller of `WorkManager.cancelUniqueWork(SyncScheduler.UNIQUE_NAME)` exists. `AuthViewModel.logout()` (`:311-326`) runs `clearAllTables()` (`UserCacheCleaner.kt:27`) with no sync fence; a sync success straddling the clear re-inserts the old account's entry (`SyncWorker.kt:190,198`). `TrackingScreen.kt:647-650` — logout is a single drawer tap with no confirmation and no unsynced-changes check. Live: confirmed the drawer Logout is a single unconfirmed tap.
- Impact: Logging out with a non-empty outbox silently discards offline-captured time; depending on timing, an in-flight START can re-seed the cleared DB with the previous account's row.
- Recommendation: On logout, cancel + await the unique sync work, then warn/flush if the outbox is non-empty before clearing.

### SV-023 — Hard process death between server commit and op deletion duplicates entries; the persisted `clientId` idempotency key is never used

- Severity: **Medium / P2 data trust**
- Status: Verified by source inspection.
- Evidence: `SyncWorker.kt:101-128` runs the CREATE duplicate-adoption guards only `if (op.attemptCount > 0)`, but `attemptCount` is incremented only after a classified transient failure (`:61-66`). A hard kill after the server commit but before `outboxDao.delete(op)` (`:54`) leaves `attemptCount = 0`, so the rerun POSTs again. `OutboxEntity.clientId` (`OutboxEntity.kt:24-29`) is documented as the idempotency correlator but is written once (`TimeEntryRepository.kt:361`) and never read or transmitted.
- Recommendation: Persist an attempt marker (or increment `attemptCount`) before the network call, and actually use `clientId` in the duplicate-adoption scan.

### SV-024 — `undoDelete` restores entries to a permanent phantom-PENDING state that hides them from server refresh while the UI shows them as synced

- Severity: **Medium / P2 data trust**
- Status: Verified by source inspection.
- Evidence: `deleteEntry` stamps the soft-deleted row `PENDING` (`:308`); `undoDelete`'s in-time branch calls `restoreDeleted(entry.id, existing.syncState)` (`:353`), writing that `PENDING` back. No outbox op remains (the DELETE was cancelled), so nothing flips it to `SYNCED`, and `applyServerEntries` skips `PENDING` rows forever (`TimeEntryDao.kt:94`). Per-entry UI status is derived purely from outbox rows (`TimeEntryRepository.kt:118-132`; `TrackingScreen.kt:843-847`), so the entry shows no pending/failed chip.
- Impact: After delete-then-undo of a synced entry, the entry looks synced but every future `refreshAll` skips it — web edits to it never reach the device again.
- Recommendation: `restoreDeleted` should restore `SYNCED` for server-id rows, in one transaction with the outbox cancellation.

### SV-025 — Retrying a dead-lettered op can replay a stale payload over newer synced state

- Severity: **Medium / P2 data trust**
- Status: Verified by source inspection.
- Evidence: `UpdatePayload` is a full snapshot (`OutboxPayloads.kt:38-47`) applied unconditionally (`SyncWorker.kt:136-145` → `persistSynced` overwrites local with the echo). `resetForRetry` (`OutboxDao.kt:50-51`) revives all dead-lettered ops for an entry regardless of whether later ops for the same entry have since synced.
- Impact: Edit A is rejected; the user fixes it with edit B (syncs); later tapping Retry on the lingering failed chip PUTs A's stale snapshot and reverts B on both sides.
- Recommendation: Drop a dead-lettered UPDATE superseded by a newer successful op for the same entry, or rebuild the retry payload from current Room state.

### SV-026 — Optimistic Room write + outbox enqueue are not atomic; process death produces unsyncable or invisible entries

- Severity: **Medium / P2 data trust**
- Status: Verified by source inspection.
- Evidence: No `withTransaction`/`runInTransaction` anywhere in main source. Every mutation is two-plus separate transactions: `startEntry` (`:188-201`), `stopEntry` (`:208-218`), `updateEntry` (`:223-245`), `createCompletedEntry` (`:275-298`), `deleteEntry` (`:305-319`), `undoDelete` (`:323-355`).
- Impact: Death between `deleteEntry`'s soft-delete upsert (`:308`) and the outbox insert (`:310`) hides the entry forever (`pendingDelete=1` excludes it from list and from `applyServerEntries`) with no op to sync and no recovery UI. The `stopEntry`/`updateEntry` mirror leaves a permanent phantom-PENDING edit with no chip.
- Recommendation: Wrap each optimistic write + outbox insert in a single Room transaction.

### SV-027 — Local-vs-remote edit conflicts are resolved silently by last-write-wins in both directions

- Severity: **Medium / P2 reporting correctness** (roadmap #34 classifies this P0)
- Status: Verified by source inspection.
- Evidence: Pull: `applyServerEntries` silently skips server versions for `PENDING` rows (`TimeEntryDao.kt:94`), no conflict surface. Push: `SyncWorker` UPDATE (`:136-145`) PUTs the full local snapshot with no revision/`updated_at` check, then accepts the echo. `FEATURE_GAP_ANALYSIS.md:247-251` and #80 (`:721-731`) require a preserved recovery copy and a CONFLICT state (gap #79) — none implemented.
- Impact: An offline edit and a concurrent web edit to the same entry silently overwrite one another with no indication to either party.
- Recommendation: Detect that the server copy changed since enqueue (compare fetched vs payload base) and preserve a recovery copy / mark CONFLICT instead of blind overwrite.

### SV-028 — Minor sync-surface inaccuracies

- Severity: **Low / P3**
- Status: Verified by source inspection.
- Evidence:
  - `TrackingScreen.kt:843-847` uses `operations.last().status` per entry, so a dead-lettered FAILED op is masked by any newer PENDING op — the chip reads "pending" over a permanent failure.
  - `SyncWorker.kt:55-69` aborts the whole drain on one transient failure (`Result.retry()`), stalling unrelated entries/organizations until backoff.
  - `SyncWorker.kt:78` leaves `SyncStatusReporter` stuck on `Error` until the next drain even after the failed op is resolved; it is in-memory only and survives logout.
- Recommendation: Surface the worst per-entry op status; isolate per-item failures within a drain; recompute the reporter state on resolution.

### SV-029 — Dead-lettered sync operations cannot be cleared from the UI

- Severity: **Medium / P2 usability and data-trust perception**
- Status: **Observed live on the authorized account.**
- Evidence: After the SV-018 reproduction left 3 dead-lettered STOP ops, Review-day's "Keep as is" advanced the wizard to "All caught up" but did **not** remove the outbox rows. Back on Track, Sync center still showed "3 failed changes", each "This change has not reached the server", offering only **Retry** (which re-fails on the dead `local-` id). There is no dismiss/discard affordance anywhere, so the orphaned ops are permanently stuck. The "Continue last entry" chip also kept pointing at the by-then-deleted entry (stale last-entry pointer).
- Impact: A user who hits SV-018/SV-025 is left with a permanent, unclearable "failed changes" banner and a nagging badge, training them to ignore sync warnings. Combines with SV-028's stuck reporter.
- Recommendation: Make "Keep as is" delete the dead-lettered op it resolves; add an explicit discard action in Sync center; clear the last-entry pointer when its target is deleted.

## Positive observations

- The full pinned verification command passes in under a minute on the current cache.
- There are 67 Kotlin/Java test files across unit and instrumentation source sets, including focused coverage for Room/outbox behavior, migrations, OAuth configuration, synchronization, date/time boundaries, calendar layout, statistics, templates, and offline E2E workflows.
- E2E support includes a deterministic mock Solidtime server and flow-level tests for tracking lifecycle, offline create/sync, editing, pagination, logout, navigation, and large-data scrolling.
- User-facing copy is almost fully mirrored across English, Dutch, and Japanese.
- The app has dedicated shared loading, empty, error, sync, and network state components rather than relying entirely on ad-hoc placeholders.
- **Sync internals examined and found sound** (second pass): the dead-letter design (peek exclusion, attempt cap, CREATE→dependents cascade — `OutboxDao.kt:29,58-59`, `SyncWorker.kt:57-59,87-97`, tested at `SyncWorkerTest.kt:98-173`); retry classification (IOException/429/5xx → RETRY, else FAIL, `SyncWorker.kt:203-208`); `applyServerEntries` single-transaction batching with the pending-edit/pending-delete guard that correctly protects unsynced local edits from server pulls; manual-entry CREATE payloads carrying real start/end; `TokenAuthenticator` single-flight refresh under lock with the Invalid/Transient distinction (`:114-158`); `cancelLatestDelete` targeting only the newest DELETE row. These are correct as designed — the defects above are around them, not in them.
- **Live functional confirmation** (second pass): history search + combined chip filters, add/edit/delete with undo restore, calendar month (Monday-start, per-day totals)/week/day, statistics custom-range picker with "vs previous period" comparison and CSV export, project filter empty state, review inbox and review-day wizard, template save/use/quick-start, landscape two-column reflow, and 1.5× font-scale reflow all worked without crashes.

## Emulator test log

| Time | Build/surface | Action | Result / evidence | Data mutation |
|---|---|---|---|---|
| 2026-07-11 14:14 | `debug` APK | Install on `emulator-5554` | Blocked: `INSTALL_FAILED_INSUFFICIENT_STORAGE`; system image has 416 MB / 7% free and no third-party packages or shared files to remove. | None |
| 2026-07-11 14:17 | `benchmark` APK | Install on `emulator-5554` | Passed; minified APK is 8.2 MB. | None |
| 2026-07-11 14:18 | Login configuration | Set `https://solid.tricked.dev` and supplied client ID; run built-in connection test | Passed with “Solidtime API reached; OAuth configuration is ready.” Repeat taps were not observed during the short request. | Local OAuth configuration only |
| 2026-07-11 14:19 | OAuth sign-in | Start OAuth and return to app | Passed; app loaded the live `Tricked` membership and existing history. The emulator/browser session appears to have completed authorization without requiring credential re-entry. | Authentication session only |
| 2026-07-11 14:19 | Track | Inspect capture form, overlap warning, continue-last-entry, history, bottom navigation, touch bounds, and semantics | Usable at 1080×2400 / 420 dpi. Existing entries remained readable and edit/delete controls exposed labels. Duplicate-looking user/organization lines recorded as SV-006. | None |
| 2026-07-11 14:19 | Calendar | Inspect default three-day week view and empty state | Passed; selected range 11–13 July, Today and period navigation visible, shared “No tracked time” empty state shown. | None |
| 2026-07-11 14:20 | Statistics | Inspect range presets, filters, totals, comparison, and CSV affordance | Passed read-only walkthrough; presets wrap without truncation, active scope is visible, comparison handles zero billable period, export is discoverable. | None |
| 2026-07-11 14:20 | Review / Inbox | Inspect badge, initial backlog, actions, and visual hierarchy | Loaded 88 deterministic review items. Large historical backlog/adoption issue recorded as SV-005. | None |
| 2026-07-11 14:26 | Add Time Entry + offline state | Inspect duration editing, description entry, semantics, airplane-mode banner, and reconnect | Offline banner appeared immediately and the form remained usable. Billable accessibility defect recorded as SV-010. The scripted save did not yield a visible labelled entry, so no create/sync claim is made and no live mutation was confirmed. Connectivity was restored. | No confirmed server mutation |
| 2026-07-11 15:02 | Add Time Entry (online) | Create "SV-review-test-entry" 14:01–15:01; verify via search; then edit desc and revert | Passed — entry created and found via text search (prior "no visible entry" was a scripting miss, not a defect). Entry later deleted. | Created + deleted (net zero) |
| 2026-07-11 15:09 | Edit + delete + undo | Edit 23-cards entry desc (append `-EDITEDSV`), save; delete; undo; revert desc | Edit persisted and showed in list. Delete showed "Entry deleted" snackbar with Undo; **during the window the entry stayed visible with a "Pending" chip** (contradictory state, SV-019). Undo restored it. Desc reverted. | Net zero |
| 2026-07-11 15:14 | Calendar month/week/day | Month view, select day, Day view | Passed — Monday-start weeks, per-day totals ("0h 16m" etc.), Day view showed overlapping entries side by side. | None |
| 2026-07-11 15:17 | Statistics custom range + filters + export | Custom range Jul 6–7, project filter "test", Export CSV | Passed — "vs previous period" comparison, "No tracked time in this range" empty state for the "test" filter, CSV share sheet `solidverdant-timeentries-20260706-20260707.csv`. | None |
| 2026-07-11 15:20 | Review inbox + review day | Inbox (89 items: overlapping + untracked), Review day sync-issue wizard | Passed — Dismiss/Adjust/Add entry actions present; review-day "Keep as is" reaches "All caught up". | None (review actions only) |
| 2026-07-11 15:25 | Templates | Save as template from an entry; Use a template | Passed — template saved, listed in "Use a template", and surfaced as a Track "Quick start" chip. Corroborates SV-011 (works, but wiped on logout). | Template created (test artifact) |
| 2026-07-11 15:26 | Rotation + large text | Landscape; font_scale 1.5 | Passed — landscape reflows Track to two columns; 1.5× text reflows with no truncation/overlap. Settings restored. | None |
| 2026-07-11 15:28 | Offline start/stop → reconnect (SV-017 + SV-018) | Airplane ON; start 15:30:00, stop 15:31:27 (~87 s); reconnect 15:33:58 | **Confirmed both.** Synced entry recorded 13:33–13:36 (2m03s) — sync-time, not capture-time (SV-017). Timer **resurrected running** after reconnect (counted 01:19→01:31); STOP dead-lettered; extra Stop taps grew failed count 1→2→3 (SV-018). | SV-offline-test created, resurrected, then **deleted (server clean)**; local residual = 3 unclearable dead-lettered STOP ops (SV-029) |
| 2026-07-11 15:40 | Cleanup | Delete resurrected entry; Review-day "Keep as is" ×3 | Completed entry deleted from server (history returned to Jul 7). Dead-lettered ops could **not** be cleared via UI (SV-029). Emulator display settings restored (rotation, font_scale 1.0). | Server clean; local dead-letter residual |

## Verification log

| Time | Command | Result |
|---|---|---|
| 2026-07-11 14:05 | `nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` | Passed; 103 tasks, build successful in 54 seconds. AGP/SDK compatibility warning recorded as SV-004. |
| 2026-07-11 14:22 | `nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon detekt` | Failed before analysis because `ignoreExtensionFunction` is not a valid detekt 1.23.8 `MagicNumber` property (SV-007). |
| 2026-07-11 14:24 | Unit-result audit | 235 tests, 0 failures, 0 errors. |
| 2026-07-11 14:24 | Lint-result audit | 172 warnings: 57 unused resources, 31 dependency updates, 27 newer versions, plus correctness/style warnings; no lint errors. |
| 2026-07-11 14:34 | Clean disposable emulator | Created and cold-booted Android 36 AVD with 12 GB userdata; installed both debug and instrumentation APKs. | Passed setup; 11 GB free after boot. |
| 2026-07-11 14:36 | Full instrumentation run, clean permissions | `am instrument -w` | 18 tests ran, 6 failed when the notification permission dialog displaced Compose (SV-014). The shell command itself returned 0 despite JUnit failures, so callers must parse output/result status. |
| 2026-07-11 14:38 | Full instrumentation rerun after `POST_NOTIFICATIONS` grant | `am instrument -w` | Passed: 18 tests, 0 failures in 59.882 seconds. This confirms the first run's six failures are permission-harness interference rather than failures of the underlying timer assertions. |
| 2026-07-11 15:30 (worktree) | `nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon detekt` **after** fixing `ignoreExtensionFunction`→`ignoreExtensionFunctions` | BUILD FAILED in 31s — but now *after* analysis, on the `748 weighted issues` gate (previously died pre-analysis, SV-007). Confirms the config fix and quantifies drift. |
| 2026-07-11 15:34 (worktree) | `nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon lintDebug` | BUILD SUCCESSFUL in 4m 9s; 172 issues, no errors (detail below). |

### Lint detail (172 issues, no errors)

- **UnusedResources 57** (≈55 distinct: 24 strings, 19 drawables, 7 dimens, 3 colors) — the legacy `values/colors.xml`/`dimens.xml` token sets are substantially dead after the Compose migration (e.g. `R.color.colorPrimary`, `R.dimen.fab_margin`, `R.drawable.ic_play`).
- Version-bump noise: GradleDependency 31, NewerVersionAvailable 27, AndroidGradlePluginVersion 5 (the last overlaps SV-004).
- **ApplySharedPref ×2** — `SettingsDataStore.kt:186`, `UserCacheCleaner.kt:31` use synchronous `commit()` (main-thread disk write; notable given the first-frame/perf focus).
- **DefaultLocale ×4** — `TimeTrackingNotificationService.kt:620`, `TimeTrackingWidget.kt:280`, `TrackingScreen.kt:3205,:3343` use `String.format` without an explicit `Locale` (wrong digits/format in e.g. Arabic-digit locales).
- **DataExtractionRules** (security) — `AndroidManifest.xml:20` `allowBackup="false"` is deprecated on Android 12+ and no `dataExtractionRules` XML is supplied, so the backup/device-transfer opt-out is not fully expressed for an app that stores auth tokens and time data.
- InlinedApi (`TimeTrackingTileService.kt:151`, `RECEIVER_NOT_EXPORTED` on minSdk 29), UnusedAttribute ×4 (manifest `localeConfig`, widget target cells), ModifierParameter ×2 (`MonthCalendarView.kt:78,254`), PluralsCandidate ×12.

## Pending review queue

Remaining after this pass:

- TalkBack end-to-end audit (beyond static NAF detection): reminders scheduling behavior on a real force-stop/reboot (SV-016 device test), and multi-organization switching for SV-009/SV-021 (needs a second membership on the test account).
- Add the missing E2E coverage identified in SV-015/SV-017/SV-018 (completed-entry CREATE, offline START/STOP timestamp assertions, START→STOP→DELETE single-drain, delete-before-first-sync).
- Commit the detekt config fix + a baseline and wire type-resolution detekt into CI (SV-007/SV-002).
- The local emulator retains 3 unclearable dead-lettered STOP ops from the SV-018 repro (SV-029); server state is clean. Clearing them requires app-data reset (would log out) — left as live evidence.
