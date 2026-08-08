---
name: solidverdant-ui-quality
description: Audit and improve SolidVerdant Jetpack Compose UI for shared-component use, design-token compliance, English/Dutch/Japanese resources, semantics, accessibility, large-font layouts, state views, performance, and Roborazzi screenshot coverage. Use when adding or reviewing screens, dialogs, filters, navigation, UI states, or visual changes.
---

# SolidVerdant UI Quality

Keep feature UI consistent with the shared SolidVerdant design system and usable across devices, locales, font scales, input methods, and accessibility services. Review the behavior and state presentation as well as the pixels.

## Inspect before changing

1. Read `AGENTS.md` and inspect the target screen, its ViewModel state, navigation entry, and existing tests.
2. Check `ui/theme` and `ui/components` before adding a new layout, state view, status banner, chip, selector, or dialog variant.
3. Locate the matching production-owned test tags and existing Compose, screenshot, or device tests. Extend those seams instead of matching localized chrome.
4. Preserve unrelated UI changes and do not perform a broad visual rewrite when the request is scoped to one feature.

## Shared design system

- Use `Dimens` for spacing, sizes, minimum touch targets, and other shared measurements.
- Use `MaterialTheme.colorScheme`, `SemanticColors`, and `MaterialTheme.typography` for color and type.
- Reuse `LoadingState`, `EmptyState`, `ErrorState`, `SyncChip`, `SyncStatusBanner`, and other existing components when their behavior fits.
- Add a genuinely reusable component to `ui/components` rather than creating a screen-local fork.
- Do not add `Color(0x...)`, raw `fontSize = ...sp`, or magic `...dp` values in feature packages. Raw token definitions belong in the theme; feature code consumes the tokens.
- Keep healthy screens quiet. Show banners, counters, advanced filters, and recovery affordances when they apply or when the user opens the relevant affordance.

## State and interaction review

For every screen, sheet, dialog, and important inline section, check:

- loading versus refresh, including progress and repeat-tap protection;
- first-use empty, no-results, filtered-empty, and invalid-input states;
- actionable error states with safe, working retry behavior;
- disabled controls with a visible reason;
- success feedback, cancellation, confirmation, and duplicate-submission prevention;
- offline, stale, pending, retrying, failed, conflict, and partial-sync presentation;
- active filters and selections, clear/reset paths, and state after navigation or recreation;
- long lists using lazy composition and stable item identity.

Do not hide a durable or synchronization failure behind a local success animation. The UI must expose useful status and recovery for Room/outbox work.

## Localization

- Put every user-facing label, content description, state message, and action in resources.
- Update English, Dutch, and Japanese together in `app/src/main/res/values`, `values-nl`, and `values-ja`.
- Keep feature-scoped resource files aligned with the existing layout; do not merge unrelated feature strings into the shared files.
- Check key parity, format placeholders, plurals, grammatical order, and long translated strings. Never use a resource key as a visible fallback.
- Use localized resource lookup only when text matching is unavoidable. Prefer stable test tags or entry data in tests.

## Accessibility and layout

- Give icon-only actions stable, meaningful content descriptions; decorative icons remain unannounced.
- Use `Dimens.MinTouchTarget` or an equivalent minimum 48 dp target for every interactive control.
- Check semantic grouping, traversal order, role, state description, and whether merged or unmerged semantics are appropriate for Compose tests and TalkBack.
- Keep controls usable with keyboard navigation and switch or screen-reader focus.
- Exercise narrow phones, tablets, large font/display scaling, long translations, empty/error content, and large datasets.
- Make important state changes observable without relying on color alone, especially sync, conflict, error, and selected states.

Production-owned test tags should be re-exported through `app/src/androidTest/.../e2e/TestTags.kt`. Match tags or entry data rather than English, Dutch, or Japanese navigation text.

## Performance and composition

- Keep the Track screen cheap to recompose while its elapsed timer updates every second.
- Memoize filtering and aggregation using only the data they depend on.
- Keep per-row work off the main thread and avoid repeated `ZonedDateTime.parse` or formatter construction; use `IsoTimes` where appropriate.
- Avoid eager project, task, tag, history, and catalogue composition.
- Check screenshot and device interactions for accidental repeated taps, unstable keys, and state resets during recomposition.

## Screenshot and test proof

Use the existing pure-JVM Roborazzi matrix. The documented command is:

```sh
./gradlew :app:recordRoborazziDebug
```

Run it through the pinned development environment when validating a change. The current matrix covers light/dark themes and phone/tablet sizes, writing generated images under `.github/screenshots/generated/` and the selected README hero images under `.github/screenshots/readme/`.

When a visual change is intentional:

1. Add or update the smallest focused screenshot test or host state that proves it.
2. Inspect the generated images at phone and tablet sizes and both themes.
3. Check resource-driven states separately when locale or text length is part of the change; the current screenshot matrix is not automatically a full locale matrix.
4. Keep only intentional generated-image changes and explain them in the handoff.

Also run focused Compose/device tests, `spotlessCheck`, and the pinned Android gate as appropriate. Run `detekt` separately when requested; it is opt-in and outside the default gate.

## Report findings

For a review, report each finding with the affected file or screen, the violated rule, a concrete reproduction or evidence path, and the smallest safe correction. Distinguish implementation defects from missing test coverage and from environment-blocked validation. Do not call the UI complete from screenshots or compilation alone.
