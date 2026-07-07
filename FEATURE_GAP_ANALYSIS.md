# SolidVerdant Feature Gap Analysis and Product Roadmap

## Executive summary

SolidVerdant already has a strong identity as a native Android client for Solidtime. Its Quick Settings tile, persistent notification, home-screen widget, recent-project shortcuts, offline cache, OAuth2/PKCE support, and self-hosted server configuration make starting and stopping a timer unusually accessible.

The largest opportunity is not to reproduce every administrative feature found in broad workforce platforms. It is to become the fastest and most trustworthy way to capture, review, and correct Solidtime entries on Android.

The recommended product position is:

> **The fastest, most trustworthy way to capture and repair Solidtime entries on Android.**

The numbered additions below are grouped by product area. Priorities use the following scale:

- **P0:** Completes or protects a core workflow.
- **P1:** High-value differentiation after the core is dependable.
- **P2:** Valuable expansion that can follow the main product loop.
- **Defer:** Large platform features that would dilute the Android-first focus.

## Timer and quick capture

### 1. Favorite and pinned timers — P0

Allow users to pin common combinations of organization, project, task, tags, billable status, and description. Pinned timers should appear above recent entries in the app and optionally in the Quick Settings selection screen.

Recent entries are useful, but they change constantly. Favorites provide a stable home for recurring activities such as “daily stand-up,” “support,” or “client administration.” Starting one should require a single tap, while still allowing its details to be edited after the timer starts.

### 2. Configurable timer defaults — P1

Let users define default behavior globally and per project. Examples include default billable status, tags, task, description, and whether starting a new timer automatically stops the current one.

This reduces repetitive cleanup and makes entries more consistent. Per-project defaults are particularly useful because billable state and required tags often differ between internal and client work.

### 3. Forgotten-timer protection — P0

Warn users when a timer has run unusually long, crosses a configurable time of day, or continues after a calendar event has ended. The warning should offer Stop, Keep Running, and Adjust End Time actions.

The app should learn only from local history or simple user settings at first. A user-configurable maximum duration is easier to understand and more trustworthy than a hidden heuristic.

### 4. Tracking reminders — P0

Add optional reminders when no timer is active during chosen working hours. Reminders could also appear at the end of the day when tracked time is below a personal target or when uncategorized entries remain.

Users should be able to configure days, working hours, reminder frequency, and quiet periods. Notifications must stay actionable and allow starting a favorite without opening the full app.

### 5. Focus and Pomodoro mode — P2

Offer an optional focus session attached to a time entry, with configurable work and break intervals. This should remain lightweight rather than becoming a separate task manager.

The differentiating detail would be integration with Android surfaces: focus progress in the notification, pause/resume controls, and automatic continuation of the same Solidtime entry after a break.

### 6. Voice, NFC, and automation starts — P2

Expose safe Android intents or app shortcuts for starting a configured timer. This enables voice assistants, NFC tags, QR routines, Tasker, MacroDroid, and other automation tools without requiring a custom integration for each one.

The app should document the supported actions and require explicit project identifiers or saved favorite IDs. Destructive or ambiguous actions should still open a confirmation surface.

### 7. Wear OS companion — P2

Provide a small Wear OS experience for viewing the current timer, starting a favorite, pausing, resuming, and stopping. Complications could show the current timer or today’s total.

Wear support fits the product’s existing “capture from anywhere” advantage much better than broad web-style administration features.

## Smart assistance

### 8. Contextual quick-start suggestions — Experimental/P2

Begin with deterministic, user-visible ranking only: pinned timers first, followed by timers previously used on the same weekday or within the same time-of-day bucket. Calendar suggestions should require a mapping explicitly created by the user, such as “Events from this calendar use this project.” Do not initially use location, phone sensors, application usage, or a trained model.

Every suggestion must be explainable—such as “Used at this time last Monday”—and computed on-device. A suggestion may prefill a form, but it must never create, edit, submit, or mark an entry billable without confirmation. Users must be able to disable each suggestion source.

### 9. Description and metadata templates — P1

Expand favorites into reusable templates with placeholder values. A template could prefill a project, tags, and a description such as “Review: {topic}” while asking for the missing topic before starting.

This serves users whose work is repetitive but not identical, and improves report quality without forcing them to type the same structure every time.

### 10. Private activity recall — Research/Defer

If this is ever pursued, study ActivityWatch’s watcher/event architecture and Toggl Timeline’s user-controlled conversion flow instead of designing a new monitoring system. Offer an opt-in local timeline only to help users reconstruct missing time. The activity history must stay on the device and must never become a surveillance feature.

The purpose is recall, not classification, productivity scoring, or automatic billing. Android application-usage access is highly sensitive and requires special user-granted access, so this feature needs a separate privacy review and threat model before implementation. Users must choose which fragments become time entries, and excluded apps must never be recorded.

## History and correction

### 11. Search and advanced history filters — P0

Add search across descriptions, projects, tasks, tags, and customers. Filters should cover date range, billable status, organization, project, task, tag, sync state, running entries, and entries without a project or description.

Filters should be easy to clear and should show the active conditions prominently. A few saved filter presets—such as “Needs categorization”—would turn history into a useful review tool.

### 12. Multi-select and bulk editing — P1

Allow users to select multiple entries and change their project, task, tags, billable status, or description together. Bulk delete should require confirmation and provide a short undo window where technically possible.

Bulk correction is essential when someone tracks a whole day against the wrong project or needs to apply a billing tag at the end of the week.

### 13. Duplicate and split entries — P1

Add actions to duplicate an entry and split it at a chosen time. Duplicating is useful for repeated manual work, while splitting lets users correct a timer that covered two activities without recreating both periods manually.

When splitting, the app should preserve metadata on both halves and then allow the second half to be edited immediately.

### 14. Undo for destructive actions — P0

Provide undo for deletion and other reversible history operations. Where server synchronization makes true undo impossible, the app can recreate the entry using its cached data.

This small safety net materially increases trust, especially on mobile where accidental taps are common.

### 15. Per-entry sync state — P0

Show whether an entry is synced, pending, retrying, or failed. Users should be able to tap the status for a plain-language explanation and retry a failed operation.

The current global sync warning is useful, but it does not answer the most important question: “Did this particular entry reach the server?”

## Daily review and time accuracy

### 16. Deterministic Time Inbox — P1, primary differentiator

Create a focused review screen that collects entries needing attention using explicit, inspectable rules:

