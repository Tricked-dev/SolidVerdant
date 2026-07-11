# Deferred findings — needs deeper design work

These came out of the continuous engineering review (full detail + evidence in `ENGINEERING_REVIEW.md`). They were **intentionally not touched** in the automated fix pass because each needs a schema migration, a new state machine, a cross-cutting policy, or a product/UX decision — the kind of change that's unsafe to bang out mechanically. This doc captures the problem and some starting thoughts so you can pick them up deliberately.

Priority order (my opinion): **SV-027 → SV-012 → SV-011 → SV-005 → SV-006 → SV-004**.

---

## SV-027 — Local↔remote edit conflicts resolved silently by last-write-wins (P0 per roadmap #34)

**Problem.** There is no conflict handling in either direction.
- Pull: `TimeEntryDao.applyServerEntries` (`:90-100`) silently *skips* the server version for any row currently `PENDING` — a concurrent server edit is dropped with no signal.
- Push: `SyncWorker` UPDATE (`:136-145`) PUTs the full local snapshot with no revision / `updated_at` precondition, then `persistSynced` accepts the echo — a concurrent server edit is silently overwritten.

Gap-analysis #34 classifies this P0, #79 asks for a CONFLICT state, and #80 asks to preserve a local recovery copy before overwriting — none implemented.

**Why deferred.** This is a new state machine + storage + UI surface, not a patch. Getting it half-right (e.g. adding a revision check without a recovery path) can make data loss *worse* by failing syncs with no way to recover the local edit.

**Thoughts / approach.**
1. **Capture a base revision at enqueue time.** When an UPDATE op is created, snapshot the server `updated_at` (or ETag/version) the local edit was based on, into the outbox row.
2. **Optimistic concurrency on push.** Before PUT, compare the server's *current* `updated_at` to the base. If unchanged → safe to write. If diverged → do **not** blind-overwrite.
3. **On divergence:** persist a recovery copy of the local edit, mark the entry a new `CONFLICT` sync state, and surface it in the Review inbox ("this entry changed on the server and on this device — keep mine / keep theirs / merge").
4. **On pull:** when a server version differs from a `PENDING` local edit, mark `CONFLICT` instead of silently skipping.
5. Needs: a `CONFLICT` value in the sync-state enum, a recovery-copy table (or a JSON column), a Review conflict card, and tests for both directions (offline edit vs web edit of the same entry, billable/project/description divergence).

**Rough size:** ~1 focused feature. Touches `OutboxEntity`/payloads, `SyncWorker`, `TimeEntryDao`, sync-state enum, a Review surface, + tests.

---

## SV-012 — Day/week boundaries use device settings instead of authoritative account rules (P2, reporting correctness)

**Problem.** Temporal logic is scattered and inconsistent:
- `ZoneId.systemDefault()` is used directly in `InboxRepository:66`, `StatisticsViewModel:101`, `ReviewDayViewModel:55`, `InboxViewModel:80`, `CalendarViewModel:354`, `ReminderWorker:77`, `WeekCalendarLayout:47`, …
- Week start is split: statistics use `WeekFields.ISO` (Monday) (`StatRange:30,37`, `StatisticsAggregator:244`) while Calendar uses the device locale's first-day-of-week (`CalendarViewModel:83`).

A user traveling, on a device tz ≠ their Solidtime profile tz, or with a non-Monday week, sees entries land on different days and different week totals across screens.

**Why deferred.** It's a cross-cutting concern touching ~7+ call sites and the aggregation logic; done piecemeal it just moves the inconsistency around.

**Thoughts / approach.**
1. Introduce a single account-scoped `TemporalPolicy` value: `reportingZone: ZoneId`, `firstDayOfWeek: DayOfWeek`, and the day-boundary rule (midnight in the reporting zone, inclusive/exclusive).
2. Source it from the Solidtime profile/org settings (the server has the authoritative timezone & week config); cache it and expose it as a flow via DI.
3. Replace **every** `ZoneId.systemDefault()` and ad-hoc `WeekFields` usage with the policy. One PR, mechanical once the policy exists.
4. Test matrix: DST transition, device-tz-≠-profile-tz travel, Sunday-start week, and entries crossing midnight in the reporting zone.

