# Deferred Review Findings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the six deferred engineering-review findings per the approved spec
`docs/superpowers/specs/2026-07-11-deferred-findings-design.md` (read it first — it is the
authority on behavior; this plan is the authority on sequencing and mechanics).

**Architecture:** Offline-first Android app (Kotlin, Compose, Hilt, Room v4, WorkManager
outbox sync against the Solidtime REST API). Changes: a content-based sync-conflict state
machine (SV-027), an injected account-scoped TemporalPolicy (SV-012), per-account template
ownership (SV-011), Review-inbox horizon/grouping UX (SV-005), app-bar clarity (SV-006), and
the AGP 9 upgrade (SV-004) — in that order, on `fix/review-findings`. **Commit granularity:
one commit per task** (as the task steps specify — no squashing); findings land in order, each
finding's last task ends with the full verification gate so every finding boundary is green.

**Tech Stack:** Kotlin, Jetpack Compose, Room (exportSchema, migration tests via Robolectric),
Hilt, kotlinx.serialization, WorkManager, JUnit4 + Robolectric, nix develop toolchain.

**Verification gate (run after every finding, before its commit):**
```bash
nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon spotlessCheck testDebugUnitTest lintDebug assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

**Single-test loop (fast TDD iterations):**
```bash
nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon :app:testDebugUnitTest --tests "<FQCN>"
```

**Emulator E2E (after SV-027, SV-005, SV-004 — ONLY on 192.168.50.20):**
```bash
git push kaisel fix/review-findings
ssh tricked@192.168.50.20 'cd ~/dev/SolidVerdant && git fetch . && git checkout fix/review-findings && git pull --ff-only . fix/review-findings 2>/dev/null; git status --short'
# then on .20 (both emulators already running):
ssh tricked@192.168.50.20 'cd ~/dev/SolidVerdant && nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon connectedDebugAndroidTest'
```
(If the checkout/pull incantation on .20 misbehaves, `git fetch origin` won't have the branch —
the push goes to the `kaisel` remote which IS that checkout's own repo; `git checkout fix/review-findings`
directly after the push is enough.)

---

## Chunk 1: SV-027 — Sync conflict detection & resolution

Read before starting: spec §SV-027; `app/src/main/java/dev/tricked/solidverdant/sync/SyncWorker.kt`,
`sync/OutboxPayloads.kt`, `data/repository/TimeEntryRepository.kt` (enqueue paths ~:262-390),
`data/local/db/{OutboxEntity,OutboxDao,TimeEntryDao,Entities,AppDatabase}.kt`,
`domain/inbox/InboxAnalyzer.kt`, `ui/review/{InboxPane,InboxViewModel}.kt`,
`app/src/test/java/dev/tricked/solidverdant/sync/SyncWorkerTest.kt` (fixture patterns),
`data/local/db/MigrationTest.kt`.

### Task 1.1: ConflictSnapshot canonical content model

**Files:**
- Create: `app/src/main/java/dev/tricked/solidverdant/sync/ConflictSnapshot.kt`
- Test: `app/src/test/java/dev/tricked/solidverdant/sync/ConflictSnapshotTest.kt`

- [ ] **Step 1: Write failing tests** — `ConflictSnapshotTest`:
  - `identical content matches` — two snapshots from same fields → `matches()` true.
  - `timestamp format variance does not differ` — start `"2026-07-11T09:00:00Z"` vs
    `"2026-07-11T11:00:00+02:00"` vs `"2026-07-11T09:00:00.000Z"` → all match.
  - `null description equals blank description`.
  - `tag order is irrelevant` — `["b","a"]` vs `["a","b"]` match.
  - `each conflict-relevant field diverges` — description/projectId/taskId/billable/tagIds/start/end
    each individually produce a mismatch.
  - `round-trips through json` — encode → decode → matches.
  - `unparseable timestamp falls back to raw-string comparison` (lenient: never throw).
- [ ] **Step 2: Run tests, verify FAIL** (class not found).
- [ ] **Step 3: Implement.** `@Serializable data class ConflictSnapshot(startMs: Long?, endMs: Long?, startRaw: String? = null, endRaw: String? = null, description: String, projectId: String?, taskId: String?, billable: Boolean, tagIds: List<String>)`.
  Factory `fun of(start: String?, end: String?, description: String?, projectId: String?, taskId: String?, billable: Boolean, tagIds: List<String>)`:
  parse instants via `OffsetDateTime.parse`→`Instant` with `Instant.parse` fallback, on failure
  keep `startRaw`/`endRaw` and null millis; normalize description `?: ""`; store `tagIds.sorted()`.
  `matches(other)`: compare millis when both parsed, else raw strings; other fields directly.
  Also `fun toTimeEntryFields()` is NOT needed — server copies are stored as the full serialized
  server `TimeEntry` (see Task 1.4), snapshots are only ever compared.
- [ ] **Step 4: Run tests, verify PASS.**
- [ ] **Step 5: Commit** `feat(sync): add canonical conflict snapshot model`.

### Task 1.2: Schema v5 — outbox base + entity conflict columns + CONFLICT state

**Files:**
- Modify: `app/src/main/java/dev/tricked/solidverdant/data/local/db/Entities.kt` (SyncState enum + TimeEntryEntity), `OutboxEntity.kt`, `AppDatabase.kt` (version 5, `MIGRATION_4_5`), the Hilt module providing the DB (add migration to builder — find via `databaseBuilder` grep).
- Test: `app/src/test/java/dev/tricked/solidverdant/data/local/db/MigrationTest.kt`

- [ ] **Step 1: Failing migration test** `migration_4_5_adds_conflict_columns`: create v4 db with
  one outbox row + one time_entries row (copy the fixture style of `migration_3_4…`), run
  MIGRATION_4_5, assert `baseSnapshotJson` readable as NULL on outbox and `conflictServerJson`
  NULL on time_entries, and that a row can be written with `syncState='CONFLICT'`.
- [ ] **Step 2: Run, verify FAIL.**
- [ ] **Step 3: Implement:** add `CONFLICT` to `SyncState`; `baseSnapshotJson: String? = null` on
  OutboxEntity; `conflictServerJson: String? = null` on TimeEntryEntity; bump `version = 5`;
  `MIGRATION_4_5` = two `ALTER TABLE … ADD COLUMN … TEXT` statements; register the migration
  in `AppDatabase.MIGRATIONS` (registered via `di/DatabaseModule.kt`). `Converters` already
  persists SyncState as TEXT by name — nothing else changes. Verify schema export:
  `ls app/schemas/*/5.json` after the gate's `assembleDebug` (KSP runs the export). Expected: file exists.
- [ ] **Step 4: Run migration tests, verify PASS.**
- [ ] **Step 5: Commit** `feat(db): room v5 — conflict columns and CONFLICT sync state` (include 5.json).

### Task 1.3: Base capture at enqueue (STOP / UPDATE / DELETE)

**Files:**
- Modify: `data/repository/TimeEntryRepository.kt` (`updateEntry`, `stopEntry`, `commitDelete` enqueue transactions), `data/local/db/OutboxDao.kt` (query: oldest non-null base for entry; query: has queued START/CREATE for entry).
- Test: extend `app/src/test/java/dev/tricked/solidverdant/data/repository/TimeEntryRepositoryWriteTest.kt`

- [ ] **Step 1: Failing tests:**
  - `update on synced entry captures pre-mutation base` — seed SYNCED entity, `updateEntry` with
    new description; queued op's `baseSnapshotJson` decodes to the OLD content.
  - `stop captures pre-stop base` — running SYNCED entity, `stopEntry`; base has `endMs == null`.
  - `delete captures base despite pending flag` — the soft-delete flow; base = untouched content.
  - `chained ops reuse oldest base` — stop then update offline; UPDATE op's base == STOP op's base.
  - `ops on locally-created entry have null base` — queued CREATE, then update → base null.
- [ ] **Step 2: Run, verify FAIL.**
- [ ] **Step 3: Implement** per spec base-capture rules 1-3, inside the existing enqueue
  transactions, BEFORE the local content write. Tag ids for the snapshot come from the entry's
  current cross-refs (same query the payload builder uses).
- [ ] **Step 4: Run, verify PASS.**
- [ ] **Step 5: Commit** `feat(sync): capture base snapshots when enqueueing stop/update/delete`.

### Task 1.4: Pull side — conflict marking + CONFLICT-row guard

**Files:**
- Modify: `data/local/db/TimeEntryDao.kt` (`applyServerEntries` + helpers), and its caller in `TimeEntryRepository`/`RemoteDataSource` mapping so the DAO gets what it needs (queued-op bases via OutboxDao — inject the lookup as a parameter map to keep the DAO pure).
- Test: `app/src/test/java/dev/tricked/solidverdant/data/local/db/TimeEntryDaoTest.kt` (+ repository-level test if wiring lives there)

- [ ] **Step 1: Failing tests:**
  - `pull skips pending row when server matches base`.
  - `pull marks conflict when server diverges from base` — row becomes CONFLICT,
    `conflictServerJson` = serialized server entry (use the app's `TimeEntry` model JSON, tags
    included), entry's outbox ops deleted.
  - `pull marks conflict for pending delete with diverged server`.
  - `pull keeps skipping rows with no base` (local CREATE case — today's behavior).
  - `pull never upserts CONFLICT rows` — conflicted row content unchanged after a pull carrying
    a different server copy…
  - `…but refreshes conflictServerJson` — the stored "theirs" now equals the newest server copy.
- [ ] **Step 2: Run, verify FAIL.**
- [ ] **Step 3: Implement** per spec §Pull side. The guard at the top of the per-entity loop:
  `local.syncState == CONFLICT` → optionally update `conflictServerJson` column only, `return@forEach`.
- [ ] **Step 4: Run, verify PASS.**
- [ ] **Step 5: Commit** `feat(sync): pull-side conflict detection, recovery copy preserved`.

### Task 1.5: Push side — windowed compare in SyncWorker

**Files:**
- Modify: `sync/SyncWorker.kt` (STOP/UPDATE/DELETE paths), `data/remote/RemoteDataSource.kt` if the windowed list call needs a parameter it lacks.
- Test: `app/src/test/java/dev/tricked/solidverdant/sync/SyncWorkerConflictTest.kt` (new; reuse `SyncWorkerTest` fixtures + `FakeRemoteDataSource`)

- [ ] **Step 1: Failing tests:**
  - `update pushes when server matches base` — PUT happened, entry SYNCED.
  - `update conflicts when server diverged` — no PUT, entry CONFLICT with server copy stored,
    ALL queued ops for the entry deleted.
  - `stop op conflicts when server edited meanwhile`.
  - `delete op conflicts when server edited meanwhile` (pendingDelete row → CONFLICT).
  - `absent from window becomes deleted-marker conflict`.
  - `put 404 becomes deleted-marker conflict` (only for based ops; unbased keeps dead-letter).
  - `fetch failure retries op` — IOException on the window fetch → op remains queued.
  - `null-base op pushes blind` (today's behavior preserved).
  - `conflicted entries are exempt from supersession logic` — pins the spec invariant (falls out
    of ops-deletion + edit-lock, but assert it: a CONFLICT entry is never treated as superseding
    or superseded by other entries' ops).
- [ ] **Step 2: Run, verify FAIL.**
- [ ] **Step 3: Implement** per spec §Push side: one paginated window fetch per org per run
  (reuse the existing 250/page loop), lazily fetched only if any queued op has a base; index by
  id; compare via `ConflictSnapshot.of(server…)` vs decoded base. Deleted marker constant
  `"DELETED"` lives next to `ConflictSnapshot`.
- [ ] **Step 4: Run, verify PASS** (run the whole `sync` package — the existing SyncWorkerTest must stay green).
- [ ] **Step 5: Commit** `feat(sync): push-side conflict detection with windowed server compare`.

### Task 1.6: Edit-lock conflicted entries (repository level) + stop exception

**Files:**
- Modify: `data/repository/TimeEntryRepository.kt` (`updateEntry`, delete path, `stopEntry`).
- Test: extend `TimeEntryRepositoryWriteTest.kt`

- [ ] **Step 1: Failing tests:** `update refuses conflicted entry` (throws or returns failure —
  match the repo's existing error idiom; assert no op enqueued, content unchanged);
  `delete refuses conflicted entry`; `stop on conflicted running entry writes end locally without op`.
- [ ] **Step 2: Run, verify FAIL.** 
- [ ] **Step 3: Implement** per spec §edit-lock.
- [ ] **Step 4: Run, verify PASS.**
- [ ] **Step 5: Commit** `feat(sync): edit-lock conflicted entries, allow local stop`.

### Task 1.7: Resolution — keep mine / keep theirs

**Files:**
- Modify: `data/repository/TimeEntryRepository.kt` (new `suspend fun resolveConflict(entryId: String, resolution: ConflictResolution)`, enum `KEEP_MINE`/`KEEP_THEIRS`).
- Test: `app/src/test/java/dev/tricked/solidverdant/data/repository/ConflictResolutionTest.kt`

- [ ] **Step 1: Failing tests** (all four action rows from the spec table, plus):
  - `keep mine re-enqueues update with server copy as base`; entry PENDING, conflict json cleared.
  - `keep mine on pending delete re-enqueues delete`.
  - `keep mine after server delete enqueues create and syncs end-to-end` — run SyncWorker after
    resolution against FakeRemoteDataSource; assert the entry is recreated and REKEYED to the new
    server id (this is the invariant the spec calls out: CREATE ops normally carry `local-` ids).
  - `keep theirs restores server copy` incl. tags, SYNCED, pendingDelete cleared.
  - `keep theirs with deleted marker removes row`.
  - `keep theirs restoring running copy is accepted as-is`.
- [ ] **Step 2: Run, verify FAIL.**
- [ ] **Step 3: Implement** per spec resolution table.
- [ ] **Step 4: Run, verify PASS.**
- [ ] **Step 5: Commit** `feat(sync): conflict resolution — keep mine / keep theirs`.

### Task 1.8: Review inbox conflict card

**Files:**
- Modify: `domain/inbox/InboxAnalyzer.kt` (`InboxIssueType.CONFLICT`; conflict issues built from
  DB state passed in — keep `analyze()` pure: caller supplies conflicted entries),
  `data/repository/InboxRepository.kt` (source conflicted entries, count them),
  `ui/review/InboxViewModel.kt` (resolution actions), `ui/review/InboxPane.kt` (card UI, pinned
  first, NOT wrapped in `DismissibleIssue`), `ui/components/EditTimeEntryDialog.kt` (disabled
  save + hint for CONFLICT), entry-list badge in `ui/tracking/TrackingScreen.kt` history items.
- Modify: `app/src/main/res/values/strings.xml`, `values-nl/strings.xml`, `values-ja/strings.xml`
  (card title/body, "Keep mine", "Keep theirs", "Deleted on server", badge desc, edit-lock hint).
- Test: `app/src/test/java/dev/tricked/solidverdant/domain/inbox/InboxAnalyzerConflictTest.kt`; compose-semantics Robolectric test for "conflict card visible and not dismissible" — there are NO existing inbox compose tests; copy the harness pattern from `app/src/test/java/dev/tricked/solidverdant/ui/tracking/BillableAccessibilityTest.kt`.

- [ ] **Step 1: Failing analyzer tests:** conflict issues emitted for CONFLICT entries, pinned
  before other types, included in count, NOT suppressed by dismissals.
- [ ] **Step 2: Run, verify FAIL.** 
- [ ] **Step 3: Implement analyzer + repository plumbing.**
- [ ] **Step 4: UI:** card shows field-diff summary (mine vs theirs; resolve project/task/tag ids
  via the local catalog, shortened-id fallback; `pendingDelete` renders mine as *Deleted*;
  `"DELETED"` marker renders theirs as *Deleted on server*), two buttons wired to
  `viewModel.resolve(entryId, …)`. Strings in all three locales.
- [ ] **Step 5: Run full test suite + gate, verify PASS.**
- [ ] **Step 6: Commit** `feat(review): conflict cards with keep-mine/keep-theirs resolution`.

### Task 1.9: SV-027 wrap-up

- [ ] Run the verification gate. Expected: BUILD SUCCESSFUL.
- [ ] Push branch to kaisel; run `connectedDebugAndroidTest` on .20 (command block at top). Expected: all connected tests pass (pre-existing failures, if any, must match master's baseline — check first if anything fails).
- [ ] Mark DEFERRED_FINDINGS.md SV-027 section with an "Implemented (date, commit)" note.
- [ ] Commit `feat(sync): complete SV-027 conflict handling` (only if wrap-up edits exist; otherwise the previous commits stand).

---

## Chunk 2: SV-012 — Account-scoped temporal policy

Read: spec §SV-012 (full call-site inventory), `ui/statistics/{StatRange,StatisticsAggregator,StatisticsViewModel}.kt`, `ui/calendar/CalendarViewModel.kt`, `data/local/SettingsDataStore.kt:124-146`.

### Task 2.1: TemporalPolicy + provider + DI

**Files:**
- Create: `app/src/main/java/dev/tricked/solidverdant/domain/time/TemporalPolicy.kt`,
  `domain/time/TemporalPolicyProvider.kt`, `di/TemporalModule.kt`
- Modify: `app/src/main/java/dev/tricked/solidverdant/data/local/SettingsDataStore.kt` —
  **required**: cached auth lives in the synchronous `immediate_ui_cache` SharedPreferences
  (`:50`, `cacheAuth` at `:135`), which has NO change-notification path today. Add an observable
  source: `private val authCacheChanges = MutableStateFlow(0)` bumped inside `cacheAuth()` and
  `clearCachedData()`, exposed as `fun observeCachedAuth(): Flow<CachedAuth?>` (emit current value,
  re-read on each bump). Do NOT test only against a fake — that would mask this gap.
- Test: `app/src/test/java/dev/tricked/solidverdant/domain/time/TemporalPolicyProviderTest.kt`

- [ ] **Step 1: Failing tests:** timezone string → ZoneId (valid, garbage → device fallback,
  absent auth → device); week_start mapping (`"monday"…"sunday"` → DayOfWeek, unknown → MONDAY);
  provider emits updated policy when cached auth changes — Robolectric test against the REAL
  `SettingsDataStore.observeCachedAuth()` (write a new cached user with a different timezone,
  assert a new policy emission).
- [ ] **Step 2: FAIL → Step 3: Implement:**
  `data class TemporalPolicy(val zone: ZoneId, val firstDayOfWeek: DayOfWeek)`;
  `@Singleton class TemporalPolicyProvider @Inject constructor(settings: SettingsDataStore)` with
  `val policy: Flow<TemporalPolicy>` + `suspend fun current(): TemporalPolicy`; Hilt module.
- [ ] **Step 4: PASS → Step 5: Commit** `feat(time): account-scoped TemporalPolicy provider`.

### Task 2.2: Statistics on policy

**Files:** Modify `StatRange.kt` (resolve gains `firstDayOfWeek: DayOfWeek` param), `StatisticsAggregator.kt:244` (param instead of `WeekFields.ISO`), `StatisticsViewModel.kt:107` (inject provider). Tests: extend `StatisticsAggregatorTest.kt` + StatRange tests.

- [ ] Failing tests: ThisWeek/LastWeek resolve with Sunday start; week-trend buckets with Sunday
  start; clipping across a DST transition in `Europe/Amsterdam`; profile-zone ≠ UTC day
  boundaries (entry at 23:30Z lands on next day in `Asia/Tokyo`).
- [ ] Implement; update existing tests to pass explicit Monday/UTC (assert unchanged results —
  regression guard).
- [ ] Gate-relevant tests PASS → Commit `feat(stats): statistics follow account temporal policy`.

### Task 2.3: Calendar, review, tracking surfaces on policy

**Files:** every remaining account-policy call site in the spec inventory: `CalendarViewModel`
(zone, firstDayOfWeek, today), `MonthCalendarView`/`WeekCalendarView`/`WeekCalendarLayout`
(zone param from VM state, delete `remember { ZoneId.systemDefault() }`), `CalendarDateUtils.entryLocalDate`
(zone param), `ReviewDayViewModel`, `InboxViewModel`, `InboxPane`, `ReviewDayPane`,
`InboxRepository:66`, `EntryTrustRules:78`, `TrackingScreen` (:1049,:1054,:2575,:1206,:3331),
`TrackingViewModel:1292`, `EditTimeEntryDialog`. **Leave `ReminderScheduler`/`ReminderWorker`
untouched** (device-local by decision).

- [ ] Failing test: `CalendarDateUtilsTest` month grid with Sunday `firstDayOfWeek`; entryLocalDate
  with explicit non-device zone.
- [ ] Mechanical replacement, compile-driven: policy flows from each ViewModel into composables as
  state; no composable calls `ZoneId.systemDefault()` afterwards. Verify with:
  `grep -rn "systemDefault\|WeekFields" app/src/main/java | grep -v -i reminder` → only
  TemporalPolicyProvider's fallback remains.
- [ ] Full gate PASS → Commit `feat(time): reporting surfaces follow account timezone and week start`.

---

## Chunk 3: SV-011 — Per-account template retention

Read: spec §SV-011, `data/local/db/{TemplateDao,Entities}.kt`, `data/repository/TemplateRepository.kt`, `data/local/UserCacheCleaner.kt`, `data/repository/AuthRepository.kt` (where auth cache is written), `ui/templates/ManageTemplatesViewModel.kt`.

### Task 3.1: Schema v6 — owner columns

- [ ] Failing migration test `migration_5_6_adds_template_ownership` (columns nullable, legacy row
  keeps NULL owner, index exists).
- [ ] Implement: `ownerEndpoint`/`ownerUserId` on TemplateEntity, version 6, MIGRATION_5_6, index
  (`ownerUserId`,`organizationId`), commit schemas/6.json.
- [ ] PASS → Commit `feat(db): room v6 — template ownership columns`.

### Task 3.2: Owner-scoped queries + claim-at-auth + stamped inserts

**Files:** `TemplateDao.kt` (owner+org filter; `claimUnowned(endpoint, userId)`), `TemplateRepository.kt` (owner from AuthDataStore endpoint + cached user id), auth-cache write path (`AuthRepository`/`SettingsDataStore.cacheAuth`) triggers claim.
- [ ] Failing tests: observe filters by owner (A's rows invisible to B — different userId; and
  different endpoint with same org id); inserts stamp owner; claim stamps NULL rows exactly once
  (second claim by another account is a no-op on already-owned rows).
- [ ] Implement → PASS → Commit `feat(templates): per-account ownership and claim-at-auth`.

### Task 3.3: Selective clear on logout

**Files:** `UserCacheCleaner.kt` — replace `database.clearAllTables()` with a dynamic sweep:
query `sqlite_master` for tables, delete from all except keep-set `{entry_templates}` (and Room's
own `room_master_table`/`android_metadata`/sqlite internals), inside a transaction.
- [ ] Failing test (`UserCacheCleanerTest`, Robolectric + in-memory db): after `clear()`,
  time_entries/outbox/etc. empty, entry_templates rows intact; a table added in the future is
  cleared by default (create a temp table in the test db and assert it's swept); full round-trip:
  insert template as account A → `clear()` (logout) → observe as account A again → template
  visible; observe as account B → not visible.
- [ ] Implement → PASS. Also assert logout flow (AuthViewModel) unchanged otherwise.
- [ ] Full gate → Commit `fix(auth): logout preserves per-account templates (SV-011)`.

---

## Chunk 4: SV-005 — Review inbox horizon, grouping, bulk dismiss

Read: spec §SV-005, `domain/inbox/{InboxAnalyzer,InboxSettingsDataStore}.kt`, `ui/review/{InboxPane,InboxViewModel}.kt`, `data/repository/InboxRepository.kt`.

### Task 4.1: Horizon settings + analyzer clamp

- [ ] Failing tests (`InboxAnalyzerTest` + settings test): `horizonChosen=false` clamps all issue
  types to today; chosen horizonStart clamps gaps/overlaps/metadata/long-duration to
  `[horizonStart, now]`; `horizonChosen && horizonStartMs==null` = everything (370-day cap);
  badge `count()` obeys the same clamp.
- [ ] Implement `horizonStartMs`/`horizonChosen` in InboxSettingsDataStore + analyzer clamp.
- [ ] PASS → Commit `feat(review): inbox horizon clamps analysis and badge`.

### Task 4.2: First-run picker + horizon chip + settings entry

**Files:** `InboxPane.kt` (picker replaces list when `!horizonChosen`: Today / This week /
Last 30 days / Everything), horizon chip near the count ("Since <date>" / "Everything") opening
the existing `InboxSettingsSheet` (add horizon row there), `InboxViewModel.kt`, strings ×3 locales.
- [ ] ViewModel test: first-run state exposes picker; choosing sets store and unlocks list.
- [ ] Implement → PASS → Commit `feat(review): first-run horizon picker and horizon chip`.

### Task 4.3: Day grouping + bulk dismiss

**Files:** `InboxPane.kt` (group issues by day in the account zone — TemporalPolicy from Chunk 2;
sticky day headers, newest first; per-day "Dismiss all"), `InboxViewModel.kt` (batch dismiss),
"dismiss everything before this date" action (overflow menu) = set `horizonStartMs` forward.
- [ ] Tests: per-day batch dismissal persists dismissal rows for exactly that day's issues;
  horizon-move hides older issues and — unlike dismissals — survives the 45-day pruning
  (fixture: dismissedAt older than retention).
- [ ] Implement. No bulk entry creation anywhere.
- [ ] Full gate → E2E on .20 (visual check of picker/grouping on an emulator: install
  `assembleDebug` APK, open Review) → Commit `feat(review): day grouping and safe bulk dismiss`.

---

## Chunk 5: SV-006 — Track app bar clarity

### Task 5.1: App bar rendering + semantics

**Files:** `ui/tracking/TrackingScreen.kt:684-737`; strings ×3 locales ("Switch organization").
- [ ] Failing compose test (`TrackingAppBarTest`, Robolectric, style of `BillableAccessibilityTest`):
  identical names render once; >1 membership shows dropdown affordance with `Role.Button` and
  content description "Switch organization"; single membership renders plain text with NO click
  semantics and `onSurfaceVariant` color (not `primary`).
- [ ] Implement per spec: trimmed case-sensitive equality collapse; `ArrowDropDown` trailing icon
  when `memberships.size > 1 && !isTracking && !isPaused`; `Modifier.semantics`/`clickable(role=Role.Button)`.
- [ ] PASS → full gate → Commit `fix(track): distinguish user/org lines, real switch affordance (SV-006)`.

---

## Chunk 6: SV-004 — AGP 9.2.1 upgrade

### Task 6.1: Upgrade

**Files:** `gradle/libs.versions.toml` (`androidGradlePlugin = "9.2.1"` + whatever the matrix
requires), `gradle/wrapper/gradle-wrapper.properties`, root + `app/build.gradle.kts` as needed.
- [ ] FIRST research, don't guess: AGP 9.2.1 release notes → required Gradle version, Kotlin/KSP
  minimums, removed DSL. Check current Gradle wrapper + Kotlin versions in the repo. Bump
  minimally.
- [ ] Build: gate command. Fix breakages iteratively (built-in Kotlin, DSL removals, lint changes).
- [ ] **Timebox: ~2h.** If still red, `git checkout` the SV-004 changes away, restore 8.13.2, and
  record the blockers in DEFERRED_FINDINGS.md instead — the five landed findings must not be
  held hostage.
- [ ] Gate PASS → push to kaisel → `connectedDebugAndroidTest` on .20 PASS.
- [ ] Commit `build: upgrade AGP to 9.2.1 (SV-004)`.

### Task 6.2: Final wrap-up

- [ ] Full gate + connected tests green on the final tree.
- [ ] Update `DEFERRED_FINDINGS.md`: mark each finding Implemented (commit hashes).
- [ ] `git push kaisel fix/review-findings` for a final on-device smoke of Track/Review/Statistics
  on one emulator.
- [ ] Report: summary of all commits, test counts, anything descoped.