- Gaps during configured working hours.
- Overlapping entries.
- Entries exceeding a user-configured maximum duration.
- Entries with no project, task, description, or required tag.
- Entries still pending synchronization.
- Calendar events that may represent untracked work.

Each item should have a quick repair action. The inbox should feel finite and reassuring: once every issue is addressed, the user reaches a clear “All caught up” state.

This is the strongest opportunity for SolidVerdant to stand out. Most timer apps make capturing data easy; fewer make daily cleanup genuinely pleasant on a phone. The inbox should call items “checks” or “items to review,” not anomalies, mistakes, fraud, or unproductive time.

### 17. Deterministic overlap and gap detection — P0

Detect overlaps with exact interval intersection and identify gaps only inside working hours explicitly configured by the user. A configurable minimum gap length prevents the inbox from filling with harmless transitions. No statistical anomaly detector is necessary.

Solidtime’s API exposes the organization setting `prevent_overlapping_time_entries`. Fetch and honor that policy: when enabled, prevention is a server-owned organization rule; when disabled, SolidVerdant may still offer an optional local review check but must not imply the overlap violates organization policy. Working hours remain separate local state because they are not present in the supplied API specification.

Warnings should not block saving. Some users legitimately track overlapping activities, so the app should allow dismissing an issue or marking it intentional.

### 18. End-of-day review — P1

Offer an optional end-of-day notification that opens a compact review: total tracked time, missing periods, uncategorized entries, billable percentage, and failed sync operations.

The review should prioritize corrections over analytics. Its goal is to help the user finish the day confident that tomorrow’s reports will be accurate. It must report only facts and user-configured rule violations, not inferred productivity or intent.

### 19. Personal goals and streaks — P2

Allow users to set daily or weekly tracking targets and optional billable-hour goals. Progress can appear in the app, widget, and end-of-day review.

Streaks should reward completing or reviewing timesheets rather than maximizing hours worked. The tone should support healthy consistency rather than encouraging overwork.

## Calendar

### 20. Week view on phones — P1

Add a phone-friendly week view between the current month heatmap and single-day timeline. It should make gaps and workload patterns visible without requiring a tablet-sized layout.

A three-day option may work better than seven columns on narrow screens. Users should be able to swipe between periods and jump back to today.

### 21. Drag to create, move, and resize — P1

Allow users to create an entry by dragging across an empty time range and adjust existing entries by moving or resizing their blocks. Editing metadata can happen in a bottom sheet after the time range is chosen.

The interactions need strong snapping, haptics, and accessibility alternatives. Precise text fields must remain available for users who cannot or do not want to drag.

### 22. Device calendar overlay — P1

Overlay selected Android calendars onto the SolidVerdant timeline. Calendar events should remain visually distinct from tracked entries and should not be uploaded automatically.

Users should be able to convert an event into a time entry, choose a suggested project, or hide it. Calendar access must be opt-in with clear privacy language.

### 23. Calendar reconciliation — P1

Go beyond merely displaying events by matching them against existing entries. The app could show that a one-hour meeting has only 35 minutes tracked, or that a completed event has no corresponding entry.

Suggested matches must always be reviewable. Titles and attendee information should remain local unless the user explicitly chooses to use them in an entry description.

## Statistics and reporting

### 24. Complete custom date ranges — P0

Finish the custom-range control already anticipated in the statistics screen. Include common shortcuts such as Today, Yesterday, Last 7 Days, Last Week, This Month, and Previous Month.

Custom ranges are table stakes for meaningful analysis and should also be reusable by history filters and exports.

### 25. Rich statistics filters — P0

Allow statistics to be filtered by organization, project, task, tag, customer, and billable status. The screen should always make its active scope clear.

Selecting a chart segment should drill into the matching entries rather than leaving charts as static decoration.

Use Solidtime’s existing time-entry filter parameters for client, project, task, tag, member, billable state, active state, and date boundaries when the requested range is not already complete in Room. This avoids downloading an unbounded history merely to filter it locally.

### 26. Previous-period comparisons — P1

Compare the selected period with the preceding equivalent period. Useful comparisons include total tracked time, billable time, average per day, and the largest project changes.

The UI should show absolute and percentage changes while handling tiny or empty prior periods gracefully.

### 27. Additional breakdowns — P1

Add breakdowns by task, tag, customer, and billable versus non-billable time. Users should be able to switch the grouping rather than scrolling through many permanent charts.

Long lists should expose the complete data, not silently stop after the top six projects. Small segments can be grouped visually as “Other” while remaining available in details.

The supplied API includes aggregate endpoints with grouping and subgrouping by day, week, month, year, user, project, task, client, billable state, description, and tag. Prefer these endpoints for authoritative large-range reports, with local aggregation as an offline or already-cached fallback.

### 28. Estimates, budgets, and progress — P1

Surface project and task estimates already available from Solidtime data. Show tracked versus estimated time, remaining time, and percentage consumed.

Optional alerts can warn when a project approaches its estimate. This converts statistics from a retrospective view into something that can prevent scope overrun.

### 29. Earnings and billable value — P2

Where the server exposes suitable billing rates, show estimated billable value by period and project. Monetary figures should clearly state their currency and source.

This should remain a reporting aid rather than becoming a complete accounting system.

### 30. CSV export and Android sharing — P1

Allow the current filtered data to be exported as CSV and shared through Android’s system share sheet. Include clear timezone, duration, organization, project, task, tags, description, and billable fields.

CSV delivers most of the practical portability value without the complexity of designing branded reports. Solidtime already provides time-entry and aggregate export endpoints with filters and format selection; use those when online so exported results follow server permissions and reporting semantics. A clearly labelled local export can remain available for cached/offline personal data.

### 31. PDF reports and scheduled summaries — P2

Add readable personal PDF summaries only after filtering and CSV export are mature. A scheduled weekly summary notification or email handoff could show totals, project distribution, and entries needing review.

Avoid building public report hosting inside the app; that is better handled by Solidtime or a web service.

### 32. Rounding and duration display preferences — P1

Support report-only rounding rules and user-selected duration formats, such as `1h 30m`, `1:30`, or `1.50 h`. Raw time entries should remain unchanged unless the user explicitly edits them.

This distinction prevents display preferences from silently changing billable data. Solidtime’s API already accepts `rounding_type` and `rounding_minutes` on reporting and export requests, while the organization resource supplies number, date, interval, and time formats. Fetch those values and use server-side rounding for server reports; reserve local preferences for optional Android display overrides.

## Offline operation and synchronization

### 33. Dedicated sync center — P0

