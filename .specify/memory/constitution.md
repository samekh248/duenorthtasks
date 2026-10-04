# Due North Tasks Constitution

## Core Principles

### I. Metro Is the Product (NON-NEGOTIABLE)

Every screen MUST follow the Windows Phone 8.1 Metro ("Modern UI") design language. It is the
reason this app exists, so it outranks Material defaults whenever the two disagree.

- Typography is the interface: Segoe-style light/semilight sans (Open Sans or Selawik as the
  licensed stand-in), oversized lowercase page titles, and a strict type ramp
  (defined in the plan's research notes, R2).
- Content over chrome: no drop shadows, no gradients, no rounded cards, no elevation, no
  Material ripple. Flat color fills only.
- The chosen look is **Light Panorama** (design C, picked 2026-10-04): the home screen is a
  Panorama hub with an oversized "due north" title that scrolls sideways more slowly than the
  sections under it ("today", "lists", "done"), like the WP8.1 Calendar and People hubs. The next
  section always peeks in from the right edge. Secondary pages use the Pivot where they need tabs.
  Every screen has a bottom Application Bar with round outlined icon buttons and an ellipsis
  (`•••`) that expands labels and a menu.
- Light (white background, near-black text) and dark (pure black) themes MUST both be
  supported. By default the app follows the phone's light/dark setting; the user can override it
  with a manual light or dark choice. One accent color drives highlights, list tiles and due captions: magenta by
  default, `#B0005E` on light and the brighter `#F0389A` on dark so text stays readable. Every
  list uses the app accent unless the user gives that list its own color. The
  user may pick another accent (the WP8.1 set plus light orange and coral); every accent has a
  light-theme and a dark-theme value.
- Motion is part of the language: turnstile page transitions, tilt-on-press, continuum
  (item flies into the next page), and slide-in list stagger. Animations MUST respect the
  system "remove animations" setting.
- Text and controls align to a 12dp left gutter and a 24dp grid; touch targets are at least
  48dp.

Rationale: a "Metro skin" over Material widgets would fail the brief. The design system is built
first, as its own module, and every feature consumes it.

### II. Fast and Fluid (NON-NEGOTIABLE)

UX performance is a top priority, equal to the Metro look. The app MUST feel instant, and sync
MUST never make it feel slow.

- Every tap gets visible feedback within 100 ms (tilt, state change, or the result itself). Task
  edits apply to the screen immediately (optimistic, from the local database) and never wait for
  the network.
- Animations and scrolling hold 60 fps, and 90/120 fps on high-refresh phones, with no dropped
  frames during transitions. Sync, database and network work never run on the main thread.
- Sync is invisible unless the user asks: it runs in the background, applies remote changes in
  small batches without moving what the user is looking at or touching, and shows only the Metro
  progress dots. A sync never blocks input, never shows a full-screen spinner, and never
  reorders a list under the user's finger.
- Loading is graceful: cached content shows at once on launch; anything still loading uses
  content-shaped placeholders that fade in, never blank screens or jumping layouts.
- Cold start to usable home screen is at most 1 second on a mid-range phone (Pixel 6a class).
- Performance budgets are tested: macrobenchmarks for startup, scrolling, transitions and
  scrolling during sync run in CI, and a change that breaks a budget is a failing build.

Rationale: Windows Phone was remembered for feeling fast on modest hardware. A Metro look that
stutters fails the brief as much as a Material look would.

### III. One Provider at a Time (NON-NEGOTIABLE)

The app syncs with exactly one backend: Google Tasks OR Microsoft To Do, never both.

- An account is connected to at most one provider. Connecting a second provider MUST first
  disconnect the first, after an explicit confirmation that names what happens to local data.
- Switching providers MUST NOT silently merge, duplicate, or delete remote data on either side.
- All provider-specific code lives behind a single `TaskProvider` interface. The UI, database and
  sync engine MUST NOT import Google or Microsoft SDK types.

Rationale: two-way sync with one remote is hard enough; two remotes means conflict resolution
across systems with different data models, which is out of scope by design.

### IV. Offline First, Remote Is the Source of Truth

- The UI reads only from the local Room database, so every action works with no network.
- Local changes are queued as pending operations and pushed by a background sync worker.
- When local and remote disagree after a sync, the remote copy wins unless the local change is
  newer and still pending. Conflicts MUST be logged, never silently dropped.

### V. Test the Seams

- The `TaskProvider` contract has one shared test suite that both provider implementations and a
  fake MUST pass.
- The sync engine is unit tested against the fake provider, including offline, conflict, and
  provider-switch scenarios.
- Each Metro component has a Compose screenshot test in dark and light themes.

### VI. Small and Simple

- One Android app module plus a small number of library modules (`design`, `data`, `sync`,
  `provider-google`, `provider-microsoft`). No module exists just for organization.
- No task data is built that neither provider can store (for example, tags or attachments in
  v1). Display preferences that live only on the phone, such as theme and per-list colors, are
  allowed and MUST NOT be sent to either provider.
- YAGNI: no tablet layouts, widgets or wear support until the phone app ships.

## Technical Constraints

- Language and UI: Kotlin, Jetpack Compose (custom Metro components, not Material 3 widgets).
- Min SDK 26, target the latest stable SDK.
- Persistence: Room. Background work: WorkManager. DI: Hilt. Networking: Retrofit + OkHttp +
  kotlinx.serialization.
- Auth: Google via Credential Manager + AuthorizationClient (scope `tasks`); Microsoft via MSAL
  for Android (scope `Tasks.ReadWrite`). Tokens are stored only in the platform-managed stores
  these libraries provide.
- Secrets (client IDs, `google-services.json`, keystores) MUST NOT be committed.

## Development Workflow

- Features follow Spec Kit: `/speckit-specify` then `/speckit-plan` then `/speckit-tasks` then
  `/speckit-implement`, one branch and one PR per feature.
- Every PR MUST pass build, lint (ktlint + Android Lint), unit tests, screenshot tests and the
  performance budgets (Principle II) in CI.
- Every UI PR includes before/after screenshots in dark theme.

## Governance

This constitution supersedes other practices in this repo. Amendments are made by PR that edits
this file, bumps the version (MAJOR for removing or redefining a principle, MINOR for adding one,
PATCH for wording) and states the reason. Plans and reviews MUST check the Constitution Check
gates in `plan.md` against the principles above; any violation is listed in that plan's
Complexity Tracking table with a justification.

**Version**: 1.3.0 | **Ratified**: 2026-10-04 | **Last Amended**: 2026-10-04
