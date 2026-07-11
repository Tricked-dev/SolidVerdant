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
- Snapshot = canonical JSON of the conflict-relevant fields: `start`, `end` (both stored as
  **epoch millis**, parsed leniently from the entity's ISO strings so server formatting variance
  — offset form, fractional seconds — cannot fabricate conflicts), `description` (null ≡ ""),
  `projectId`, `taskId`, `billable`, sorted `tagIds`. All comparisons use this parsed form —
  with one refinement (adopted during implementation): an *unparseable* timestamp keeps its raw
  string and compares by raw equality, so a malformed server value fails toward a visible
  conflict instead of masking one via `null == null`.
- Captured for **STOP, UPDATE, and DELETE** ops (START/CREATE create server state and need no
  base). Rule, applied inside the same transaction that enqueues the op, **before** the local
  mutation is written to the entity:
  1. a queued op for this entry already carries a base → reuse the oldest such base (an offline
     STOP→UPDATE chain shares the pre-stop base);
  2. else a queued START/CREATE exists for the entry → base `null` (born locally; nothing on the
     server to diverge from; pushes blind as today);
  3. else → snapshot the entity's **pre-mutation** content (the last server-acked content — this
     covers the real flows: `updateEntry` snapshots before upserting the edit, `stopEntry` before
     writing `end`, and the soft-delete path snapshots the untouched content even though
     `softDeleteLocal` has already flipped the row to `PENDING`).

### Push side (SyncWorker)

- Before draining STOP/UPDATE/DELETE ops for an org, fetch the org's current entries via the
  existing paginated list call (reusing the existing 250/page loop), over the window
  `[min(base starts, local starts) − 1 day, now + 1 day]`, and index by id. One windowed fetch
  per org per sync run; on fetch failure the ops stay queued and retry as today.
- Per STOP/UPDATE/DELETE op with a base:
  - server content == base → safe; PUT/DELETE as today.
  - server content ≠ base → **conflict**: store the server copy on the entity
    (`conflictServerJson`), set `syncState = CONFLICT`, delete **all** of the entry's queued ops.
  - entry absent from the fully-paginated window, or the push returns `404` → treated as
    "deleted on server" → conflict with the `"DELETED"` marker. (Residual, documented risk: a
    server edit that moved the entry's `start` more than a day outside the window shows up as a
    deleted-on-server conflict rather than an edit conflict — wrong flavor, but visible and
    losslessly resolvable, never a silent overwrite.)
- No base (null) → push blind, as today (STOP without a base keeps today's dead-letter behavior
  on 404).

### Pull side (`TimeEntryDao.applyServerEntries`)

Replaces today's silent skip for `PENDING` rows:
- queued op has a base and server content == base → skip (remote unchanged; our edit will push).
- server content ≠ base (or `pendingDelete` row with diverged server) → mark `CONFLICT` as above.
- no base available (e.g. local CREATE not yet pushed) → keep today's skip.
- **rows already in `CONFLICT` are never upserted by pull** (the row is the recovery copy; the
  existing `PENDING`-only guard must be widened, since conflict rows have no queued ops left).
  If a pulled server copy differs from the stored `conflictServerJson` (canonical parsed-field
  comparison, not raw JSON), only that column is refreshed so resolution always acts on the
  latest "theirs". No absence-detection is attempted
  while unresolved — if the entry was meanwhile deleted server-side, the eventual *Keep mine*
  push hits 404 and re-conflicts with the `"DELETED"` marker, which is self-correcting.

### Storage

- `SyncState` enum gains `CONFLICT` (stored as TEXT via existing converter — additive, no
  migration needed for the enum itself).
- `time_entries` gains nullable `conflictServerJson` (server copy at detection time, including
  its tag ids; the literal string marker `"DELETED"` means deleted-on-server). Local edit stays
  in the row itself — it *is* the recovery copy.
- `CONFLICT` entries are excluded from outbox pushes (their ops were deleted) and from the
  supersession logic.

### Conflicted entries are edit-locked

- Repository mutations (`updateEntry`, delete) refuse entries with `syncState = CONFLICT`; the
  edit dialog disables saving with a "resolve the sync conflict in Review first" hint, and
  conflicted rows carry a small badge in entry lists.
