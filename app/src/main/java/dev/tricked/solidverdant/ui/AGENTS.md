# Compose UI subtree guidance

Read the repository-root `AGENTS.md` first. These rules add UI-specific constraints for files under
this directory.

- Reuse `ui/components` and `ui/theme`; consume `MaterialTheme`, `SemanticColors`, and `Dimens`.
  Do not add feature-local literal colors, raw `sp`, or unexplained `dp` values.
- Put every visible string, content description, state message, and action in English, Dutch, and
  Japanese resources. Check placeholders, plural forms, long translations, and key parity.
- Cover loading, empty, no-results, error, retry, disabled, stale/offline, pending, conflict, and
  success states when they apply. Preserve selections and useful state across navigation and
  recreation.
- Give controls useful semantics, stable production-owned test tags, meaningful icon descriptions,
  and at least a 48 dp touch target. Check large fonts, narrow phones, tablets, keyboard order, and
  TalkBack behavior; do not rely on color alone for important state.
- Keep Track inexpensive to recompose. Memoize derived work, use lazy collections and stable keys,
  and avoid parsing or formatter construction in list rows.
- Extend focused Compose, screenshot, or device coverage for visual and interaction changes. Match
  stable tags or entry data in tests rather than localized visible text.