Add a screen showing the last successful sync, pending changes, recent failures, retry schedule, and affected entries. Provide Retry All and item-specific retry actions.

Messages should be actionable and avoid exposing implementation terms. For example: “This edit has not reached the server yet” is more useful than a raw HTTP error.

### 34. Conflict resolution — P0

When the same entry changes locally and remotely, present both versions and allow the user to keep the phone version, keep the server version, or combine fields where safe.

Conflicts should never be resolved silently when doing so could lose time or billing metadata.

### 35. Clear freshness indicators — P1

Show when projects, tasks, organizations, and entries were last refreshed. Cached data should remain usable offline, but the app should communicate when a list may be stale.

This is especially important for the Quick Settings tile, whose state can otherwise diverge from changes made on web or desktop clients.

### 36. Offline capability explanation — P1

Explain which actions work offline and when they will synchronize. The explanation can appear in onboarding and in the sync center rather than interrupting normal use.

Good offline behavior becomes a differentiator only when users understand and trust it.

## Android integration

### 37. Project-specific widgets — P1

Allow a widget to be configured for a favorite or project. Tapping it should start that exact timer or prompt only for the missing description.

Multiple widgets could serve different work contexts, while a distinct active appearance prevents accidentally starting duplicate work.

### 38. Daily-total and goal widgets — P2

Add compact widget variants showing today’s tracked total, billable total, goal progress, or pending review count.

These widgets support awareness without forcing the user into the app and make better use of Android’s resizable widget surface.

### 39. Customizable notification actions — P1

Let users choose which actions appear on the active and idle notifications. Options might include Pause, Stop, Switch Project, Add Note, and Start Favorite.

Android’s action limit means personalization is more valuable than trying to display every control simultaneously.

### 40. Better cross-device state refresh — P0

Refresh the current timer more reliably after returning to the app and provide a lightweight way to reconcile tile, widget, and notification state with the server.

Where continuous remote updates are impossible, show freshness honestly and add a refresh action rather than presenting stale state as authoritative.

## Projects, organizations, and accounts

### 41. Faster organization switching — P1

Remember the most recently used project and filters per organization. Organization switching should make the data boundary explicit and avoid resetting more UI state than necessary.

If switching while a timer is active is unsupported, explain why and offer to stop or retain the current timer before switching.

### 42. Cross-organization overview — P2

Provide an optional combined personal summary across organizations. Individual entries should still retain clear organization labels, and editing should occur within the correct organization context.

This helps consultants and contributors who work across several Solidtime organizations.

### 43. Project progress in selection — P1

Show useful context in project selection, such as customer, active/archived state, recently used status, and estimate consumption. Keep the default list compact and reveal extra detail only where it helps selection.

Users should be able to hide archived projects and pin favorites.

### 44. Multiple saved server profiles — P2

Allow advanced users to save more than one Solidtime endpoint/account profile and switch deliberately. Each profile must keep authentication, cached data, and encryption boundaries separate.

This would be valuable to developers or consultants managing personal and company-hosted instances, but it should not complicate the default login experience.

## Self-hosting, setup, and trust

### 45. Connection test and setup diagnostics — P0

Add a Test Connection action to custom OAuth configuration. Validate the URL, TLS connection, expected API shape, redirect URI, and client ID where possible before launching login.

Errors should explain whether the problem is connectivity, an unsupported server, OAuth configuration, or an invalid endpoint.

### 46. QR-based configuration — P1

Let self-hosting administrators encode the endpoint and client ID in a QR code or configuration link. Scanning or opening it should preview the values before saving them.

This reduces error-prone manual copying while preserving user confirmation and avoiding secrets in the configuration payload.

### 47. Server compatibility information — P1

Display detected server/version information and which optional capabilities are available. If a feature is unsupported, hide it or explain the requirement instead of failing after interaction.

This becomes increasingly important as Solidtime and self-hosted installations evolve at different speeds.

### 48. Privacy and data-management page — P1

Explain what is stored locally, what is sent to the selected server, how tokens are protected, and which optional permissions enable calendar, location, or notifications.

Provide controls to clear caches, revoke the local session, and inspect storage usage without requiring a full logout unless security demands it.

### 49. Diagnostic export — P1

Create a privacy-reviewed diagnostic bundle containing app version, Android version, server capability summary, and sanitized recent sync errors. Never include access tokens, descriptions, project names, or other work content by default.

This can make self-hosting support dramatically easier while maintaining the app’s trustworthiness.

## Accessibility, localization, and personalization

### 50. Complete localization coverage — P0

Move all user-facing calendar and statistics strings into Android resources and update English, Dutch, and Japanese translations. Dates, week starts, number formats, and duration formats should follow locale conventions.

Partially translated screens make a multilingual app feel unfinished even when the underlying feature works correctly. Locale translation remains an app responsibility, but date, interval, time, number, and currency presentation should honor the corresponding organization formats exposed by Solidtime unless the user explicitly chooses an Android-only override.

### 51. TalkBack and keyboard audit — P0

Test the complete timer, calendar, editing, statistics, login, widget configuration, and Quick Settings flows with TalkBack and external keyboard navigation.

Charts need textual summaries, project colors need non-color labels, touch targets need adequate size, and icon-only actions need stable descriptions.

### 52. Large text and responsive layout audit — P0

Verify screens at the largest supported font and display sizes. Timer controls, tags, history rows, bottom sheets, and charts should reflow without clipping or hiding primary actions.

The wide/tablet layouts are a good foundation, but accessibility scaling must also work on narrow phones.

### 53. Reduced motion and color-independent states — P1

Respect system animation preferences and ensure tracking, paused, error, and selected states are not communicated by color alone.

Text, icons, shape, and content descriptions should reinforce every meaningful status.

### 54. Configurable day and week rules — P1

Fetch the user’s Solidtime `week_start` and `timezone` fields and use them as the default across calendar, history, statistics, and exports. If the API permits profile updates, changing these should be presented as a Solidtime account preference because it affects other clients; otherwise SolidVerdant may offer a clearly labelled device-only override.

An optional workday boundary for overnight shifts remains local because no such field appears in the supplied API specification.

Entries crossing midnight need predictable treatment in history, statistics, and calendar views.

### 55. Configurable home experience — P2

Let users choose whether the Track, Calendar, Stats, or Time Inbox screen opens by default. Power users could also hide unused bottom-navigation destinations.

Keep the standard setup opinionated; customization should not turn the first-run experience into a settings exercise.

## Features to defer

### 56. Full invoicing — Defer

