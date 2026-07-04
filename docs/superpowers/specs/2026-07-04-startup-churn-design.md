# SolidVerdant Startup Churn Reduction — Design

Date: 2026-07-04
Status: Approved by user (Approach A + optimistic revalidation default)

## Problem

Frame captures of app startup (app/src/main/startup-frame-capture/, analyzed in
frame-changes.txt) show ~11 visible UI state changes between the OS splash and
the settled Time Tracking screen:

1. Light-grey "Solidtime Login" screen flashes even though the user is logged
   in, in the wrong (light) theme.
2. Dark skeleton UI appears, then the header populates in three stages
   (title → username → organization + continue-last-entry card).
3. The form disables (Start button greys out), a refresh spinner overlays the
   Description field, history entries land, then project tags and customer
   counts land in a second pass, then the form re-enables and the spinner
   retracts.

Root causes, confirmed in code:

- No SplashScreen keep-on-screen condition: Compose renders before auth
  state, theme, or any data is known (MainActivity.kt).
- `AuthViewModel.isLoggedIn` is `StateFlow<Boolean>` with
  `initialValue = false` → LoginScreen always renders first for logged-in
  users (AuthViewModel.kt).
- `appTheme.collectAsState(initial = AppThemeMode.SYSTEM)` → light theme
  renders before the stored theme loads (MainActivity.kt).
- Only projects/tasks are cached (CacheDataStore.kt, 5-minute TTL). User,
  memberships, time entries, active entry, and tags are network-only, so each
  response lands in its own `uiState.copy()`.
- Network waterfall: `loadUserData()` (user + memberships requests) must
  complete before `loadAllData()` can start, because it needs the org/member
  ID — which is already stored locally but unused for this purpose.
- Single `TrackingUiState.isLoading` flag drives both the pull-to-refresh
  spinner and `enabled = !uiState.isLoading` on every form control, so a
  background refresh visibly disables and re-enables the form.

## Goal

- At most 2–3 visible UI states at startup for a logged-in user:
  splash → fully populated screen (→ in-place data update only if the server
  differs from cache).
- No login screen or light-theme flash for logged-in users.
- Faster time-to-fresh-data via optimistic parallel fetching (default on,
  user-toggleable).

## Non-goals

- No Room/SQLite offline-first rewrite.
- No feature removal; tile/notification/widget services untouched except
  where they consume changed APIs.
- No redesign of the logged-out login experience beyond correct theming.

## Design

### 1. Startup gate (kills the login/theme flash)

- Add `androidx.core:core-splashscreen` and a `Theme.App.Starting` splash
  theme; `MainActivity` calls `installSplashScreen()` with
  `setKeepOnScreenCondition` holding until a single `startupReady` state is
  true.
- `startupReady` means three DataStore-backed values have resolved:
  - Auth state: replace the boolean-with-false-default by a tri-state
    `AuthState { Unknown, LoggedIn, LoggedOut }` derived from
    `AuthRepository.isLoggedIn`; splash holds while `Unknown`.
  - Theme mode: expose a suspend one-shot read (or first emission) of
    `AppThemeMode`; the theme composable never renders with a guessed
    default.
  - Snapshot hydration (section 2) has been applied to the viewmodels
    (hydration completes with "no snapshot" for first-run/logged-out users).
- Signal: each viewmodel exposes its readiness as state
  (`AuthViewModel.authState != Unknown` + `hydrated` flags;
  `TrackingViewModel.hydrated`; theme exposed as nullable-until-loaded), and
  `MainActivity` combines them into one `startupReady` boolean that
  `setKeepOnScreenCondition` reads.
- All three are local reads (tens of milliseconds); the splash hold is not
  perceptible relative to current cold start.
- Logged-out users go straight to LoginScreen in the correct theme; the
  existing login flow is otherwise unchanged.

### 2. Screen snapshot cache (kills skeletons and staged loading)

- Generalize `CacheDataStore` into a versioned, org-keyed JSON snapshot:

  ```
  ScreenSnapshot(
    version: Int,            // schema version; mismatch → discard snapshot
    organizationId: String,
    user: User,
    memberships: List<Membership>,
    currentMembershipId: String,
    timeEntries: List<TimeEntry>,   // most recent page, as shown today
    activeEntry: TimeEntry?,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    savedAtEpochMs: Long
  )
  ```

- Ownership: a new `@Singleton SnapshotRepository` wraps the snapshot store
  and is the only writer/reader. It exposes
  `updateAuthSlice(user, memberships, currentMembershipId)`,
  `updateTrackingSlice(organizationId, timeEntries, activeEntry, projects,
  tasks, tags)`, and `read(): ScreenSnapshot?`. Each `update*` call merges
  its slice into the stored snapshot in one DataStore edit, so neither
  viewmodel needs the other's state. `AuthViewModel` calls
  `updateAuthSlice` after successful user/membership loads;
  `TrackingViewModel` calls `updateTrackingSlice` after every successful
  `loadAllData` completion and after mutations that change what the screen
  shows (start/stop/pause/resume/edit/delete entry).
- Read once at startup via `SnapshotRepository.read()`; hydrates
  `AuthUiState` (user, memberships, currentMembership) and
  `TrackingUiState` (entries, active entry, projects, tasks, tags) each in
  one atomic `StateFlow` emit — the screen renders fully populated in its
  first frame.
- No TTL for display: stale data always shows immediately and the background
  refresh corrects it (stale-while-revalidate). `savedAtEpochMs` is kept for
  debugging/future use only.
