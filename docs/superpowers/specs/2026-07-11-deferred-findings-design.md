# Deferred review findings — design (2026-07-11)

Design for the six deferred findings from `DEFERRED_FINDINGS.md` / `ENGINEERING_REVIEW.md`.
All product decisions below were made explicitly by the maintainer on 2026-07-11.

Execution order: SV-027 → SV-012 → SV-011 → SV-005 → SV-006 → SV-004, one commit per
finding on `fix/review-findings` (based on `d8477cd`). Each step passes the full local gate
(`spotlessCheck testDebugUnitTest lintDebug assembleDebug`) before the next starts; E2E smoke
runs on the 192.168.50.20 emulators after SV-027, SV-005, and SV-004.

---

## SV-027 — Sync conflict detection & resolution (P0)

**Decision:** full conflict flow — new `CONFLICT` sync state, recovery of the local edit,
Review-inbox conflict card with *Keep mine* / *Keep theirs*, both push and pull directions.

**Constraint discovered:** the Solidtime API exposes **no** `updated_at`, version, or ETag on
time entries (verified against `TimeEntryResource.php` upstream), and there is **no single-entry
GET** (verified against `routes/api.php`). Optimistic concurrency must therefore be
**content-based**: compare the server's current field content against the field content the
local edit was based on.

### Base snapshot

- New nullable column `baseSnapshotJson` on `outbox` (Room v4→v5, `MIGRATION_4_5`).
- Snapshot = canonical JSON of the conflict-relevant fields: `start`, `end`, `description`,
  `projectId`, `taskId`, `billable`, sorted `tagIds`.
- Captured when an UPDATE or DELETE op is enqueued:
  - entry currently `SYNCED` → snapshot of the current entity (last server-acked state);
  - an op for this entry is already queued → reuse that op's base (the edit chain shares one base);
  - entry born locally with its CREATE still queued → base `null` (nothing on the server to
    diverge from; op pushes blind, as today).

### Push side (SyncWorker)

- Before draining UPDATE/DELETE ops for an org, fetch the org's current entries once via the
  existing list endpoint (window sized to cover the queued ops' base `start` values, one request
  per org per sync run) and index by id.
- Per UPDATE/DELETE op with a base:
  - server content == base → safe; PUT/DELETE as today.
  - server content ≠ base → **conflict**: store the server copy on the entity
    (`conflictServerJson`), set `syncState = CONFLICT`, delete the entry's queued ops.
  - entry absent from the fetched window → PUT anyway; a `404` response is treated as
    "deleted on server" → conflict with `conflictServerJson = null` marker.
- No base (null) → push blind, as today.

### Pull side (`TimeEntryDao.applyServerEntries`)

Replaces today's silent skip for `PENDING` rows:
- queued op has a base and server content == base → skip (remote unchanged; our edit will push).
- server content ≠ base (or `pendingDelete` row with diverged server) → mark `CONFLICT` as above.
- no base available (e.g. local CREATE not yet pushed) → keep today's skip.

### Storage

- `SyncState` enum gains `CONFLICT` (stored as TEXT via existing converter — additive, no
  migration needed for the enum itself).
- `time_entries` gains nullable `conflictServerJson` (server copy at detection time, including
  its tag ids; the literal string marker `"DELETED"` means deleted-on-server). Local edit stays
  in the row itself — it *is* the recovery copy.
- `CONFLICT` entries are excluded from outbox pushes (their ops were deleted) and from the
  supersession logic.

### Review surface

- New `InboxIssueType.CONFLICT`, generated from DB state (entries with `syncState = CONFLICT`),
  not from analyzer heuristics; conflict cards are pinned above other issue types and are **not**
  swipe-dismissible — the user must choose.
- Card shows both versions (field-level "mine vs theirs" summary) with actions:
  - **Keep mine** → re-enqueue UPDATE with base = the stored server copy; entry `PENDING`.
    If the server copy is the deleted marker → enqueue CREATE instead.
  - **Keep theirs** → overwrite the entity with the server copy, `SYNCED`, clear conflict.
    If deleted marker → delete the local row.
- Field-level merge is explicitly out of scope for this pass (revisit if conflicts prove common).

### Tests

Unit/Robolectric: push conflict (server diverged), push pass (server == base), pull conflict,
pull skip (server == base), 404-delete conflict, keep-mine and keep-theirs resolution (incl.
delete marker), base reuse across chained edits, migration v4→v5, conflict card presence and
non-dismissibility. Divergence axes per the finding: description, project, billable, tags.

---

## SV-012 — Account-scoped temporal policy (P2)

**Decision:** account policy (profile `timezone` + `week_start`) for all **reporting** surfaces;
**device-local** time only for reminder scheduling. The cached `User` already carries both fields
(`/users/me`), they are just never read.

### Design

- `TemporalPolicy(zone: ZoneId, firstDayOfWeek: DayOfWeek)` + `TemporalPolicyProvider`
  (@Singleton, new `TemporalModule`), exposing `Flow<TemporalPolicy>` and a snapshot accessor,
  derived from `SettingsDataStore` cached auth. Fallbacks: unparseable/absent timezone → device
  zone; unknown `week_start` → Monday. Logged out → device defaults.
- `week_start` mapping: solidtime's lowercase day names → `DayOfWeek`.

### Call-site policy

Account policy (via provider):
- Statistics: `StatisticsViewModel` zone, `StatRange.resolve` (gains `firstDayOfWeek` param),
  `StatisticsAggregator` week bucketing (gains `firstDayOfWeek` param; drops `WeekFields.ISO`).
- Calendar: `CalendarViewModel` (zone, `firstDayOfWeek`, "today"), `MonthCalendarView` /
  `WeekCalendarView` / `WeekCalendarLayout` (zone passed in, no `remember { systemDefault() }`),
  `CalendarDateUtils.entryLocalDate` (explicit zone param).
