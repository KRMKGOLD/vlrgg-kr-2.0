# Design

## Source of Truth

- Status: Active
- Last refreshed: 2026-07-12
- Primary product surfaces: Android and iOS mobile apps built with Compose Multiplatform.
- Evidence reviewed:
  - `AGENTS.md`
  - `docs/README.md`
  - `docs/app-arch/app-arch.md`
  - `docs/app-arch/ui-layer.md`
  - `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/App.kt`
  - `/Users/seongha/Desktop/Agentic-Attendance-Service/DESIGN.md` as the source document structure
- Ownership: This file is the canonical contract for shared visual language, interaction behavior, accessibility, and UI review. Feature-specific requirements live in `docs/feature/<feature>/`.

## Brand

- Personality: Competitive, immediate, focused, and editorial without becoming visually noisy.
- Trust signals: Clear match times and statuses, recognizable team and event identity, explicit data freshness, stable navigation, and predictable loading/error behavior.
- Avoid:
  - Generic business-dashboard styling.
  - Decorative esports effects that reduce readability.
  - Using red across every surface until nothing has emphasis.
  - Marketing-style hero layouts inside information-dense app screens.
  - Presenting scraped or inferred data as certain when the source is unavailable or incomplete.

## Product Goals

- Goals:
  - Make VLR.GG Valorant esports information comfortable to browse on Android and iOS.
  - Let users scan upcoming and completed matches quickly.
  - Make event, team, and player relationships easy to follow across detail screens.
  - Keep the current content state, update state, and failure state unambiguous.
  - Preserve a consistent shared experience across platforms while respecting safe areas and native interaction expectations.
- Non-goals:
  - Reproduce the desktop website pixel for pixel.
  - Expose VLR.GG HTML structure, scraping selectors, or server-internal source models in the UI.
  - Add decorative motion, large promotional surfaces, or complex customization before core browsing flows are validated.
  - Define feature-specific API fields or navigation details in this file.
- Success signals:
  - Users can identify match status, participants, event, and scheduled time from a list without opening each detail.
  - Moving from a match to related event, team, or player information feels predictable.
  - Loading, empty, error, and stale states never look like valid populated content.
  - The same semantic tokens and component states are used across Android and iOS.

## Personas and Jobs

- Primary personas:
  - Active follower: repeatedly checks today's matches and recent results.
  - Event follower: tracks brackets, standings, schedules, and event progress.
  - Team or player follower: looks up roster, profile, and related match information.
- User jobs:
  - Find what is live, upcoming, or recently completed.
  - Understand who is playing, in which event, and when.
  - Move from a match to related event, team, and player details.
  - Refresh information and understand when data cannot be loaded.
- Key contexts of use:
  - Short, repeated mobile sessions.
  - Time-sensitive checks before or during matches.
  - Longer browsing sessions across related esports entities.

## Information Architecture

- Primary navigation:
  - Bottom navigation order: News, Matches, MyPage, Events, About.
  - Default destination: MyPage.
  - Every root destination exposes Search through a shared Top App Bar.
  - Search opens over the current destination and Back restores the previous destination and state.
- Core routes/screens:
  - News list and detail
  - Match list and detail
  - Event list and detail
  - Search
  - Series, Team, and Player detail
  - MyPage favorites and notification settings
  - About
- Content hierarchy:
  - Live and time-sensitive match state first.
  - Participants, scheduled time, and result second.
  - Event context and supporting metadata third.
  - Related navigation and secondary statistics after the primary reading task.

## Design Principles

- Status before decoration: Live, upcoming, completed, postponed, empty, and failed states must be understood before visual flourish is added.
- Scan first, inspect second: List surfaces expose the minimum information needed to choose an item; detail screens carry deeper context.
- Red has a job: The primary red identifies high-priority action, selection, or live emphasis. It is not a default background for large areas.
- Shared semantics, platform-aware behavior: Android and iOS use the same content hierarchy and tokens while respecting platform safe areas, back behavior, and accessibility settings.
- Honest data boundaries: The UI distinguishes unavailable, stale, partial, and empty data instead of inventing or silently retaining misleading values.
- Tradeoffs:
  - Prefer dense, stable information layouts over large editorial compositions.
  - Prefer explicit labels and timestamps over color-only or icon-only meaning.
  - Prefer a small reusable token set over per-screen styling exceptions.

## Visual Language

### Color Palette

The palette contains four unique color values and five semantic entries. The duplicated `#111823` is intentional: `Dark Canvas` and `Primary Ink` share a value while keeping separate semantic roles.

The first MVP ships with Light theme only. Dark palette roles remain documented for future design work but are not an implementation or screenshot acceptance target until this file is explicitly refreshed for Dark Mode.

