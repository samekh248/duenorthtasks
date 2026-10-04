# Research: Due North Tasks v1

Phase 0 output for [plan.md](plan.md). Each entry is Decision / Rationale / Alternatives.

## R1. UI toolkit for Metro

- **Decision**: Jetpack Compose using `compose.foundation` and our own `design` module. No Material 3
  dependency at all; text is drawn with `BasicText` / `BasicTextField`.
- **Rationale**: Metro is the opposite of Material (no elevation, ripple, rounded shapes, FABs).
  Wrapping Material widgets means fighting every default. Compose foundation gives raw layout,
  gestures (`HorizontalPager`) and animation, which is all Metro needs.
- **Alternatives**: Android Views with custom styles (slower to build custom animation);
  Material 3 with heavy theming (still leaks ripple, shapes, insets behavior); Flutter (would leave
  the native Kotlin/Room/WorkManager stack and the MSAL/Google libraries are Android-native).

## R2. Typography

- **Decision**: Bundle **Selawik** (Microsoft's open-source, OFL-licensed metric-compatible
  fallback for Segoe UI) in Light, Semilight, Regular and Semibold. Type ramp in `design`:

  | Token | Use | Size / weight |
  |---|---|---|
  | `panoramaTitle` | "due north" across the home panorama | 118sp Light, tracking -4% |
  | `sectionHeader` | panorama sections ("today", "lists", "done") | 40sp Light, lowercase |
  | `pageTitle` | small app name above a page title ("DUE NORTH") | 13sp Semibold, uppercase, +6% tracking |
  | `header` | page titles ("sync account", list names) | 52sp Light, lowercase |
  | `listName` | list names next to their tiles | 24sp Light |
  | `subheader` | section titles, task titles in lists | 20sp Semilight |
  | `body` | notes, settings rows | 15sp Regular |
  | `caption` | due dates, secondary text | 12sp Regular, 60% opacity |

- **Rationale**: Segoe WP is not redistributable. Selawik matches Segoe UI metrics and is from
  Microsoft, so the shapes feel right. Sizes follow WP8.1's Store-app ramp converted to sp.
- **Alternatives**: Open Sans (close but rounder), Roboto Light (looks like Android, not Metro).

## R3. Metro component inventory

| Metro control | Our Compose component | Notes |
|---|---|---|
| Panorama (home) | `MetroPanorama` | one wide horizontal scroller; title layer moves at about 1/3 the speed of the sections (parallax); sections 310dp wide with 40dp gaps so the next one peeks; snaps per section |
| List tile | `MetroListTile` | 64dp flat accent square, open-task count bottom-right in white 22sp Light, grey square for undated lists |
| Pivot | `MetroPivot` | `HorizontalPager` + header row that scrolls with the pager, inactive headers at 40% opacity, next header bleeds off screen |
| Application Bar | `MetroAppBar` | 72dp tall, up to 4 round 48dp outlined icon buttons, `•••` expands to labels + menu items |
| CheckBox | `MetroCheckBox` | square, 2dp border, accent fill when checked |
| ToggleSwitch | `MetroToggle` | rectangular track + rectangular thumb |
| TextBox | `MetroTextField` | white/black filled rectangle when focused, no underline |
| ListView item | `MetroListItem` | left-aligned to 12dp gutter, tilt on press |
| Progress dots | `MetroProgressDots` | five accent dots sliding across the top edge (sync indicator) |
| Message dialog | `MetroDialog` | full-width band across the top, title + body + two flat buttons |
| Date picker | `MetroDatePicker` | WP8.1 looping day/month/year columns |
| Accent picker | `MetroAccentGrid` | grid of flat squares |
| Radio button | `MetroRadio` | 28dp circle, 2dp border, filled 12dp dot when selected (sync account page) |

## R4. Motion

- **Decision**: Implement four WP8.1 transitions in `design/motion`:
  turnstile (page rotates on the Y axis around its left edge, forward in / backward out),
  continuum (tapped item's title flies up into the next page's header),
  slide-in stagger (list items enter offset by ~30ms each),
  tilt (pressed item rotates toward the touch point by up to ~10 degrees).
- **Rationale**: Without motion, flat UI looks like a wireframe; the motion is what people remember.
- **Alternatives**: Plain crossfades (fail SC-006).
- **Accessibility**: All four read `Settings.Global.ANIMATOR_DURATION_SCALE`; at 0 they become
  instant.

## R5. Colors (Light Panorama)

Chosen design: **Light Panorama**, light theme by default, magenta accent.

| Token | Light (default) | Dark |
|---|---|---|
| background | `#FFFFFF` | `#000000` |
| foreground | `#111111` | `#FFFFFF` |
| secondary text | `#5C5C5C` | `#A6A6A6` |
| app bar | `#E5E5E5` | `#1F1F1F` |
| accent (magenta, default) | `#B0005E` | `#F0389A` |
| overdue | `#C40000` | `#FF6B6B` |

The WP8.1 magenta `#D80073` is too light for small text on white, so the light-theme accent is
darkened and the dark-theme accent brightened. Every other accent the user can pick gets the same
treatment: a light-theme and a dark-theme value, each checked for 4.5:1 caption contrast.