Generating tax-compliant invoices, handling currencies, payment states, discounts, tax rules, and branding is a product of its own. SolidVerdant should expose billable value and export clean data before attempting accounting workflows.

### 57. Payroll and attendance management — Defer

Payroll, overtime policy, leave, compliance, and attendance rules vary widely by jurisdiction. These features would move the app away from its focused Solidtime companion role.

### 58. Employee monitoring, screenshots, and GPS surveillance — Avoid

Do not imitate workforce-monitoring products that collect screenshots, detailed application activity, or continuous employee location. If location or activity context is added, it should be private, optional, local-first, and designed solely to help the device owner recall their own time.

### 59. Kiosk and shift scheduling — Defer

Shared-device clock-in, shift assignment, and workforce scheduling solve a different operational problem and require significant administrative interfaces. They do not reinforce the app’s Android personal-capture advantage.

### 60. Full team administration — Defer

Role management, member provisioning, timesheet approvals, locked periods, and audit logs are better handled by Solidtime’s web administration unless the server later exposes a narrow, mobile-appropriate approval workflow.

## Evidence-backed patterns and hard cases we can use

The items in this section distinguish reusable, well-understood behavior from speculative inference. They are implementation constraints for the roadmap, not optional polish.

### 61. Use ActivityWatch as the precedent for private recall

