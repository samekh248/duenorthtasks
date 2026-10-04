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

Chosen design: **Light Panorama**, magenta accent. The theme follows the phone's light/dark
setting by default (`isSystemInDarkTheme()`), with a manual override in settings.

| Token | Light | Dark |
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

### Accent choices

The user picks one accent at a time from 22 choices: the 20 WP8.1 accents plus **light orange**
and **coral**, which Dustin asked for. Each has a light-theme value (used on white) and a
dark-theme value (used on black). The ratios are text contrast against the background, and all
meet 4.5:1 for 13sp captions. Fills (list tiles, checked boxes, picker swatches) use the same
value, except that light orange and coral keep their bright color as a fill on white too, so they
still look light; there the darker value is used only for text. On a tile, the count is drawn in
white or black, whichever contrasts more with the tile.

| Accent | Light theme text | Dark theme | Source |
|---|---|---|---|
| magenta (default) | `#B0005E` (7.0:1) | `#F0389A` (5.7:1) | WP8.1 |
| light orange | `#A85400` (5.3:1), fill `#FFA552` | `#FFA552` (10.8:1) | added |
| coral | `#B8402A` (5.5:1), fill `#FF8A6E` | `#FF8A6E` (9.1:1) | added |
| lime | `#5A6E00` (5.7:1) | `#A4C400` (10.5:1) | WP8.1 |
| green | `#3C7A0E` (5.3:1) | `#60A917` (7.2:1) | WP8.1 |
| emerald | `#007A00` (5.5:1) | `#2DB52D` (7.8:1) | WP8.1 |
| teal | `#00787A` (5.3:1) | `#00ABA9` (7.4:1) | WP8.1 |
| cyan | `#0B6FA4` (5.5:1) | `#1BA1E2` (7.2:1) | WP8.1 |
| cobalt | `#0050EF` (6.2:1) | `#4D8BFF` (6.5:1) | WP8.1 |
| indigo | `#6A00FF` (6.9:1) | `#9A5CFF` (5.3:1) | WP8.1 |
| violet | `#8A00D4` (6.9:1) | `#C25CFF` (6.3:1) | WP8.1 |
| pink | `#B0308F` (5.7:1) | `#F472D0` (8.2:1) | WP8.1 |
| crimson | `#A20025` (8.2:1) | `#FF5C7A` (7.1:1) | WP8.1 |
| red | `#C41100` (6.1:1) | `#FF5C4D` (6.9:1) | WP8.1 |
| orange | `#B34A00` (5.4:1) | `#FA6800` (7.0:1) | WP8.1 |
| amber | `#8F5F00` (5.5:1) | `#F0A30A` (9.9:1) | WP8.1 |
| yellow | `#7A6A00` (5.4:1) | `#E3C800` (12.5:1) | WP8.1 |
| brown | `#825A2C` (6.1:1) | `#C8955A` (7.9:1) | WP8.1 |
| olive | `#566B4F` (5.8:1) | `#93AD89` (8.6:1) | WP8.1 |
| steel | `#576778` (5.8:1) | `#8EA2B8` (8.0:1) | WP8.1 |
| mauve | `#76608A` (5.5:1) | `#A891BE` (7.5:1) | WP8.1 |
| taupe | `#6E6240` (6.0:1) | `#B5A577` (8.6:1) | WP8.1 |

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
- **Rationale**: Satisfies FR-024 (no clobbering) and constitution Principle VI (no feature neither
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
- **Rationale**: The provider modules are the seam the constitution requires (Principle III); the
  design module is built first and in isolation (Principle I).

## R12. Performance (constitution Principle II)

- **Decision**: Build speed in from M0 rather than tuning it at the end.
  - **Startup**: Baseline Profiles generated by a `:benchmark` module; App Startup library with no
    eager SDK init (MSAL and Google Identity load on first sync, not at launch); first frame
    draws from Room with no network on the launch path.
  - **Main thread**: only Compose. Room, mapping and network run on `Dispatchers.IO` /
    `Default`; `StrictMode` in debug builds fails on main-thread disk or network access.
  - **Lists**: `LazyColumn` with stable keys and `contentType`, immutable UI models (`@Immutable`,
    `kotlinx.collections.immutable`), list diffing off the main thread, `animateItem()` for
    inserts and removals.
  - **Sync application**: remote changes are committed in batches of at most 50 rows per
    transaction, so Room's invalidation emits small updates; while the user is dragging or
    flinging a list, its updates are held and applied when the gesture ends.
  - **Animations**: turnstile, continuum, tilt and panorama parallax use `graphicsLayer`
    transforms only (no relayout per frame).
  - **Measurement**: Jetpack Macrobenchmark tests for cold/warm start, panorama swipe, list
    scroll, turnstile transition and "scroll while syncing 500 changes"; run in CI on a
    Gradle Managed Device and fail the build when a budget in the plan is exceeded. JankStats
    in debug builds logs dropped frames.
- **Rationale**: These are the usual causes of stutter in Compose apps (work on the main thread,
  unstable list items, large database invalidations, layout-driven animation). Testing budgets in
  CI keeps regressions from creeping in.
- **Alternatives**: Profiling only before release (regressions pile up unnoticed).