The WP8.1 accents the picker offers (base values):
lime `#A4C400`, green `#60A917`, emerald `#008A00`, teal `#00ABA9`, cyan `#1BA1E2`,
cobalt `#0050EF`, indigo `#6A00FF`, violet `#AA00FF`, pink `#F472D0`, magenta `#D80073` (default),
crimson `#A20025`, red `#E51400`, orange `#FA6800`, amber `#F0A30A`, yellow `#E3C800`,
brown `#825A2C`, olive `#6D8764`, steel `#647687`, mauve `#76608A`, taupe `#87794E`.

## R6. Google Tasks API

- **Decision**: REST via Retrofit against `https://tasks.googleapis.com/tasks/v1/`.
  Lists: `users/@me/lists`. Tasks: `lists/{id}/tasks` with `updatedMin`, `showDeleted=true`,
  `showHidden=true`, `showCompleted=true`, `maxResults=100` and `pageToken` for incremental sync.
  Reorder with `tasks/{id}/move?parent=&previous=`. Edits use `PATCH`.
- **Model facts that shape the app**: status is `needsAction` / `completed`; `due` keeps only
  the date; subtasks are full tasks with a `parent` (one level); there is no importance,
  reminder or recurrence field in the API; lists have no incremental endpoint, so lists are
  fetched in full each sync (they are small).
- **Auth**: Credential Manager ("Sign in with Google") for identity, then Google Identity Services
  `AuthorizationClient.authorize()` requesting scope `https://www.googleapis.com/auth/tasks` for an
  access token. Calling `authorize()` again in the background returns a fresh token without UI
  while the grant stands. Requires an Android OAuth client (package name + SHA-1) in Google Cloud.
- **Alternatives**: The legacy `google-api-services-tasks` Java client (heavy, uses deprecated
  `GoogleAccountCredential`); `GoogleSignInClient` (deprecated).

## R7. Microsoft To Do API

- **Decision**: Microsoft Graph v1.0 via Retrofit: `me/todo/lists`, `me/todo/lists/delta`,
  `me/todo/lists/{id}/tasks/delta` for incremental sync (store the `@odata.deltaLink`),
  `.../tasks/{id}/checklistItems` for steps. Edits use `PATCH`. Use `$batch` (up to 20 requests)
  when pushing many queued changes.
- **Model facts**: status has five values (`notStarted`, `inProgress`, `completed`,
  `waitingOnOthers`, `deferred`); we map "done" to `completed` and anything else to "open",
  preserving the original non-completed value on un-complete. `importance` is `low|normal|high`
  (we toggle `normal` <-> `high`). `dueDateTime` is a date-time with time zone; we write midnight
  in the device zone. Steps (`checklistItem`) are title + `isChecked` only.
- **Auth**: MSAL for Android, single-account mode, authority `common` (personal and work/school
  accounts), scopes `Tasks.ReadWrite` and `offline_access`. `acquireTokenSilent` in the sync
  worker. Requires an Entra app registration with the Android redirect URI
  `msauth://<package>/<signature-hash>`.
- **Alternatives**: Graph Java SDK (large, brings its own HTTP stack; we need only ~10 calls).

## R8. Common task model across providers

- **Decision**: The local model is the intersection both services can store (list, title, notes,
  due date, completed, steps) plus `importance`, gated by a `ProviderCapabilities` flag. Remote
  fields we do not model are never sent back because every write is a field-level `PATCH`.
- **Rationale**: Satisfies FR-024 (no clobbering) and constitution Principle V (no feature neither
  provider can store) without lowest-common-denominator UX for To Do users.
- **Steps mapping**: Google child task (title + `status`) <-> local Step <-> To Do `checklistItem`.

## R9. Sync engine

- **Decision**: Outbox pattern. Every local edit writes the entity and a `PendingOperation` row in
  one Room transaction. A WorkManager `SyncWorker` (unique, network-constrained) runs
  push-then-pull: push outbox in order, then pull changes since the stored cursor (Google
  `updatedMin` timestamp, To Do `deltaLink`). Conflict rule: remote wins unless the local entity
  has a pending op whose `localUpdatedAt` is newer than the remote `updated`; the loser is written
  to `SyncLogEntry`.
- **Triggers**: app foreground, pull-to-refresh, after any local edit (debounced 5s), and a periodic
  job every 30 minutes on unmetered networks.
- **Idempotency**: creates carry a client-generated id; if a create's response is lost, the next
  pull matches the remote item by stored `remoteId` once known, or by (title, createdAt window)
  fallback for Google, which has no client-supplied id. To Do inserts are retried only after a
  delta pull checks for the item.
- **Alternatives**: Remote-only (fails offline requirement); CRDTs (overkill, providers are
  last-writer-wins anyway).

## R10. Testing

- **Decision**: JUnit 5 + Turbine for flows; Room in-memory DB; MockWebServer with recorded JSON
  fixtures for each provider; one shared `TaskProviderContractTest` run against Fake, Google and
  Microsoft implementations; Roborazzi (Robolectric) screenshot tests for each Metro component in
  dark and light; a sync soak test (SC-004) driven by the fake provider with random failures.
- **Alternatives**: Paparazzi (fine too, but Roborazzi also captures interaction states).

## R11. Architecture and modules

- **Decision**: Single-activity Compose app, MVVM with unidirectional state (`ViewModel` exposes
  `StateFlow<UiState>`), Hilt for DI, Navigation Compose with custom turnstile transitions.
  Modules: `app`, `core:design`, `core:data`, `core:sync`, `provider:api`, `provider:google`,
  `provider:microsoft`, `provider:fake`.
- **Rationale**: The provider modules are the seam the constitution requires (Principle II); the
  design module is built first and in isolation (Principle I).