- Corrupt/unversioned/old-version snapshot → discard, log via Timber, fall
  back to current skeleton behavior for that launch.
- Organization switch (`selectMembership`) keeps the old behavior of
  clearing cached org-scoped data: the snapshot is replaced on the next
  successful load of the new org; until then the screen shows the existing
  loading state (org switching is an explicit user action, mid-session churn
  there is acceptable and out of scope).
- Logout clears the snapshot along with existing cleared state.
- The 5-minute-TTL `getCachedProjects`/`getCachedTasks` paths are removed in
  favor of the snapshot. Their verified consumers —
  `service/TimeTrackingTileService.kt` (lines ~700–701) and
  `ui/tile/ProjectSelectionViewModel.kt` (lines ~60–61) — are updated
  mechanically to read from `SnapshotRepository`.
  (`TimeTrackingNotificationService.kt` does not use the TTL cache and is
  untouched.)

### 3. Optimistic parallel refresh (kills the waterfall; default ON, toggleable)

- New setting in `SettingsDataStore`: `optimisticRefresh`, default `true`,
  exposed as a toggle in the existing settings/config UI ("Optimistic
  refresh — fetch data immediately using cached account info; uses a few
  extra API calls").
- With no snapshot present (first run, post-logout, discarded snapshot),
  startup uses the sequential path regardless of the toggle — there is no
  cached membership to be optimistic with.
- Default (optimistic) startup with a snapshot present:
  - Immediately start `loadAllData(cachedOrgId, cachedMemberId)` — entries,
    projects, tasks, tags, active entry — in parallel with
    `getCurrentUser()` + `getMyMemberships()` revalidation.
  - If revalidation confirms the cached membership: user/memberships state
    updated only if changed (equality check → usually no visible change).
  - If revalidation yields a different membership (revoked/changed): re-run
    `loadAllData` for the correct org and replace the snapshot; UI shows the
    new org's data when it lands. Auth errors fall through to the existing
    logout/error handling.
  - Cost: the same requests as today, just earlier; the "extra" calls are
    the user/membership revalidation pair each launch, accepted by design.
- Toggle OFF (frugal): current sequential behavior — revalidate user +
  memberships first, then `loadAllData` — but still hydrating from the
  snapshot first, so the UI is populated either way; only freshness arrives
  later.
- All `loadAllData` results are applied in one combined `uiState.copy()`
  (loaders return data instead of individually mutating state), so a
  changed dataset lands as one visible update, and an unchanged one lands as
  zero (structural equality skip).

### 4. Sync indicator + loading-state split (kills form disable/spinner churn)

- Split `TrackingUiState.isLoading` into:
  - `isRefreshing`: true only for user-initiated pull-to-refresh and the
    explicit header refresh button → drives `PullToRefreshBox`.
  - `isSyncing`: true during any background/startup refresh → drives a
    subtle indicator: the existing header refresh icon rotates while
    syncing (small, non-blocking, no layout shift).
  - `isMutating`: true during user-initiated mutations
    (start/stop/pause/resume/edit/delete) → this alone drives
    `enabled = !…` on form controls, preserving today's double-submit
    protection.
- Background refreshes never disable the form and never show the
  pull-to-refresh spinner.
- Failure of a background refresh with a populated screen: keep showing
  cached data, stop the sync indicator, surface the existing error snackbar
  only for user-initiated refreshes; startup sync failures log via Timber
  and show a subtle stale indicator only if we already have one (we don't —
  so: silent, matching "cached data is better than an error dialog").

## Error handling summary

- Snapshot read failure/corruption → discard + skeleton fallback (never
  crash, never block splash beyond the read attempt).
- Revalidation reveals membership change → reload for correct org, replace
  snapshot.
- Auth/token failure during optimistic load → existing TokenAuthenticator/
  logout paths unchanged; snapshot cleared on logout.
- Background refresh network failure → cached UI persists, sync indicator
  stops, no modal/blocking error.

## Testing

- Unit tests: snapshot round-trip (serialize/deserialize, version mismatch
  discard, corrupt JSON discard); tri-state auth derivation; optimistic vs
  frugal load ordering (using a fake repository, assert request order and
  that state lands in a single emit); loading-flag transitions
  (isSyncing/isRefreshing/isMutating independence).
- Migration verification: quick-settings tile and its project-selection
  dialog (`ProjectSelectionViewModel`) still resolve project/task names
  after the TTL-API removal.
- Manual verification: re-run capture_startup_frames.sh and count distinct
  visual states — target ≤3 for warm start (snapshot present), and verify
  no login/light-theme flash; verify first-run (no snapshot) and logged-out
  paths still work; verify org switch and the settings toggle both ways.

## Affected files (expected)

- MainActivity.kt (splash gate, tri-state routing)
- ui/auth/AuthViewModel.kt (tri-state, snapshot hydration, revalidation)
- ui/tracking/TrackingViewModel.kt (hydration, combined state application,
  optimistic path, loading-flag split, snapshot writes)
- ui/tracking/TrackingScreen.kt (isRefreshing/isSyncing/isMutating wiring,
  header sync indicator)
- data/repository/SnapshotRepository.kt (new; owns snapshot reads/writes)
- data/local/CacheDataStore.kt (snapshot storage backend; TTL paths removed)
- data/local/SettingsDataStore.kt (optimisticRefresh toggle)
- ui/config or settings surface (toggle UI), theme startup read,
  build.gradle.kts + splash theme resources
- service/TimeTrackingTileService.kt, ui/tile/ProjectSelectionViewModel.kt
  (mechanical: consume SnapshotRepository instead of removed TTL cache APIs)