| Semantic token | Color | Hex | RGB | Primary role |
| --- | --- | --- | --- | --- |
| Valorant Red | [sample](https://www.color-hex.com/color/ff4654) | `#FF4654` | `rgb(255, 70, 84)` | Primary accent, selected state, live emphasis, high-priority action |
| Deep Red | [sample](https://www.color-hex.com/color/ba3a46) | `#BA3A46` | `rgb(186, 58, 70)` | Pressed/strong accent, destructive emphasis, accessible red surface with white text |
| Dark Canvas | [sample](https://www.color-hex.com/color/111823) | `#111823` | `rgb(17, 24, 35)` | Dark theme canvas, dark surface, inverse container |
| White | [sample](https://www.color-hex.com/color/ffffff) | `#FFFFFF` | `rgb(255, 255, 255)` | Light theme canvas/surface, inverse text |
| Primary Ink | [sample](https://www.color-hex.com/color/111823) | `#111823` | `rgb(17, 24, 35)` | Light theme text, icon, and primary button label |

Color usage rules:

- Light surfaces use `White` with `Primary Ink` for the default text and icon pairing.
- Dark surfaces use `Dark Canvas` with `White` for the default inverse pairing.
- A `Valorant Red` filled control uses `Primary Ink` text or icon. White normal-size text on `Valorant Red` does not meet the target contrast.
- A `Deep Red` filled control may use `White` text. Do not use `Dark Canvas` text on `Deep Red` for normal-size copy.
- Borders, dividers, disabled surfaces, tonal backgrounds, hover/pressed overlays, and scrims must be alpha variants of these tokens rather than new unapproved hex colors.
- Status is never encoded by color alone. Pair color with a label, icon, shape, or position.
- Do not place `Valorant Red` and `Deep Red` adjacent when the boundary itself must be perceived; their contrast is for hierarchy, not separation.

### Typography

- Use the platform-appropriate system sans-serif until a shared bundled font is explicitly approved.
- Display: 28sp, semibold, compact line height. Use only for a primary page title or major score/status value.
- Page title: 22sp, semibold.
- Section title: 16sp, semibold.
- Body: 14sp, regular.
- Body strong: 14sp, semibold.
- Compact metadata: 12sp to 13sp, regular or medium.
- Team names, player handles, event names, scores, and timestamps must remain readable at the user's system font scale.
- Avoid oversized editorial type inside cards, lists, bottom sheets, and dialogs.

### Spacing and Layout Rhythm

- Use a 4dp base grid with common steps of 4, 8, 12, 16, 24, and 32dp.
- Use 16dp as the default phone horizontal content inset unless a platform safe area requires more.
- Keep list row heights stable for scanability; allow content growth rather than clipping when font scale increases.
- Group related match metadata tightly and separate unrelated sections with spacing, not repeated nested cards.
- Keep the primary score/status alignment stable between loading and populated states.

### Shape, Radius, and Elevation

- Default component radius: 8dp.
- Compact chips may use a pill shape when their text remains short.
- Use elevation sparingly for dialogs, sheets, floating controls, and active overlays.
- Prefer borders, background contrast, and spacing over heavy shadows.

### Motion

- Motion communicates state change or navigation continuity; it is not decoration.
- Use short, subtle transitions for selection, content replacement, and detail expansion.
- Avoid animation that delays access to live scores, schedules, refresh, or navigation.
- Respect reduced-motion settings and provide an immediate state change when motion is reduced.

### Imagery and Iconography

- Team, event, country/region, and player imagery supports identity but never replaces a text label.
- Preserve image aspect ratio and provide stable placeholders to prevent layout shift.
- Use familiar platform icons for navigation, refresh, back, favorite, filter, and external link actions.
- Provide content descriptions for meaningful imagery; mark repeated decorative imagery as non-semantic.

## Components

- Existing components to reuse: No production design components exist yet; the current `App.kt` is placeholder Compose content.
- Initial shared component candidates:
  - Match row/card
  - Match status label
  - Event identity row
  - Team identity row
  - Player identity row
  - Score or series summary
  - Filter/tab control
  - Loading skeleton
  - Empty/error state
  - Confirmation dialog or bottom sheet when a feature requires one
- Variants and states:
  - Live, upcoming, completed, postponed/cancelled when supported by the server contract
  - Selected, pressed, focused, disabled
  - Loading, empty, populated, partial, error, stale
- Token/component ownership:
  - Color, typography, spacing, and shape tokens belong under `app/shared/src/commonMain/.../ui/theme`.
  - A component remains feature-local until at least two real features reuse the same behavior and visual contract.
  - Feature documents own their data fields and acceptance criteria; this file owns shared visual and interaction semantics.

## Accessibility

- Target standard: WCAG 2.2 AA intent for contrast and interaction, adapted to Android and iOS accessibility APIs.
- Keyboard/focus behavior: Any desktop, tablet keyboard, or accessibility focus path must follow visual reading order and expose visible focus.
- Contrast/readability:
  - `Primary Ink` on `White`: 17.82:1.
  - `White` on `Dark Canvas`: 17.82:1.
  - `Primary Ink` on `Valorant Red`: 5.31:1.
  - `White` on `Deep Red`: 5.55:1.
  - `White` on `Valorant Red` is 3.36:1 and is not approved for normal-size text.
- Screen-reader semantics:
  - Read match state, participants, score, event, and scheduled time in a meaningful order.
  - Do not announce a team logo separately when the adjacent team name already conveys the same information.
  - Combine split visual score elements into one coherent accessibility description when appropriate.
- Touch targets: Interactive targets are at least 48dp where platform guidance allows; compact visual elements may use a larger invisible touch area.
- Reduced motion and sensory considerations: Respect system animation and font-scale settings. Never rely on motion, color, or sound as the only status cue.

## Responsive Behavior

- Supported devices: Android and iOS phones are the first target; the shared layout must remain usable on tablets, foldables, and resizable desktop previews without duplicating product logic.
- Layout adaptations:
  - Compact width: single-column content, bottom navigation when the final navigation model uses tabs, and full-width detail sections.
  - Medium width: wider content gutters and optional two-column metadata where reading order remains clear.
  - Expanded width: list-detail is allowed for information-dense browsing after the relevant feature defines selection and back-stack behavior.
- Touch/hover differences:
  - Touch layouts keep generous targets and do not reveal essential actions only on hover.
  - Pointer hover may add feedback but must not be required to discover content or actions.
- Safe areas: Top bars, bottom navigation, sheets, and edge-to-edge content respect platform insets and gesture regions.

## Interaction States

- Loading: Use skeletons that preserve the approximate final layout and avoid replacing the whole app shell with a spinner.
- Empty: Explain what has no data and distinguish a valid empty result from a load failure.
- Error: Use a concise safe message and an explicit retry action when retry is meaningful. Do not expose exceptions, URLs, selectors, or raw server details.
- Success: Prefer the updated content itself as confirmation; use transient feedback only when the result would otherwise be unclear.
- Disabled: Keep disabled actions legible and explain the reason when it is not obvious from context.
- Offline/slow network: Keep navigation responsive, show ongoing refresh state, and do not imply that stale content is current.
- Partial/stale data: Identify the affected section and preserve usable content only when the feature's data policy explicitly permits it.

## Content Voice

- Tone: Direct, compact, neutral, and esports-aware.
- Primary language: Korean UI copy. Preserve official team names, player handles, event names, map names, and region abbreviations when translation would damage recognition.
- Terminology:
  - Use one Korean term consistently for match, event/tournament, team, player, live, upcoming, and completed states once product planning confirms the glossary.
  - Display dates and times with an explicit locale/time-zone policy defined by the relevant feature.
- Microcopy rules:
  - Prefer specific state and recovery guidance over generic failure messages.
  - Prefer exact timestamps over vague phrases when precision affects match discovery.
  - Keep status labels short enough to scan but never abbreviate away their meaning.

## Implementation Constraints

- Framework/styling system:
  - Compose Multiplatform shared UI in `app/shared/src/commonMain`.
  - Navigation 3 for app navigation.
  - Metro DI for dependency injection.
  - Material 3 components may provide accessible behavior, but their default palette must be replaced by project theme tokens.
- Design-token constraints:
  - Define semantic tokens rather than using raw hex values in feature composables.
  - Implement the Light scheme for MVP. Do not expose a Dark theme toggle until the Dark scheme is reviewed in this file.
  - Do not add a new color value without updating this file and verifying contrast for its intended foreground/background pair.
- Performance constraints:
  - Keep lists lazy and image loading bounded for information-dense match/event screens.
  - Avoid layout shifts when remote logos or images load.
  - Refresh and network latency must not block navigation or obscure the current content state.
- Compatibility constraints:
  - Prefer `commonMain` implementations before platform-specific UI.
  - Platform-specific code is limited to behavior that cannot be expressed safely in shared Compose code.
  - UI must tolerate safe-area differences, dynamic type/font scale, and locale-dependent text growth.
- Test/screenshot expectations:
  - Preview or screenshot representative Light-theme loading, empty, error, and populated states once real screens exist.
  - Verify the approved foreground/background contrast pairs when theme tokens change.
  - Add focused UI-state tests after a feature's screen contract stabilizes.

## Open Questions

- [ ] Confirm the official Korean terminology and time-zone display policy. Owner: product/content. Impact: list density, date formatting, and accessibility labels.
- [ ] Confirm whether a shared bundled typeface is required; system sans-serif is the active default until then. Owner: design. Impact: brand expression, binary size, and text metrics.