- Review: `ReviewDayViewModel`, `InboxViewModel`, `InboxPane`, `ReviewDayPane` display,
  `InboxRepository` gap analysis (working window interpreted in the account zone),
  `EntryTrustRules`.
- Entry editing/display in `TrackingScreen` / `TrackingViewModel` (dates shown and picked in the
  account zone so the phone agrees with the web app).

Device-local (unchanged): `ReminderScheduler`, `ReminderWorker` (a 17:00 reminder fires at 17:00
where the user physically is).

### Tests

Policy mapping (timezone strings, week-start strings, fallbacks); aggregator + `StatRange` with
Sunday-start weeks; range clipping with profile zone ≠ device zone (travel case) and across a DST
transition; calendar month grid with Sunday start. Existing tests updated to pass explicit policy.

---

## SV-011 — Per-account template retention (P2)

**Decision:** owner columns + hidden-per-account retention. Account A's templates survive
invisibly while B is logged in and reappear when A returns.

### Design

- Room v5→v6 `MIGRATION_5_6`: `entry_templates` gains nullable `ownerEndpoint TEXT` and
  `ownerUserId TEXT`; index on (`ownerUserId`, `organizationId`).
- Owner identity = Solidtime server endpoint (`AuthDataStore.ENDPOINT`) + `User.id` — the app
  supports multiple server instances, so user id alone is insufficient.
- Backfill: migrations can't read DataStore, so legacy rows keep `NULL` owners and are
  **claimed on next authenticated access** (one `UPDATE ... WHERE ownerUserId IS NULL` stamping
  the current account, run when the template list is first observed while logged in).
- All template queries filter by owner + org; new inserts stamp the owner.
- Logout (`UserCacheCleaner.clear`): replace `database.clearAllTables()` with a dynamic sweep —
  enumerate the DB's tables at runtime and clear all **except** `entry_templates` (list-based
  keep-set, so future tables are cleared by default). Coordinates with the already-landed SV-022
  (sync cancelled before clearing).

### Tests

Migration v5→v6; logout retains template rows while clearing the rest; same-account
logout→login sees its templates; a different account (different user id, and separately a
different endpoint with colliding org id) sees none; NULL-owner claim happens exactly once.

---

## SV-005 — Review inbox horizon, grouping, bulk dismiss (P2)

**Decision:** first-visit horizon prompt + day grouping + bulk dismiss; per-card confirmation
retained for anything that *creates* entries.

### Design

- `InboxSettingsDataStore` gains `horizonStartMs: Long?` + `horizonChosen: Boolean`.
- First Inbox open with `horizonChosen == false`: instead of the issue list, show a picker —
  *Today / This week / Last 30 days / Everything* — which sets the horizon and unlocks the list.
- `InboxAnalyzer` clamps **all** issue types (gaps, overlaps, missing metadata, long duration) to
  windows intersecting `[horizonStart, now]`; badge count follows automatically (same inputs).
  The 370-day gap cap remains as an outer bound.
- Horizon is visible as a chip next to the count ("Since 11 Jun"), tappable → inbox settings
  sheet, where it can be widened/narrowed later.
- Cards grouped by day (sticky day headers, newest first) with a per-day "Dismiss all" (batch
  `InboxDismissalEntity` insert).
- "Dismiss everything before this date" = moving `horizonStartMs` forward — durable (no
  interaction with the 45-day dismissal retention pruning) and reversible from settings.
- No bulk entry creation anywhere. Strings added to en/nl/ja.

### Tests

Analyzer horizon clamping per issue type; first-run picker state; badge count respects horizon;
per-day batch dismissal; horizon move hides older issues and survives dismissal pruning.

---

## SV-006 — Track app bar user/org clarity (P3)

**Decision:** collapse duplicate lines + explicit dropdown affordance.

- `user.name` == org name (trimmed, case-sensitive) → render a single line.
- Switching possible (`memberships.size > 1 && !tracking && !paused`) → org line gets a trailing
  `ArrowDropDown` icon and proper semantics: `Role.Button`, content description
  "Switch organization" (new string, en/nl/ja); otherwise plain `onSurfaceVariant` text with no
  click modifier at all (today it's `primary`-colored even when inert).
- Compose semantics test in the style of `BillableAccessibilityTest` (collapsed-dupe case,
  affordance-visible case, single-membership inert case).

---

## SV-004 — AGP 9.2.1 upgrade (P3)

**Decision:** do the major upgrade now, as the final step, on the already-green tree.

- Bump `androidGradlePlugin` 8.13.2 → 9.2.1; Gradle wrapper and Kotlin/KSP/Hilt/Room/detekt/
  spotless versions raised as required by AGP 9's compatibility matrix (resolved during
  implementation from the release notes, not guessed).
- Address AGP 9 breaking changes (built-in Kotlin integration, DSL removals) as they surface.
- Gate: full local gate green + `connectedDebugAndroidTest` smoke on the .20 emulators. If the
  migration turns into a rabbit hole (> ~2h of chasing), stop, revert the SV-004 commit, and file
  the blocker — the other five findings must not be held hostage.

---

## Verification strategy

- Local (nix develop): `env -u LD_LIBRARY_PATH ./gradlew --no-daemon spotlessCheck
  testDebugUnitTest lintDebug assembleDebug` after every finding.
- Emulators (192.168.50.20 only): push the branch to the `kaisel` remote, check it out there, run
  `connectedDebugAndroidTest` / manual smoke via the two running emulators after SV-027, SV-005,
  and SV-004.
- Sync-conflict E2E is approximated by unit/Robolectric tests (two-actor server edits are not
  reproducible against production); the emulator pass covers regression of normal sync flows.
