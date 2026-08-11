# Data and sync subtree guidance

Read the repository-root `AGENTS.md` first. These rules add data-layer constraints for files under
this directory.

- Keep Room as the local source of truth. A user mutation and its outbox operation must commit in
  one transaction through `TimeEntryRepository`; UI, notifications, widgets, tiles, and workers
  must not upload mutations directly.
- Preserve account and organization ownership on every entity, query, cache, outbox operation, and
  cleanup path. Templates are the intentional surviving exception and remain endpoint/user scoped.
- Treat the server as authoritative. Keep optimistic state, idempotency keys, bounded retry,
  dead-letter visibility, conflict snapshots, and explicit resolution intact.
- Pulls must not overwrite pending edits or conflicts. Do not silently rewrite user data to satisfy
  local overlap, duration, or boundary checks.
- Keep OAuth and PKCE secrets in the encrypted authentication storage. Do not log tokens, work
  descriptions, catalogue names, calendar content, or raw remote responses.
- For Room schema, migration, provider, Hilt binding, or worker changes, add focused failure and
  recovery coverage and follow the root clean-build rule before trusting device results.