---

## SV-011 — Template retention contradicts its contract; templates wiped on every logout (P2)

**Problem.** `TemplateRepository:83-84` documents that `entry_templates` is retained across logout per-account (roadmap #787, "Keep for the same account"), but `AuthViewModel.logout` → `UserCacheCleaner.clear()` → `database.clearAllTables()` deletes every template. So favorites are lost on every logout. **Naively keeping them is also unsafe:** templates are scoped only by `organizationId`, with no server-profile/user ownership, so retaining rows could expose account A's templates to account B on the same device.

**Why deferred.** The safe fix requires a **Room schema migration** (adding ownership identity) — a data-layer change that deserves its own careful PR + migration test, not a mechanical edit.

**Thoughts / approach.**
1. Add ownership columns to `entry_templates`: server profile id + user/account id (whatever uniquely identifies the Solidtime account, not just org).
2. Room migration: add the columns (nullable), backfill existing rows with the currently-logged-in account on upgrade.
3. On logout, replace the blanket `clearAllTables()` for templates with a **selective clear**: keep rows belonging to accounts, but on the *next login* only present rows whose owner matches the returning account (and clear/hide foreign-account rows).
4. Tests: same-account logout→login survives; different-account login does **not** see the first account's templates; migration test from the pre-column schema.

**Note:** the same `clearAllTables()`-on-logout pattern is what SV-022 (cancel sync before clearing) and SV-021 (clear on *forced* logout too) address from the other side — coordinate with those fixes.

---

## SV-005 — Fresh Review inbox dumps a large undifferentiated historical backlog (P2, adoption)

**Problem.** First Review visit showed **88** items going back weeks (e.g. "Untracked time" cards from 23–24 June when the date is 11 July), each repeating the same copy, to be processed one card at a time. There's no grouping, bulk action, age scope, or onboarding — this trains users to ignore the badge before they ever reach the intended "caught up" state.

**Why deferred.** This is a product/UX decision (what's the right default horizon? what bulk actions are safe?), not a bug fix.

**Thoughts / approach.**
- On first enablement, ask how far back to scan (Today / This week / Custom) and keep that horizon visible near the count.
- Group consecutive historical checks by day.
- Add safe bulk actions: "dismiss everything before this date" and per-day review — while **preserving individual confirmation for actually creating entries** (don't bulk-create).
- Consider capping the initial scan horizon by default and letting the user widen it.

---

## SV-006 — Track app bar shows indistinguishable user + org names (P3, clarity)

**Problem.** The Track title renders the user name and organization name on adjacent unlabeled lines ("Tricked" / "Tricked"), so when they match — or are similar — it reads like duplicated text or a rendering defect. The org line's clickability is signaled only by color, and it's disabled when the user has a single membership.

**Thoughts / approach.**
- Collapse identical values, or explicitly label the org line (e.g. "Organization: Tricked").
- Show a dropdown affordance only when switching is actually possible (>1 membership); otherwise render it as plain, clearly non-interactive text.
- Give the selector explicit accessibility semantics.

---

## SV-004 — Android Gradle Plugin outside its tested compile-SDK range (P3, build hygiene)

**Problem.** AGP 8.13.2 reports it was tested through compile SDK 36.1, but the project compiles against SDK 37.0. Build succeeds, but lint/resource/tooling behavior on SDK 37 is outside the plugin vendor's declared compatibility.

**Thoughts / approach.** Low urgency. Upgrade AGP when a version pinned for SDK 37 lands (Renovate should surface it — the repo already wires Renovate). Until then, track the warning explicitly rather than suppressing it silently.

---

*Everything else from the review (SV-001, SV-007–SV-010, SV-013–SV-026, SV-028, SV-029, plus the lint items) is being fixed and regression-tested in the automated pass. SV-003 was retracted (the missing widget string is intentionally `translatable="false"`).*
