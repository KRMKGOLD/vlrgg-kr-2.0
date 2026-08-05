# Design System: VLR.GG Mobile 2.0

**Stitch project:** [VLR.GG Mobile 2.0 — Canonical 88 Screens](https://stitch.withgoogle.com/projects/8765150675340843101) (`8765150675340843101`)

**Canonical Stitch design-system asset:** `Tactical Editorial` (`assets/093ced9b20974146bf4905595b92db45`). Use it for subsequent state-screen generation together with the explicit `390 × 884` resource / centered `360 × 780` app-artboard prompt contract below.

**Historical reference project:** [VLR.GG Mobile 2.0](https://stitch.withgoogle.com/projects/15805645626358472639) (`15805645626358472639`)

**Status:** Active — Step 1 contract

**Last refreshed:** 2026-08-05
**Applies to:** Compose Multiplatform UI in `app/shared/src/commonMain`; Android and iOS mobile first.

This is the canonical visual, interaction, and accessibility contract for the app. Feature documents own their data and flows; [the app architecture](docs/app-arch/app-arch.md) owns placement and runtime boundaries. Step 1 ships the Light theme (`Theme.kt`, `Colors.kt`, `Typography.kt`, `Dimensions.kt`) and shared `VlrButton`, `VlrIconButton`, `VlrSearchField`, and `StatusChip` contracts in `commonMain`; this document remains the handoff for feature adoption and visual validation.

## Evidence and confidence

The contract uses Stitch project metadata (`MOBILE`, `LIGHT`, `ROUND_EIGHT`, custom color `#FF4654`) and prior project exports as historical visual evidence. The 19 historical Stitch objects, including corrected/duplicate variants such as **Matches Root Corrected**, **Match Detail Refined**, **MyPage Root Corrected**, and **About Root Corrected**, are not the canonical reconstruction or a current implementation baseline. The approved screen-level contract is recorded in this committed `DESIGN.md` and `docs/feature/**`; feature screens and navigation remain unimplemented unless the implementation-status tables explicitly say otherwise.

- **[Observed]** means a value or behavior appears in project metadata, a Stitch source, or a screenshot.
- **[Normalized]** means the sources differed slightly; this file chooses one durable token without inventing a new visual direction.
- **[Inferred]** means it is an implementation or accessibility rule required to turn the observed design into a safe shared Compose contract. It is not represented as a Stitch screenshot fact.

The project contains a previous `Tactical Information Architecture` asset, older reference screens, and corrected Stitch exports. They are all historical evidence and do not override the committed canonical contracts in `DESIGN.md` and `docs/feature/**`. Source exports vary between `#FF4654`/`#FF4655`, `Public Sans`/`Inter`, and nearby neutral values; the normalized tokens below resolve those export differences deliberately.

The canonical wireframes are an approved reconstruction brief, not evidence that the current app already implements the 12 product surfaces. The canonical Stitch project now contains 12 normalized representative Base screens for review; the 88 independent state screens remain pending until those Base screens pass review. Stitch generation and feature implementation remain separate work; this file records the visual contract and validation criteria only.

## Canonical screen contract (2026-08-03)

- Canonical product review frames use a fixed `360 × 780px` app artboard centered inside Stitch's `390 × 884px` mobile resource. The resulting 15px horizontal and 52px vertical outer margins are review-canvas space, not product UI or the app's 16px content inset. `360px` remains the minimum supported review width and the normalization baseline for Stitch/Figma handoff. This fixed size applies only to review captures: production Compose layouts remain responsive, respect platform safe areas, and scroll the content region between persistent app chrome. **[Approved 2026-08-04]**
- Root is fixed five-tab navigation: `News`, `Matches`, `MyPage`, `Events`, `About`; `MyPage` is the default destination. Every root has title + Search. Detail and Search are pushed screens without bottom navigation.
- Detail top bars use Back plus only feature-required actions: News/Event/Series Back-only, Match Back + bell only for Upcoming/Postponed, Team/Player Back + star. Search is full-screen push, explicit-submit-only, maximum 30 characters; blank or symbol-only input cannot submit.
- News rows are thumbnail/card-free divider rows whose full bounds open News Detail. Search rows are image-free divided rows with text type labels and full-row navigation.
- Match Card visual anatomy is shared, but navigation is contextual: Matches Root may expose stable-ID Team/Event nested targets; Event/Team/Player/MyPage Next Matches cards open Match Detail only. Match Detail retains Maps then Head to Head; existing server `pastMatches` may remain in the response but is UI-hidden and belongs to follow-up contract cleanup.
- MyPage provides no Match favorite feature or group and displays no Match bell. Team favorites are the primary personalization; Player favorites are secondary. The planned `Next Matches` contract aggregates upcoming Matches from favorite Team IDs, dedupes by stable Match ID, sorts by scheduled time, distinguishes no teams/no matches/section failure, reuses Compact Match Card, and opens Match Detail only. No endpoint or DTO is invented here.
- Event Detail uses `Matches` (default), `News`, `Stats` tabs with per-tab state/scroll preservation. Stats keeps a pinned identity column, horizontally scrolls metrics in `Rounds`/`Rating`/`ACS`/`K-D`/`ADR`/`KAST` order, and only the Player identity cell opens Player Detail. Team/Player Detail use section Empty/missing markers with no generic Partial screen; Series uses full-content loading/error with no generic Partial. About is flat information sections with exact source URL; source-link failure is Snackbar + Copy Link.

## Accessibility and interaction acceptance

- All interactive controls have a reachable 48dp target, programmatic name, visible focus order, and safe-area-aware placement. Modal dialogs trap focus, restore focus on dismissal, and expose explicit actions.
- Status and Live are written labels, not color-only signals; Live contrast and all normal text/UI boundaries target WCAG 2.2 AA intent (4.5:1 normal text, 3:1 large text/UI boundary where applicable).
- Tables expose row identity with every metric label; horizontally scrollable metric columns do not remove the pinned identity. Search focus order is Back → field → clear → results.
- Loading, empty, initial error, pagination error, and mutation states preserve the correct layout/recovery boundary. Snackbar recovery actions remain reachable and are announced; mutation spinners disable the covered screen actions.

## 1. Visual theme and atmosphere

**Light, compact, editorial match coverage.** The UI is a calm white information canvas with a single hot-red signal for live state, selection, scores, and urgent actions. It feels structured and fast rather than decorative: thin outlines, small type, stable three-column match rows, dense but breathable lists, and a five-item labelled bottom navigation.

- Put status and time before identity, identity before score/result, and event context last in dense match content. **[Observed]** in Matches Root Corrected and Match Detail Refined.
- Keep the chrome quiet: white app bars, centered or start-aligned concise titles, 24dp line icons, and low-contrast dividers. **[Observed]**
- Use red as a signal, not a page background. A live label, selected tab/nav item, score, focus outline, or primary action may be red; ordinary text and cards must remain neutral. **[Observed]**
- Do not add glow, gradients, hero treatment, heavy cards, desktop-dashboard density, or decorative esports graphics. **[Inferred]** from the repeated flat mobile layouts.

## 2. Semantic color tokens and roles

Use semantic token names in Compose; feature code must not contain raw hex values. Values are Light-only for Step 1. Dark mode is not designed or an acceptance target yet.

| Token | Value | Role and evidence |
| --- | --- | --- |
| `AccentLive` | `#FF4654` | Bright Valorant signal for live labels, selected navigation, scores, focus outline, and toggles. **[Observed]** project custom color and historical exports. |
| `AccentLiveContainer` | `#FF4654` at 10% alpha | Quiet live/selected background; keep the full-color foreground separate. **[Observed]** Matches Root Corrected. |
| `ActionPrimary` | `#D32F2F` | Solid primary and destructive action surface. **[Observed]** Design System Reference. |
| `OnActionPrimary` | `#FFFFFF` | White label/icon on `ActionPrimary`; `#D32F2F` supplies the accessible action-red, not `AccentLive`. **[Observed]** reference; **[Normalized]** for contrast. |
| `ActionPrimaryPressed` | `#B71C1C` | Pressed primary/destructive action and `OnAccentContainer` text. **[Observed]** reference. |
| `AccentLiveOn` | `#111823` | Dark label/icon on a full `AccentLive` surface when normal-size text is required. **[Inferred]** accessibility correction; do not use white normal-size text on `#FF4654`. |
| `Surface` | `#FFFFFF` | App canvas, app bar, cards, and input field. **[Observed]** throughout. |
| `SurfaceSubtle` | `#F5F5F5` | Recessed group, segmented-control track, and quiet container. **[Observed]** reference; **[Normalized]** from `#F4F4F5`/`#F5F5F5` exports. |
| `SurfaceSelected` | `#FFEBEE` | Light live/status container. **[Observed]** reference. |
| `TextPrimary` | `#18181B` | Primary readable content, headings, and scores. **[Observed]** corrected Matches source; **[Normalized]** over nearby `#111823`/`#212121` exports. |
| `TextBrand` | `#111823` | Brand-dark text/icon on accent or identity treatment. **[Observed]** Search and Match Detail sources. |
| `TextSecondary` | `#71717A` | Metadata, inactive navigation, and quiet labels. **[Observed]** corrected Matches source; **[Normalized]** over nearby `#757575`. |
| `Outline` | `#E4E4E7` | 1dp list/card/input divider and inactive border. **[Observed]** corrected Matches source; **[Normalized]** over nearby reference outlines. |
| `FocusOutline` | `#FF4654` | Visible keyboard/accessibility focus ring, 2dp outside the component. **[Observed]** search border; **[Inferred]** focus treatment. |
| `Scrim` | `#000000` at 32% alpha | Modal/sheet backdrop only. **[Inferred]**; no heavy shadows should substitute for it. |

### Status colors

Status always needs a written label; color is a supporting cue. These pairs are **[Observed]** in Design System Reference. A future screenshot pass must verify the exact Korean-label contrast at its final rendered size; where it is insufficient, retain the background and use `TextPrimary` rather than weakening the label.

| Status | Container | Label/icon | Usage |
| --- | --- | --- | --- |
| Live | `#FFEBEE` | `#D32F2F` | Live match and active score context. |
| Upcoming | `#E3F2FD` | `#1976D2` | Scheduled, not begun. |
| Completed | `#E8F5E9` | `#388E3C` | Final result. |
| Postponed | `#FFF3E0` | `#F57C00` | Delayed; label must remain explicit. |
| Cancelled | `#F5F5F5` | `#757575` | Cancelled or unavailable schedule. |
| Partial/Stale | `SurfaceSubtle` | `TextSecondary` | Data-quality state; pair with an explanatory label. |
| Unavailable | `Surface` + dashed `Outline` | `TextSecondary` | No usable source data; never resemble an empty result. |

## 3. Typography rules

The exported product screens predominantly load **Public Sans** (including the component reference, corrected Matches, Search, and Match Detail); the older project design asset and corrected About export use Inter. The typography hierarchy—not a bundled web font—is the stable visual rule.

**Step 1 Compose decision [Inferred]:** use `FontFamily.SansSerif`/the platform system sans-serif as the shared implementation default. Do not bundle Public Sans or Inter until a cross-platform Korean-capable font and licensing decision is approved. The Korean UI copy and official Latin names must share the same hierarchy, and numeric scores/timers should use tabular figures when the selected platform font supports them.

| Role | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| `Display` | 28sp / 34sp | 600 | Major score or one primary page result. **[Observed]** reference; line height **[Normalized]** from project asset. |
| `PageTitle` | 22sp / 28sp | 600 | Page title when not using compact app-bar title. **[Observed]** reference. |
| `SectionTitle` | 16sp / 24sp | 600 | Section heading, card heading. **[Observed]** reference. |
| `Body` | 14sp / 20sp | 400 | Primary explanatory copy. **[Observed]** reference; line height **[Normalized]**. |
| `BodyStrong` | 14sp / 20sp | 600 | Team name, result name, interactive row title. **[Observed]** reference. |
| `Label` | 13sp / 16sp | 500 | Compact status and control label. **[Observed]** reference. |
| `LabelSmall` | 12sp / 16sp | 400 | Time, category, secondary metadata. **[Observed]** reference. |
| `NavLabel` | 10sp / 12sp | 500; 700 selected | Always-visible bottom-navigation label. **[Observed]** corrected Matches source. |

- Use `Display` sparingly; a data row should normally use `BodyStrong` plus `LabelSmall`, not display type. **[Observed]**
- Keep live scores and changing timers width-stable; use tabular numbers if available. **[Inferred]**
- Support system font scale and Korean/Latin text growth. Never rely on fixed row height where text can clip; compact metadata may wrap or a row may grow. **[Inferred]**
- Use sentence case/Korean UI labels; preserve official team, player, tournament, map, and region names. **[Observed]** Korean screens; **[Inferred]** content policy.

## 4. Geometry, elevation, spacing, and layout

### Shape and depth

- Default component corner: **8dp** (`ROUND_EIGHT`). **[Observed]** project metadata and corrected rows.
- Cards and result rows: 8dp; match cards may use 12dp when their grouped three-column shape needs more separation. **[Observed]** `rounded-lg`/`rounded-xl`; **[Normalized]** into the 8dp base plus an optional 12dp card token.
- Status chips and icon buttons: fully pill/circular. **[Observed]**
- Identity image: 8dp square for event/series artwork; circle for team/player avatar. **[Observed]** Search source.
- Surfaces are flat. Use 1dp outlines and tonal layers before elevation. A very soft shadow is allowed only for a selected segment, fixed bottom bar, dialog, or sheet; it must not become card decoration. **[Observed]** corrected Matches; **[Inferred]** Compose elevation limit.

### Spacing and responsive rhythm

| Token | Value | Role |
| --- | --- | --- |
| `Space1` | 4dp | Icon/text micro-gap, compact internal padding. |
| `Space2` | 8dp | Related controls or row detail. |
| `Space3` | 12dp | Standard card/row internal padding. |
| `Space4` | 16dp | Phone horizontal inset and major control gap. |
| `Space6` | 24dp | Adjacent content groups. |
| `Space8` | 32dp | Unrelated sections. |

The 4dp grid and 16dp horizontal inset are **[Observed]** in the project design asset and recurring source layouts. Use a single fluid column at compact width. Keep the match row’s status/time rail, team/score middle, and event rail visually stable; detail pages may use full-width white cards separated by `SurfaceSubtle` page sections. **[Observed]**

- Respect safe areas for the 56dp app bar and fixed 5-destination bottom navigation. Bottom labels are always visible. **[Observed]**
- Tablet/foldable expansion may add gutters or a secondary metadata column only when reading order remains status → identity → result → context. It must not hide essential actions behind hover. **[Inferred]**
- Loading skeletons preserve the final row’s status, identity, score, and event slots; do not shift the app bar or bottom navigation. **[Observed]** reference; **[Inferred]** implementation detail.

## 5. Step 1 component contracts

### 5.1 Typography

**Purpose:** expose the seven roles in the typography table as a small shared style contract, not as a wrapper composable around every `Text`.

- **Variants/sizes:** `Display`, `PageTitle`, `SectionTitle`, `Body`, `BodyStrong`, `Label`, `LabelSmall`, and `NavLabel`; exact sizes are in section 3. **[Observed]**
- **States:** no component state matrix. Disabled, error, and selected are expressed by the semantic color supplied by the parent component, never by a different text size or font family. **[Inferred]**
- **Content rules:** use `TextPrimary` for readable primary content, `TextSecondary` for metadata, and a status/action color only when text is adjacent to its explicit state label. Do not use ellipsis for primary match identity or a live score without a feature-defined recovery path. **[Inferred]**
- **Semantics:** plain text remains plain text. Headings use heading semantics where the Compose target supports them; a number-only score needs an adjacent combined description such as “Sentinels 2, DRX 1.” **[Inferred]**
- **Compose mapping:** place type scale in `ui/theme/Typography.kt` when theme work begins; use `sp`, `FontFamily.SansSerif`, explicit line heights, and no web-font dependency. **[Inferred]**

### 5.2 Button

**Purpose:** provide a concise explicit action without changing the product’s flat, information-first tone.

| Variant | Visual contract | Content |
| --- | --- | --- |
| `Primary` | `ActionPrimary` fill, `OnActionPrimary`, 8dp corner. | One concise imperative Korean label; optional leading icon. |
| `Secondary` | `Surface` fill, 1dp `Outline`, `ActionPrimary` label/icon. | Same label rules; use where a primary action already exists. |
| `Text` | Transparent, `ActionPrimary` label/icon; pressed tonal fill. | Short secondary action, never the only destructive confirmation. |
| `Icon` | Circular visual surface with a 24dp familiar icon. | Requires an accessible label; no visible text required. |
| `Destructive` | Same as `Primary` unless a future feature needs a separately reviewed destructive token. | Explicit destructive Korean verb; never rely on red alone. |

**Sizes [Normalized/Inferred]:** standard visual height 40dp inside a **48dp minimum target**; compact visual height 32dp is allowed only inside a 48dp target and never for the sole action in a screen or dialog. Horizontal padding is 16dp standard / 12dp compact, with 8dp icon-label gap. Icons are 20dp in text buttons and 24dp in icon buttons. Do not create icon-only `Primary` actions without a label or accessible name.

| State | Primary / secondary / text / icon behavior |
| --- | --- |
| Enabled | Full semantic foreground/background; pointer/press affordance. |
| Focus | 2dp `FocusOutline` outside the visual bounds; never focus by color shift alone. |
| Pressed | `ActionPrimaryPressed` for primary/destructive; `SurfaceSubtle` for secondary/text/icon. |
| Disabled | 50% visual opacity only when the reason is obvious; otherwise keep the control enabled and explain the constraint nearby. No interaction. **[Observed]** reference opacity; explanation **[Inferred]**. |
| Loading | Preserve width, disable repeat activation, replace leading icon or label slot with a progress indicator, and expose “loading” in semantics. **[Inferred]** |
| Error | Not a button visual state. Put validation/retry context next to the action. |
| Selected | Not applicable; use a segmented control or navigation item, not a selected button. |

**Semantics and accessibility [Inferred]:** use `Button`/clickable button role, a meaningful label, disabled semantics, and a 48×48dp minimum target. Maintain at least 4.5:1 contrast for normal text and 3:1 for icon/large text; `#FFFFFF` is permitted on `ActionPrimary #D32F2F`, but not normal-size text on `AccentLive #FF4654`. Respect keyboard focus and minimum font scale without clipping.

**Compose mapping [Inferred]:** start from Material 3 behavior but provide project `ButtonColors`, border, shape, `minimumInteractiveComponentSize`, and a shared state API in `commonMain`. Do not use Android-only ripple, font, or view APIs. Keep the component feature-local until two real features use the exact same contract, per [UI-layer rules](docs/app-arch/ui-layer.md).

### 5.3 Search field

**Purpose:** a prominent query input with leading search icon, optional clear action, and separate result/empty regions. **[Observed]** Search Root.

- **Variants:** `Standard` (56dp visual height, pill shape, filled `SurfaceSubtle`, 2dp accent focus border; **[Observed]** Design System Reference) and `Compact` (40dp visual height, 8dp corner, outlined `Surface`; **[Observed]** Search Root). The parent must provide a 48dp reachable clear/back action even for Compact. **[Inferred]**
- **Content/icon rules:** leading 20–24dp search icon is always visible; placeholder is “팀, 선수, 대회 검색…” or feature-equivalent Korean copy; clear icon appears only for non-empty editable input and has the accessible name “검색어 지우기.” Use a trailing spinner only while an explicitly submitted query is loading. **[Observed]** icon and placeholder; spinner rule **[Inferred]**.
- **Result rule:** initial state uses a quiet panel with a search/explore icon and instruction; results are image-free divided rows with a persistent text type label and available metadata. The full row is the Detail target. **[Approved wireframe contract]**

| State | Visual and behavioral contract |
| --- | --- |
| Enabled / empty | `Surface` or `SurfaceSubtle` according to size; outline; placeholder and search icon. |
| Enabled / value | Same surface; visible clear icon; query remains selectable/editable. |
| Focus | 2dp `FocusOutline`; do not remove the label/placeholder context. **[Observed]** Search sources. |
| Error | 2dp error outline plus short text error below the field; do not announce color alone. **[Inferred]** |
| Disabled | 50% opacity, no clear action, disabled field semantics; retain entered value if useful. **[Inferred]** |
| Loading | Keep query and results layout stable; show progress in trailing slot and set busy semantics. **[Inferred]** |
| Selected | Text selection is platform-owned; the field itself has no selected variant. |

**Semantics and accessibility [Inferred]:** use an editable-text/text-field role with programmatic label, current value, hint, error, and busy state. The clear affordance is a separate labelled button. Image-free Search results are individual buttons/links with a combined name (type, name, optional region); do not add decorative logo semantics. Meet 4.5:1 text contrast and preserve focus order: back, field, clear, then results.

**Compose mapping [Inferred]:** wrap Material 3 `TextField`/`OutlinedTextField` behavior in `commonMain` only after two features need it. Supply custom `TextFieldColors`, 8dp/pill shapes, leading/trailing slots, `singleLine`, IME search action, and stable state hoisting (`value`, `onValueChange`, `isLoading`, `error`). Do not couple search navigation or server error types to the field.

### 5.4 Status chip

**Purpose:** a compact, non-actionable textual state marker for a match or data quality. It complements status/time placement; it never replaces it. **[Observed]** Design System Reference and match screens.

- **Variants:** `Live`, `Upcoming`, `Completed`, `Postponed`, `Cancelled`, `Partial`, `Stale`, `Unavailable`; use the table in section 2. **[Observed]**
- **Size:** 12sp/16sp medium label, 24dp visual height, 12dp horizontal padding, pill shape, 1dp color-tinted border. **[Observed]** reference source (`px-3 py-1`, 12px); dp normalization is **[Inferred]**.
- **Content/icon rules:** label is mandatory and short (“LIVE”, “예정”, “종료”, etc. after product glossary confirmation). An optional 12–16dp status icon may reinforce but cannot duplicate the full label. No avatars, scores, or decorative icons in a chip. **[Inferred]**

| State | Contract |
| --- | --- |
| Enabled | The specified label/container/foreground pair; chips are informational by default. |
| Focus | Not focusable when informational. If a feature makes it interactive, use a separate filter/tag contract with 48dp target and 2dp focus ring. **[Inferred]** |
| Error | Use `Unavailable` for unavailable source data, or put a separate error message/action beside the chip; no generic red “error chip.” **[Inferred]** |
| Disabled | Not applicable to an informational chip. |
| Loading | Replace with a layout-matched neutral skeleton; do not show an invented status. **[Inferred]** |
| Selected | Not applicable. A selected filter is a segmented control, not `StatusChip`. |

**Semantics and accessibility [Inferred]:** expose the chip’s label in the parent match description in reading order (for example, “Live, Sentinels versus LOUD”). A standalone informational chip may use plain text semantics. If it becomes interactive, give it button semantics, visible focus, 48dp target, and selected state only as part of a separately designed filter. Keep a written data-quality explanation near `Partial`, `Stale`, or `Unavailable`.

**Compose mapping [Inferred]:** model `StatusChip` as a sealed status value plus localized display label owned by the UI layer; map its colors in theme tokens. It must not expose raw server error codes, exceptions, selectors, URLs, or parser details. Keep it feature-local until the same display/semantics contract is reused by two features.

### 5.5 Divider full-row

**Purpose:** present News and Search results as flat, scan-friendly navigation rows without card or thumbnail decoration.

- Use a 1dp `Outline` divider between rows, with title/name as the strongest text and author/time or type metadata as secondary text.
- The entire bounded row is one target with a minimum 48dp reachable area; do not create a title-only nested target.
- Search rows are image-free and carry a visible text type label (`Series`, `Event`, `Team`, or `Player`). There is no logo or decorative image whose semantics need announcing.
- Supported internal links use link semantics and a persistent cue beyond color; unsupported or external links remain non-routable.

### 5.6 Compact Match Card

**Purpose:** share the match visual anatomy across roots and detail contexts while keeping navigation capability context-aware.

- Stable scan order is status/time rail → two Team identities → score/result → Event or stage context. Live always includes a written `LIVE` label.
- Use a low-emphasis 1dp outline and compact spacing; avoid decorative elevation. Team logo/image failure uses a same-size neutral placeholder and preserves the adjacent name.
- The non-link surface opens Match Detail. Only Matches Root may expose nested Team/Event targets, and only when stable IDs are present; Event, Team, Player, and MyPage `Next Matches` cards open Match Detail only. Event context is `stage` when the parent Event is already visible.
- Nested targets have distinct semantics, do not overlap the card target, and remain reachable at 48dp. Team identity is not a target when the current response has no Team ID.

## 6. Navigation, feedback, and accessibility rules

- **Navigation:** the observed mobile shell is News, Matches, MyPage, Events, About; each item has icon and always-visible label. Selection uses red icon/text with a subtle red container. Search is reached from app-bar action and returns to the prior destination. **[Observed]** screens; navigation implementation remains governed by [app runtime](docs/app-arch/app-runtime.md).
- **Detail bars:** News/Event/Series use Back-only; Match uses Back plus a bell only for Upcoming/Postponed; Team/Player use Back plus a favorite star. Detail screens and Search are pushed destinations without bottom navigation.
- **Favorite mutation recovery:** Team/Player Add failure restores star OFF; Remove failure keeps star ON. Both expose an actionable Retry Snackbar and never block the entire screen. Match notification mutation uses the separate bell contract and modal blocking spinner described below.
- **Mutation feedback:** Match notification subscribe/unsubscribe shows a modal scrim and centered spinner while disabling all covered-screen actions; subscribe failure keeps the bell OFF, unsubscribe failure keeps it ON, and each provides an announced Snackbar Retry action.
- **Loading:** preserve app chrome and final content geometry with a spinner or skeleton. Do not replace an entire populated screen with a spinner for a local refresh. **[Observed]** matches footer/reference skeleton; **[Inferred]** refresh rule.
- **Empty and error:** distinguish no results, unavailable data, and load failure. Use concise Korean recovery copy and an explicit retry only when retry is meaningful. Do not reveal HTTP status, exception text, upstream URLs, selectors, or raw HTML. **[Inferred]** from the project/server boundary.
- **Interaction:** visible focus follows visual reading order; hover may add feedback on pointer targets but never reveals an essential action. Pressed feedback must be short and must not delay live information. **[Inferred]**
- **Accessibility baseline:** target WCAG 2.2 AA intent: 4.5:1 normal text, 3:1 large text/UI boundaries where applicable, 48dp minimum interactive target, no color-only state, dynamic type/font-scale support, safe-area support, and reduced motion. The visual chip may be smaller only while it is non-interactive. **[Inferred]**
- **Image semantics:** team/event/player artwork supports identity but does not replace the adjacent name. Give meaningful standalone imagery a description; mark repeated logos and decorative thumbnails as non-semantic. **[Observed]** identity rows; **[Inferred]** accessibility behavior.

## 7. Implementation handoff and non-goals

1. [x] Implement `ui/theme` tokens (`Theme.kt`, `Colors.kt`, `Typography.kt`, `Dimensions.kt`) in `commonMain` with the semantic names above.
2. [x] Implement the Step 1 typography, button, search-field, and status-chip contracts in `commonMain`; feature code adopts them only when the shared behavior matches its requirements, per [the UI layer guide](docs/app-arch/ui-layer.md).
3. [ ] On physical Android and iOS devices, capture focused state/screenshot coverage for default, focus, disabled, error, and loading states and verify Korean font fallback, font-scale growth, and TalkBack/VoiceOver labels.

Non-goals for Step 1: a dark theme, a bundled font, generic component-library expansion, a custom animation system, Android/iOS-divergent behavior, or implementation of navigation/features not already planned. Any new color, new interactive chip/filter behavior, or destructive-action treatment requires an update to this file and a visual/accessibility review.