[ActivityWatch](https://github.com/ActivityWatch/activitywatch) is an open-source, privacy-focused automatic activity tracker. Its most reusable idea is architectural: independent watchers produce timestamped events, local storage keeps the evidence under the user’s control, and higher-level views interpret events without changing the originals. Its [documentation](https://docs.activitywatch.net/en/latest/) also demonstrates AFK and window watchers as separate data sources.

If SolidVerdant ever implements activity recall, use the same separation:

1. A collector records a narrow event type.
2. Raw events remain local and expire according to a visible retention setting.
3. Excluded applications are filtered before persistence.
4. A recall screen displays evidence without creating a Solidtime entry.
5. Only an explicit user action converts selected evidence into a draft entry.

Do not copy ActivityWatch wholesale into the main app. First evaluate whether interoperability with an existing ActivityWatch installation is possible, or whether a minimal optional companion is safer than adding monitoring permissions to SolidVerdant.

### 62. Use Toggl Timeline as the conversion-flow precedent

[Toggl Timeline](https://toggl.com/track/automated-time-tracker/) keeps automatically captured activity private and lets the user decide what becomes a time entry. This is the correct interaction boundary for financial records.

The reusable flow is **observe → display → suggest → confirm**, never **observe → submit**. SolidVerdant should preserve the source event, show the proposed start/end and metadata, and require confirmation in the normal entry editor. Rejecting a suggestion must not alter future records silently.

### 63. Use Kimai as the precedent for deterministic timesheet validation

[Kimai](https://github.com/kimai/kimai) is a mature open-source time-tracking system with explicit configuration for overlaps, future entries, budgets, exports, rates, and validation. Its [configuration documentation](https://www.kimai.org/documentation/configurations.html) is a useful catalogue of rules whose behavior can be explained to users.

SolidVerdant can reuse the domain patterns without depending on Kimai code:

- Overlap checks based on interval intersection.
- Optional rejection or warning for entries in the future.
- Configurable handling of concurrent timers.
- Budget and estimate comparisons based on direct arithmetic.
- Explicit working-time and break rules.
- Search and export behavior built from stored fields rather than inferred categories.

Server rules remain authoritative. Local checks should prevent obvious mistakes early but must not claim that an entry is accepted until Solidtime confirms it.

### 64. Exact overlap detection

Two half-open intervals `[startA, endA)` and `[startB, endB)` overlap exactly when:

```text
startA < endB AND startB < endA
```

Using half-open intervals means one entry ending at 10:00 and another starting at 10:00 do not overlap. Running timers use the current instant as a provisional end only for display. Invalid or missing timestamps should produce a data-quality check rather than being coerced into a result.

Important hard cases to test:

1. Adjacent entries with equal boundary times.
2. One interval fully containing another.
3. Identical intervals.
4. Entries crossing midnight.
5. Daylight-saving transitions and repeated local times.
6. A running entry overlapping a completed entry.
7. Entries belonging to different organizations.
8. Server timestamps with different offsets representing the same instant.

All comparisons should use instants. Local dates and timezones are for grouping and presentation only.

### 65. Exact gap detection

Gap detection should operate only inside working windows configured by the user. For each window:

1. Clip completed entry intervals to the working window.
2. Sort them by start instant.
3. Merge overlapping or adjacent intervals.
4. Return uncovered intervals at least as long as the configured minimum gap.

Hard cases include lunch breaks, split shifts, overnight shifts, holidays, entries crossing a window boundary, overlapping entries, daylight-saving changes, and users who intentionally do not account for every working minute. Consequently, gaps are invitations to review—not errors—and the entire feature must be optional.

### 66. User-configured long-timer checks

The first implementation should use an explicit threshold such as “remind me after 4 hours.” It should not calculate an anomaly score from the user’s history.

The notification must say what factual rule fired: “This timer has been running for 4 hours.” Actions should include Keep Running, Stop Now, and Choose End Time. Choosing Keep Running can snooze the warning for a configured duration, but must not teach a hidden model.

This approach is predictable, testable, and safe for users whose work sessions legitimately vary.

### 67. Deterministic suggestion ranking

Quick-start suggestions can be useful without machine learning. Rank only entries or templates the user has previously created, using an inspectable sequence such as:

1. Pinned favorites in the user’s chosen order.
2. Explicit calendar-to-project mappings matching an active event.
3. Most recently used entries.
4. Most frequently used entries within the same weekday and coarse time bucket.

Do not combine these into an opaque confidence score. Display the reason next to each suggestion, deduplicate identical project/task combinations, and fall back to recents when there is insufficient history.

Evaluation should measure whether users accept a suggestion unchanged, edit it, or ignore it. A low acceptance rate means the ranking should be removed or simplified—not made more aggressive.

### 68. Deterministic calendar reconciliation

Calendar reconciliation should begin as a visual comparison, not entity inference. Safe capabilities include:

- Displaying selected calendar events beside tracked entries.
- Showing uncovered portions of an event using interval arithmetic.
- Converting an event into a draft entry.
- Applying a project only through a mapping explicitly saved by the user.
- Remembering that the user dismissed a particular event instance.

Do not infer customer, project, billable status, or productivity from attendees, event title, location, or conference URL. These fields may contain sensitive data and the same event pattern can represent different work contexts.

### 69. Research warning: phone context does not establish work intent

Context-aware activity-recognition research shows that time, location, and sensors can improve recognition of activities, but it does not validate classification of professional intent or billable work. For example, [CAVIAR](https://arxiv.org/abs/1906.03033) evaluated context-aware activity recognition on 26 subjects and emphasizes uncertainty and asking the user when confidence is insufficient. Another [multi-context activity study](https://pmc.ncbi.nlm.nih.gov/articles/PMC4610464/) used only three participants despite reporting strong aggregate classification results.

More directly, the CHI paper [Understanding Personal Productivity](https://par.nsf.gov/servlets/purl/10132820) explains that automated tracking misses off-device work and cannot reliably infer intent from an application or website: the same software can be used for personal and professional purposes, and duration does not establish productivity.

Therefore SolidVerdant must not use phone context to automatically:

- Select a customer or project.
- Mark time billable.
- Label time productive or unproductive.
- Accuse a user of a mistake or suspicious behavior.
- Create, edit, stop, or submit a timesheet.

### 70. Human-in-the-loop safety contract

Research such as [A Reliable Framework for Human-in-the-Loop Anomaly Detection in Time Series](https://arxiv.org/abs/2405.03234) supports bidirectional review and correction rather than blind automation. It is a design reference, not a production library suitable for SolidVerdant’s data.

Every experimental suggestion in SolidVerdant must obey this contract:

1. **No silent writes.** Suggestions never create, edit, delete, stop, mark billable, or submit entries automatically.
2. **Explain the trigger.** Show the exact event or rule that produced the suggestion.
3. **Preserve evidence.** Keep source evidence separate from the proposed timesheet.
4. **Require confirmation.** Conversion occurs through a normal editable draft.
5. **Fail to no suggestion.** Missing data, ambiguous state, or low support produces nothing.
6. **Stay local-first.** Context data remains on-device unless the user explicitly submits selected fields as part of an entry.
7. **Be optional.** Each data source and suggestion category can be disabled independently.
8. **Avoid judgment.** Use neutral language such as “Review this period,” never “unproductive,” “fraudulent,” or “incorrect.”
9. **Allow dismissal.** Users can dismiss a suggestion without being repeatedly challenged.
10. **Measure harm.** Track local acceptance, edit, dismissal, and disablement rates before expanding a feature; do not collect content or behavioral telemetry without separate consent.

The Android [`UsageStatsManager`](https://developer.android.com/reference/android/app/usage/UsageStatsManager) exposes sensitive device-usage history and requires special user-granted access for querying other applications. Any proposal to use it requires a dedicated privacy threat model, a retention design, permission education, and a clear demonstration that calendar and explicit user input cannot solve the problem first.

## Data ownership and local application state

SolidVerdant cannot assume every roadmap concept exists in Solidtime. There are also two different capability sets to distinguish:

1. **What Solidtime’s supplied OpenAPI specification supports.** The repository’s [`api-1.yaml`](api-1.yaml) includes users, organizations, members, clients, projects, tasks, tags, time entries, reports, filtering, aggregation, bulk entry changes, and exports.
2. **What the Android client currently integrates.** The current Retrofit interface and models use only users, memberships, active/history time entries, projects, tasks, and tags. Several server capabilities therefore require Android integration work but do not require inventing local product state.

Project records include a client ID, billable defaults/rates, estimated time, and spent time, while task records include estimates and spent time. The full API additionally provides client resources; organization rate and formatting settings; member rates; overlap policy; user timezone and week-start preferences; report definitions; server-side aggregation, rounding, bulk updates, and exports. Neither the current client nor the supplied API specification exposes working hours, calendar mappings, goals, favorites, reminder rules, or review decisions.

The specification confirms the examples previously identified from Solidtime’s [timesheet documentation](https://docs.solidtime.io/user-guide/timesheet): `UserResource` contains `timezone` and `week_start`, while `OrganizationResource` contains number, currency, date, interval, and time formats. These should be integrated rather than duplicated locally. Prefer the server value whenever it exists and is relevant across clients; use local state when the preference is specifically about this Android app or no supported server field exists.

### 71. Define four data classes before implementing roadmap features

Every new field should be assigned to exactly one primary class:

1. **Solidtime source data:** Authoritative business records fetched from or written to Solidtime, such as time entries, organizations, projects, tasks, tags, billable state, and estimates.
2. **Derived local data:** Recomputable views produced from Solidtime data, such as totals, overlaps, trends, and project frequency. This data may be cached but must not become a second source of truth.
3. **Durable SolidVerdant preferences:** User choices that Solidtime does not represent, such as a long-timer threshold, notification actions, or selected calendars.
4. **Ephemeral device state:** Temporary state such as an open draft, notification presentation, current widget rendering, or a calendar query result.

Do not store a derived value as durable truth when it can be recomputed from authoritative entries. Do not upload a local preference by hiding it inside a time-entry description, tag, or other unrelated Solidtime field.

### 72. Scope local state by server, user, and organization

Local state cannot safely use a single global preference namespace. A person can log out and another person can log in, and the roadmap also proposes multiple self-hosted profiles. At minimum, locally owned business-adjacent state needs a scope composed from:

```text
serverProfileId + userId + organizationId
```

The server profile ID should be a stable local identifier, not the raw access token. Server-wide preferences omit the organization ID; genuinely device-wide appearance preferences may omit all account identifiers.

Examples:

- Theme and reduced-motion choices can be device-wide.
- A favorite timer must be scoped to server, user, and organization.
- Working hours normally belong to a user and may optionally differ by organization.
- Calendar-to-project mappings must be scoped to the organization containing that project.
- Dismissed review checks must include the account and entry/event identity.
- Cached project IDs must never be reused after switching servers merely because the UUID happens to match.

Logging out should remove sensitive caches and ephemeral records. The product must deliberately choose whether non-sensitive conveniences such as favorites survive logout; if they do, they must remain inaccessible to a different logged-in user.

### 73. Solidtime-owned data available to current SolidVerdant

The following fields can be treated as server-owned with the current integration:

| Data                | Current source                   | Appropriate uses                                                                             | Important limitation                                                                                         |
| ------------------- | -------------------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| User                | Solidtime user endpoint          | Identity, account scoping, timezone, and first day of week                                   | Current Android model omits profile photo, timezone, and week start even though the API supplies them        |
| Membership and role | Membership endpoint              | Organization selection and coarse permission awareness                                       | Permission capabilities should still be confirmed by server responses                                        |
| Organization        | Membership/organization endpoint | ID, name, currency, formats, billable-rate policy, employee capabilities, and overlap policy | Current Android model keeps only ID, name, and currency; no working schedule exists in the API specification |
| Time entry          | Time-entry endpoints             | Timer state, history, overlap checks, gaps, statistics, exports                              | Local pending edits can temporarily differ from server state                                                 |
| Client              | Client endpoint                  | Customer names, archived state, filters, and project context                                 | Supported by the API but not integrated or cached by the Android client                                      |
| Project             | Project endpoint                 | Selection, archived status, color, client ID, billable defaults, estimates and progress      | Android already receives these fields but must resolve client names through the unintegrated client endpoint |
| Task                | Task endpoint                    | Selection, completion state, project relation, estimates and progress                        | Task permissions and additional fields may require capability checks                                         |
| Tag                 | Tag endpoint                     | Entry metadata and filters                                                                   | Tags should not be repurposed to store hidden app configuration                                              |

[Solidtime’s feature overview](https://github.com/solidtime-io/solidtime) also lists clients, rates, permissions, and imports, while its [billable-rate documentation](https://docs.solidtime.io/user-guide/billable-rates) describes rate precedence across organization, organization member, project, and project member. The supplied API exposes organization, member, project, and project-member rate resources, but the Android client currently models only the project rate. Earnings features therefore require integrating all applicable levels or obtaining an effective server-calculated value; calculating revenue from the project rate alone could be wrong and must not ship as authoritative reporting.

### 74. Working hours are SolidVerdant-owned unless an API source is added

The current SolidVerdant models and API calls contain no working-hours or work-schedule field. Gap detection must therefore be disabled by default and introduced as an explicit local preference.

A local work-schedule model needs more than one start and end time:

```text
WorkSchedule
- enabled
- timezoneId
- per-day list of zero or more working windows
- minimumGapMinutes
- optional effectiveFrom date
- optional organizationId override
```

Multiple windows support lunch breaks and split shifts. Zero windows represent a non-working day. Overnight shifts should be represented deliberately, either as a window that crosses midnight or as two normalized windows with a clear ownership rule for the workday.

The schedule must store an IANA timezone ID rather than only a UTC offset so daylight-saving transitions remain meaningful. Device-timezone changes should prompt the user to keep the schedule in its original timezone or migrate it; the app must not silently reinterpret historical review results.

This schedule is a review aid, not proof of an employment obligation. It must never be sent to Solidtime as tracked time or used to accuse the user of missing work.

### 75. Feature-by-feature local state inventory

| Feature                | Solidtime data used                                                                 | Additional local state required                                                 | Suggested persistence                                                                                      |
| ---------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Favorite timers        | Project, task, tags, organization                                                   | Favorite ID, label, pinned order, description template, default billable choice | Room, account/organization scoped                                                                          |
| Timer defaults         | Project/task metadata                                                               | Per-project default fields and start behavior                                   | Room or typed DataStore, organization scoped                                                               |
| Long-timer protection  | Active entry start                                                                  | Enabled flag, threshold, snooze duration, last warning for entry                | DataStore for settings; ephemeral/Room for warning state                                                   |
| Tracking reminders     | Active entry and daily totals                                                       | Days, time windows, quiet hours, target, last notification                      | DataStore plus scheduled-work metadata                                                                     |
| Focus/Pomodoro         | Active entry                                                                        | Interval lengths, current focus phase, phase deadline                           | DataStore settings; saved transient session for process death                                              |
| Automation starts      | Project/task IDs                                                                    | User-created automation/favorite ID and allowed action                          | Room; never embed OAuth secrets in intents                                                                 |
| Contextual ranking     | Historical entries                                                                  | Optional ranking preferences; accepted/dismissed counters if retained           | Prefer derived queries; persist only explicit feedback                                                     |
| Description templates  | Project/task/tag IDs                                                                | Template text, placeholders, pinned order                                       | Room, organization scoped                                                                                  |
| History filters        | Entries and catalogue                                                               | Saved-filter name and predicates                                                | Room or serialized DataStore, organization scoped                                                          |
| Undo deletion          | Deleted entry snapshot                                                              | Undo expiry and pending/recreate state                                          | Room transaction/outbox, short retention                                                                   |
| Per-entry sync state   | Entry and server responses                                                          | Pending operation, retry count, error category, timestamps                      | Existing Room/outbox, expanded state machine                                                               |
| Time Inbox             | Entries and project/task/tag metadata                                               | Work schedule, thresholds, dismissed check keys, review completion              | Room plus DataStore settings                                                                               |
| Overlap policy         | Organization `prevent_overlapping_time_entries` and entries                         | Optional device-only review preference when server permits overlaps             | Server policy cached in Room; local review toggle in DataStore                                             |
| Personal goals         | Daily/weekly derived totals                                                         | Goal amount, period, enabled state, completion preference                       | DataStore, user or organization scoped                                                                     |
| Calendar overlay       | None unless creating an entry                                                       | Selected calendar IDs, permission state, display colors                         | DataStore; event content queried ephemerally                                                               |
| Calendar mappings      | Project/task/tag IDs                                                                | Local calendar ID/event rule to favorite or project mapping                     | Room, organization scoped                                                                                  |
| Calendar dismissals    | Entry intervals                                                                     | Event occurrence key, dismissal/review state, expiry                            | Room with limited retention                                                                                |
| Statistics filters     | Server filter/aggregate endpoints and cached entries/catalogue                      | Selected range and grouping; only explicitly local saved views need storage     | Ephemeral by default; server `ReportResource` when saving cross-client reports, Room for device-only views |
| Estimates/progress     | Project/task estimates and spent time                                               | Usually none beyond display settings                                            | Derived/cache only                                                                                         |
| Earnings               | Currency and rates                                                                  | None if authoritative rates become available                                    | Do not persist calculated money as truth                                                                   |
| CSV/PDF export         | Solidtime export and aggregate-export endpoints, or filtered cached entries offline | Last Android share destination only if useful                                   | Generated files via temporary/cache storage; no duplicate business records                                 |
| Rounding display       | Server report rounding parameters and organization formats                          | Optional Android-only display override                                          | Server query parameters for reports; DataStore only for an override                                        |
| Sync center            | Server responses and entries                                                        | Attempts, errors, last success, conflict snapshots                              | Room; sanitized and retention-limited                                                                      |
| Project widget         | Project/favorite IDs                                                                | Per-widget configuration keyed by app-widget ID                                 | Widget preferences/Room, account scoped                                                                    |
| Notification actions   | Active entry                                                                        | User-selected action order and idle behavior                                    | DataStore, device-wide or account scoped                                                                   |
| Organization switching | Memberships                                                                         | Last selected organization and per-organization UI state                        | DataStore, server/user scoped                                                                              |
| Client filtering       | Solidtime client endpoint and project client IDs                                    | No business state beyond cache freshness                                        | Room catalogue cache, organization scoped                                                                  |
| Multiple servers       | Server endpoint and account                                                         | Profile label, endpoint, client ID, selected profile                            | Encrypted secrets store plus non-secret profile DB                                                         |
| Connection diagnostics | Server responses                                                                    | Last test result and timestamp, preferably short-lived                          | Ephemeral or short-retention local record                                                                  |
| Privacy controls       | Cached/local datasets                                                               | Retention periods and enabled data sources                                      | DataStore, device/account scoped as appropriate                                                            |

### 76. Time Inbox check state requires a stable identity

Inbox checks are derived, but user decisions about them may need persistence. A dismissal key should be based on the factual subject and rule version rather than list position. Examples include:

```text
overlap:{entryAId}:{entryBId}:{entryAUpdatedAt}:{entryBUpdatedAt}:{ruleVersion}
gap:{scheduleId}:{localDate}:{startInstant}:{endInstant}:{ruleVersion}
long-timer:{entryId}:{thresholdMinutes}:{entryStart}
calendar:{calendarId}:{eventInstanceId}:{eventStart}:{mappingVersion}
```

If an entry changes, its previous dismissal should normally stop applying because the underlying facts changed. Dismissals should have a retention limit so local storage does not accumulate indefinitely. “Intentional overlap” may be remembered longer than “not now,” but neither state should be uploaded to Solidtime unless Solidtime introduces a purpose-built field.

### 77. Calendar state is device-local and permission-sensitive

Calendar events do not belong to Solidtime unless the user explicitly converts one into an entry. Store only identifiers and mappings needed for the feature; query event title, time, location, and attendees from Android’s calendar provider when rendering.

Prefer storing:

- The selected local calendar ID and owning Android account identifier where necessary.
- A user-visible alias in case provider names change.
- Explicit mapping to a SolidVerdant favorite or Solidtime project/task.
- Dismissal keys for specific event occurrences with an expiry.

Avoid persisting full event descriptions, attendee lists, meeting URLs, or historical event copies. Calendar IDs may change when an account is removed and re-added, so broken mappings need a visible repair state rather than silently targeting another calendar.

Revoking calendar permission should stop queries immediately and offer deletion of mappings and dismissals. Logging into another Solidtime account must not expose mappings created for the previous account.

### 78. Reminder and scheduler state must survive Android constraints

Reminder preferences are local because the current Solidtime data does not describe Android notification behavior. Persist the user’s requested rule, but treat the exact delivery time as best-effort because Android may defer background work.

Required local fields include enabled state, selected weekdays, timezone, earliest/latest reminder times, repeat interval, quiet hours, minimum tracked target if applicable, and the last delivered rule occurrence. The last-delivered key prevents duplicate reminders after reboot or process recreation.

WorkManager is appropriate for deferrable review reminders; exact alarms should not be requested merely to deliver productivity nudges. Re-evaluate schedules after reboot, timezone changes, locale changes, notification-permission changes, and login/logout.

### 79. Sync state must be richer than `SYNCED` or `PENDING`

The current Room entity has `SYNCED` and `PENDING`, plus a pending-delete flag. A sync center and trustworthy per-entry status will require an operation-level state model closer to:

```text
QUEUED
IN_FLIGHT
RETRY_SCHEDULED
BLOCKED_AUTH
CONFLICT
FAILED_PERMANENT
SYNCED
```

The local outbox should retain operation type, target ID, account/organization scope, created time, last attempt, retry count, safe error category, and the payload or before/after snapshots required for conflict handling. Raw tokens, server response bodies, and sensitive descriptions must not appear in diagnostic logs.

“Last synced” also has multiple meanings: last catalogue refresh, last history refresh, last active-timer refresh, and last successful outbox flush. Store and display them separately rather than presenting one misleading global timestamp.

### 80. Conflict resolution requires local and server snapshots

A meaningful conflict screen needs the base version the user originally edited, the pending local version, and the newly fetched server version. SolidVerdant’s current time-entry model does not expose a server revision or `updated_at`, so robust optimistic concurrency may require API support.

Without a server revision or conditional-update mechanism, the app can detect some likely conflicts but cannot prove that an update is conflict-free. Until the API supports this properly:

- Avoid claiming strong conflict detection.
- Re-fetch immediately before risky delayed edits where practical.
- Never merge start/end times automatically.
- Allow field-by-field review only when all three snapshots are available.
- Preserve a local recovery copy before overwriting a pending edit.

### 81. Favorites and templates are local references, not copies of server objects

A favorite should store IDs for its Solidtime organization, project, task, and tags, plus locally owned fields such as label and pinned order. Resolve names and colors from the latest cached server catalogue when displayed.

If a referenced project is archived, a task is completed, a tag is deleted, or access is revoked, mark the favorite unavailable and explain why. Do not silently substitute an item with the same name. Offer repair, partial start without the missing field, or deletion depending on server validation rules.

Descriptions deserve a deliberate choice: a fixed description stored in a favorite is local work content and should receive the same protection as cached entries. A template may be safer than retaining many previously used free-form descriptions.

### 82. Goals, streaks, and review completion are local-only product concepts

The current Solidtime integration contains tracked durations but no personal target, streak, or “review complete” record. Store the goal definition locally and derive progress from entries.

A streak day should be defined by an explicit rule such as “review completed” or “tracked target reached,” including timezone and workday boundary. Never rewrite historical streaks from cached values alone: recalculate when entries sync or change. If the user changes a goal, decide visibly whether the new rule applies prospectively or recomputes history.

These features should remain private personal aids, not team performance metrics.

### 83. Statistics should remain derived wherever possible

Totals, billable percentages, averages, project distributions, trends, previous-period comparisons, overlaps, and estimate progress are deterministic functions of fetched entries and catalogue data. They should be calculated from the Room cache and invalidated when relevant records change.

Persist only explicitly named saved views and display preferences. Avoid maintaining independent aggregate truth unless performance measurements demonstrate a need; stale aggregates are particularly dangerous after offline edits, deletes, organization switches, or timezone changes.

Calculations must document whether running entries are included, how entries crossing date boundaries are allocated, which timezone defines a day, and whether totals use server duration or start/end instants.

### 84. Client names and authoritative earnings need more Android integration

Projects currently expose `clientId`, and the supplied specification already defines client list/create/update/delete endpoints. SolidVerdant does not yet call or cache them, so customer-facing filters and labels require Android client integration and a Room client catalogue—not a new server feature. Showing a raw client UUID is not a useful substitute.

Likewise, Solidtime billable rates can be inherited from organization, organization member, project, or project member, according to the official [billable-rate documentation](https://docs.solidtime.io/user-guide/billable-rates). The specification contains resources for these levels, but the current app’s project-only model is insufficient to calculate authoritative earnings. Before implementing earnings:

1. Determine whether a report/aggregate response returns the effective monetary result; otherwise fetch every level needed to reproduce Solidtime’s precedence exactly.
2. Confirm units, currency, historical-rate behavior, and rounding.
3. Test changes to rates that affect existing entries.
4. Treat the server’s result as authoritative.

Until then, retain billable-duration reporting but defer monetary totals.

### 85. Choose storage by data shape and sensitivity

Use typed DataStore for small preference records such as theme, thresholds, notification action choices, and selected home screen. Use Room for collections, relationships, review decisions, favorites, templates, saved filters, calendar mappings, and sync operations. Use Android Keystore-backed encryption for OAuth secrets and any future server-profile credentials.

Do not put growing JSON collections into preferences merely because serialization is convenient. Collections need migration, indexing, referential cleanup, account scoping, and transactional updates. Conversely, do not create a database table for a single device-wide boolean.

Sensitive local work content includes descriptions, calendar metadata, activity history, customer/project mappings, and conflict snapshots. Minimize it, define retention, exclude it from logs, and ensure the app’s backup policy matches the intended threat model.

### 86. Add deletion and retention rules before collecting new state

Each local dataset needs an owner, retention rule, and deletion trigger:

| Local dataset                      | Recommended retention/deletion behavior                                        |
| ---------------------------------- | ------------------------------------------------------------------------------ |
| Cached Solidtime catalogue/history | Replace through sync; clear on logout for that profile                         |
| Outbox operations                  | Keep until synced or explicitly resolved; preserve recoverable failure details |
| Conflict snapshots                 | Delete after resolution plus a short recovery period                           |
| Favorites/templates                | Keep for the same account unless user deletes them; never expose cross-account |
| Work schedules/goals               | Keep for the same account; offer reset/export if these become substantial      |
| Inbox dismissals                   | Expire by rule type and invalidate when source data changes                    |
| Calendar occurrence dismissals     | Short retention, such as the surrounding review period                         |
| Full calendar event content        | Do not persist by default                                                      |
| Activity recall events             | Deferred; requires an explicit short retention setting before collection       |
| Generated exports                  | Use user-selected destinations or temporary cache with cleanup                 |
| Diagnostic history                 | Sanitized, bounded, and deleted on logout or explicit clear                    |

A privacy page should provide separate actions for clearing convenience data, calendar mappings, diagnostics, and all account data. “Clear cache” must explain whether favorites and work schedules survive.

### 87. Server capability detection must gate optional features

Self-hosted Solidtime instances may run different versions. At login or refresh, detect supported fields and endpoints through an official version/capability mechanism if available. Cache capability results per server profile with a timestamp.

The supplied specification confirms clients, multi-entry update/delete, filters, aggregation, exports, reports, organization formats/overlap policy, and user timezone/week-start fields. Feature gates should test those capabilities on older self-hosted versions rather than assuming every instance matches the checked-in specification. Features depending on effective monetary results or revision tokens still need particular scrutiny because those are not evident in `TimeEntryResource`. A failed API call must not be interpreted as the user having no data. Distinguish unsupported endpoint, forbidden operation, network failure, and empty result.

### 88. Data-source labels should appear in settings and diagnostics

For user trust and developer clarity, settings should identify where behavior comes from:

- **From Solidtime:** entries, clients, projects, tasks, tags, estimates, reports, profile timezone/week start, organization formatting and overlap policy.
- **Calculated on this device:** totals, gaps, overlaps, trends, and suggestion ordering.
- **Stored only on this device:** work schedule, favorites, reminders, mappings, dismissals, and goals.
- **From Android with permission:** calendar events and any future device-usage evidence.

This vocabulary should also appear in diagnostics and feature documentation. It prevents a local preference from being mistaken for something that will automatically follow the user to another phone.

## Recommended delivery sequence

### Phase 1: Make the core trustworthy

1. Finish custom statistics ranges.
2. Add history search and filters.
3. Add per-entry sync states and a sync center.
4. Add overlap and long-timer warnings.
5. Add undo for deletion.
6. Complete localization and accessibility audits.
7. Add connection testing for self-hosted setup.
8. Integrate the complete Solidtime user profile fields and display the server-provided profile picture in the account header and settings surfaces. Cache the image through the normal image-loading cache, show initials as the fallback, avoid persisting a separate bitmap, and clear account-specific cached presentation on logout.

### Phase 2: Create the differentiating review loop

1. Build the Time Inbox.
2. Add end-of-day review and configurable reminders.
3. Add favorites and templates.
4. Add a week calendar and calendar-event overlay.
5. Add statistics filters, comparisons, and drill-down.
6. Add CSV export.

### Phase 3: Own Android-native capture

1. Add deterministic, explainable ranking for favorites and recents; keep broader contextual suggestions experimental.
2. Add project-specific and daily-total widgets.
3. Add customizable notification actions.
4. Publish automation intents for Tasker, NFC, and voice workflows.
5. Add Wear OS controls.

Private activity recall, sensor-based context recognition, location-based suggestions, productivity classification, and automatic metadata inference are not part of Phase 3. They remain deferred research items unless a separate privacy and validation proposal satisfies the safety contract above.

## Success measures

The roadmap should be judged by workflow outcomes rather than raw feature count:

- Median taps required to start a correct timer.
- Percentage of entries that need later metadata correction.
- Number of unresolved sync failures after 24 hours.
- Time required to complete an end-of-day review.
- Percentage of active users using a tile, widget, shortcut, or notification action.
- Percentage of Time Inbox issues resolved rather than dismissed.
- Crash-free and offline-success rates for timer start, stop, edit, and delete actions.

These measures reinforce the product’s strongest potential: dependable capture with low-effort correction, built specifically for Android and Solidtime.