- **Exception — stopping:** a conflicted *running* entry can still be stopped (time capture must
  never be blocked). The stop writes `end` into the local row only, enqueues nothing; the row is
  the recovery copy, so *Keep mine* pushes the stop with everything else, and *Keep theirs*
  restores the server copy (which may still be running — consistent with choosing "theirs").

### Review surface

- New `InboxIssueType.CONFLICT`, generated from DB state (entries with `syncState = CONFLICT`),
  not from analyzer heuristics; conflict cards are pinned above other issue types and are **not**
  swipe-dismissible — the user must choose.
- Card shows both versions (field-level "mine vs theirs" summary; project/task/tag ids resolved
  to names via the local catalog, falling back to a shortened id when no longer present). A
  `pendingDelete` row renders "mine" as *deleted*.
- Actions, by what "mine" is. **Both actions clear `conflictServerJson` and exit the `CONFLICT`
  state** (Keep mine → `PENDING`, Keep theirs → `SYNCED`):
  - **Keep mine**, mine = edit → re-enqueue UPDATE with base = the stored server copy; `PENDING`.
  - **Keep mine**, mine = deletion → re-enqueue DELETE with base = the stored server copy.
  - **Keep mine**, theirs = `"DELETED"` marker → enqueue CREATE (recreate on the server). The op
    carries the row's old *server* id, not a `local-` id — the worker's reconcile path rekeys
    whatever id the op carries, but this is covered by an explicit end-to-end test since other
    invariants (dead-letter cascade guard) assume CREATEs carry `local-` ids.
  - **Keep theirs** → overwrite the entity with the server copy (clearing `pendingDelete` if
    set), `SYNCED`. If theirs is the deleted marker → delete the local row. If theirs is still
    running, the restored running entry is accepted as-is even if another timer is now running
    locally — existing running-entry invariants apply, no special handling.
- Field-level merge is explicitly out of scope for this pass (revisit if conflicts prove common).

### Tests

Unit/Robolectric: push conflict (server diverged), push pass (server == base), pull conflict,
pull skip (server == base), 404/absent-window-delete conflict, STOP-op conflict (offline stop vs
web edit), local-delete vs server-edit conflict, keep-mine and keep-theirs resolution (edit,
deletion, and deleted-marker variants), base reuse across STOP→UPDATE chains, timestamp-format
variance does NOT conflict (offset vs Z, fractional seconds), edit-lock enforcement + stop
exception on conflicted rows, **pull leaves CONFLICT rows untouched except a `conflictServerJson`
refresh**, recreate-after-server-delete syncs and rekeys end-to-end, migration v4→v5, conflict
card presence and non-dismissibility.
Divergence axes per the finding: description, project, billable, tags.

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
- Entry editing/display in `TrackingScreen` / `TrackingViewModel` and `EditTimeEntryDialog`
  (the actual date/time-editing surface, listed explicitly so it isn't missed) — dates shown and
  picked in the account zone so the phone agrees with the web app.

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
  **claimed when the auth cache is written** (login / auth refresh): one
  `UPDATE ... WHERE ownerUserId IS NULL` stamping the current account. Claiming at auth time
  rather than first-list-view narrows the misclaim window; the residual case (upgrade installed,
  account A never triggers an auth refresh before logging out, B logs in and claims A's legacy
  rows) is accepted and documented — it can only affect rows from before this feature existed.
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
  `horizonChosen && horizonStartMs == null` means *Everything* (still bounded by the 370-day
  gap cap). `horizonChosen` defaults false, so existing users see the picker once on their next
  visit — intended: it is exactly the "how far back should Review look?" onboarding they never got.
- First Inbox open with `horizonChosen == false`: instead of the issue list, show a picker —
  *Today / This week / Last 30 days / Everything* — which sets the horizon and unlocks the list.
- `InboxAnalyzer` clamps **all** issue types (gaps, overlaps, missing metadata, long duration) to
  windows intersecting `[horizonStart, now]`; badge count follows automatically (same inputs).
  The 370-day gap cap remains as an outer bound. While `horizonChosen == false` the analyzer
  clamps to *today* — the badge never shows the historical backlog before the user has picked a
  horizon (that badge is the adoption problem this finding exists to fix).
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
