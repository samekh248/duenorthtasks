---

description: "Task list for Due North Tasks v1"
---

# Tasks: Due North Tasks v1

**Input**: Design documents from `/specs/001-metro-todo-app/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: Included, because constitution Principle IV requires contract, sync and screenshot tests.

**Organization**: Grouped by user story so each story can be built and tried on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1..US5 from spec.md

## Dependency map

```mermaid
flowchart LR
    P1[Phase 1<br/>Setup] --> P2[Phase 2<br/>Foundation:<br/>design system,<br/>Room, provider API]
    P2 --> US1[US1 home panorama<br/>offline]
    US1 --> US2[US2 Google sync<br/>+ sync engine]
    US2 --> US3[US3 Microsoft sync]
    US3 --> US4[US4 Switch provider]
    US1 --> US5[US5 Theme + accent]
    US4 --> P8[Polish + release]
    US5 --> P8
```

---

## Phase 1: Setup (M0)

**Purpose**: An empty app that builds in CI.

- [ ] T001 Create Gradle multi-module project per plan.md: `settings.gradle.kts`, `app`, `core:design`, `core:data`, `core:sync`, `provider:api`, `provider:google`, `provider:microsoft`, `provider:fake`
- [ ] T002 Add version catalog `gradle/libs.versions.toml` (Compose BOM, Hilt, Room, WorkManager, Retrofit, OkHttp, kotlinx.serialization, MSAL, Google Identity, Credential Manager, JUnit 5, Turbine, Robolectric, Roborazzi)
- [ ] T003 [P] Configure ktlint, Android Lint baseline and a module-dependency rule that fails if `app` or `core:*` depends on `provider:google` / `provider:microsoft` types (only DI wiring in `app/di/ProviderModule.kt` may)
- [ ] T004 [P] GitHub Actions workflow `.github/workflows/ci.yml`: build, unit tests, screenshot verify, lint
- [ ] T005 [P] `local.properties` loading for client IDs into `BuildConfig`, with documented sample in quickstart.md

**Checkpoint**: CI green on an empty black activity.

---

## Phase 2: Foundational (M1)

**Purpose**: Metro design system, local database, provider seam. Blocks every story.

### Metro design system (`core:design`)

- [ ] T006 Bundle Selawik fonts in `core/design/src/main/res/font/` and define type ramp `MetroTypography.kt` (research R2)
- [ ] T007 [P] `MetroColors.kt`: light (default) and dark palettes, accents with a light and a dark value each, magenta default (research R5); `MetroTheme` composable with `LocalMetroColors`, `LocalAccent`
- [ ] T008 [P] Motion: `motion/Turnstile.kt`, `motion/Tilt.kt` (Modifier.metroTilt), `motion/SlideInStagger.kt`, `motion/Continuum.kt`; all honor animator duration scale
- [ ] T009 `components/MetroPanorama.kt` (wide snapping scroller, 310dp sections that peek, title layer with ~1/3 speed parallax) and `components/MetroPivot.kt` for settings
- [ ] T010 [P] `components/MetroAppBar.kt` (round outlined icon buttons, `•••` expand with labels + menu)
- [ ] T011 [P] `components/MetroCheckBox.kt`, `MetroToggle.kt`, `MetroTextField.kt`, `MetroListItem.kt`
- [ ] T012 [P] `components/MetroProgressDots.kt`, `MetroDialog.kt`, `MetroContextMenu.kt`
- [ ] T013 [P] `components/MetroDatePicker.kt` (looping columns), `MetroAccentGrid.kt`, `MetroListTile.kt` (accent square + count), `MetroRadio.kt`
- [ ] T014 Component gallery screen `app/src/debug/.../GalleryScreen.kt`
- [ ] T015 Roborazzi screenshot tests for every component, light + dark, in `core/design/src/test/`

### Local data (`core:data`)

- [ ] T016 Room entities and DAOs per data-model.md: `AccountEntity`, `TaskListEntity`, `TaskEntity`, `StepEntity`, `PendingOperationEntity`, `SyncLogEntity` with cascading FKs from Account
- [ ] T017 `TaskRepository` with transactional write + outbox enqueue, outbox coalescing rules, `Flow` reads
- [ ] T018 [P] Repository tests: coalescing, create-then-delete cancel, cascade on account delete

### Provider seam (`provider:api`, `provider:fake`)

- [ ] T019 `TaskProvider`, `ProviderCapabilities`, models, `TaskPatch`, `ProviderError` per contracts/task-provider.md
- [ ] T020 Abstract `TaskProviderContractTest` in `provider/api/src/testFixtures/`
- [ ] T021 `FakeProvider` (in-memory, injectable failures and latency) passing the contract test
- [ ] T022 Hilt `ProviderRegistry` that returns the single provider for the `Account` row

**Checkpoint**: Gallery shows every Metro component; screenshot and contract tests pass.

---

## Phase 3: User Story 1 - Manage tasks in a Metro interface (P1) 🎯 MVP (M2)

**Goal**: A full offline todo app on the debug demo account.

**Independent Test**: spec.md US1; quickstart V1, V2, V8.

- [ ] T023 [P] [US1] Compose UI test: swipe the panorama, add, complete, edit, delete a task in `app/src/androidTest/.../HomeFlowTest.kt`
- [ ] T024 [US1] Navigation graph with turnstile transitions in `app/.../nav/NavGraph.kt`
- [ ] T025 [US1] `HomeViewModel` + `HomeScreen` panorama: "today" (overdue first in red, today, tomorrow across all lists), "lists" (tiles with counts and next task, new list row), "done" (recent completions); pull-to-refresh hook
- [ ] T026 [US1] `ListViewModel` + `ListScreen`: one list's tasks, collapsible completed group, sort
- [ ] T027 [US1] "add a task" box at the top of today: enter adds (due today, default list); "add details" link appears once typing and expands the box in place with details, due and list chips, "hide details" / "add", and the provider hint; app bar `+` focuses the box
- [ ] T028 [US1] `TaskDetailViewModel` + `TaskDetailScreen` with continuum entry: "DUE NORTH · LIST" header, 38sp title, accent due line, full details with tappable links, steps, due and list pickers; app bar mark done / edit / delete
- [ ] T029 [P] [US1] Task row details preview: first two lines of details, clamped, grey 14sp, between title and caption
- [ ] T030 [P] [US1] Add, rename, delete lists (from the "lists" section and the list page menu)
- [ ] T031 [P] [US1] `SearchScreen`: search titles and details across lists (FR-015), with a Room FTS table
- [ ] T032 [P] [US1] Long-press context menu (edit, delete, move to)
- [ ] T033 [US1] Debug-only "demo account" option on the sync account screen, backed by `FakeProvider` with sample data

**Checkpoint**: US1 acceptance scenarios pass in airplane mode.

---

## Phase 4: User Story 2 - Sync with Google Tasks (P1) (M3)

**Goal**: Real Google Tasks, both ways, plus the sync engine all providers share.

**Independent Test**: spec.md US2; quickstart V3, V4, V5, V9.

### Sync engine (`core:sync`)

- [ ] T034 [US2] `SyncEngine`: push outbox in `seq` order, then pull per list with cursor; map `ProviderError` to actions (contract table)
- [ ] T035 [US2] Conflict resolver (remote wins unless newer pending local edit; loser to `SyncLogEntity`)
- [ ] T036 [US2] `SyncWorker` + `SyncScheduler` (unique work, network constraint, debounce 5s after edits, on foreground, periodic at the user's "sync every" interval, default 15 min, Wi-Fi only if set)
- [ ] T037 [P] [US2] Sync engine tests against `FakeProvider`: offline queue, conflict both directions, cursor expired, auth required, recovered-list edge case
- [ ] T038 [P] [US2] Soak test `SyncSoakTest.kt`: 200 random ops with random failures, zero duplicates/losses (SC-004)

### Google provider (`provider:google`)

- [ ] T039 [P] [US2] Retrofit `GoogleTasksApi` + DTOs (research R6)
- [ ] T040 [US2] `GoogleAuth`: Credential Manager sign-in + `AuthorizationClient` for the `tasks` scope, silent token refresh
- [ ] T041 [US2] `GoogleTasksProvider` mapping per data-model.md (subtasks <-> steps, `updatedMin` cursor, `move` for order)
- [ ] T042 [P] [US2] MockWebServer fixtures and `GoogleTasksProviderContractTest`
- [ ] T043 [US2] Sync account screen (radio choice, "signed in as", sync interval, Wi-Fi only toggle) + first-sync progress

**Checkpoint**: Daily-drivable with a Google account.

---

## Phase 5: User Story 3 - Sync with Microsoft To Do (P2) (M4)

**Independent Test**: spec.md US3; quickstart V6.

- [ ] T044 [P] [US3] Retrofit `GraphTodoApi` + DTOs including `$batch` (research R7)
- [ ] T045 [US3] `MicrosoftAuth` with MSAL single-account mode, `common` authority, `Tasks.ReadWrite`
- [ ] T046 [US3] `MicrosoftTodoProvider` mapping (checklistItems <-> steps, importance, deltaLink cursors, raw status preserved)
- [ ] T047 [P] [US3] MockWebServer fixtures and `MicrosoftTodoProviderContractTest`
- [ ] T048 [US3] Importance star in task rows and detail, shown only when `capabilities.importance`

**Checkpoint**: Daily-drivable with a Microsoft account.

---

## Phase 6: User Story 4 - Switch provider, never both (P2) (M5)

**Independent Test**: spec.md US4; quickstart V7.

- [ ] T049 [P] [US4] Test: switch with pending ops shows warning; confirm clears all tables; no provider write calls recorded on the fake
- [ ] T050 [US4] `AccountManager.switchProvider()`: cancel unique sync work, sign out, delete Account row, return to "choose a service"
- [ ] T051 [US4] Picking the other service on the sync account screen: Metro confirm dialog with the "your tasks stay in that account" note and the unsynced-changes warning; sign out button
- [ ] T052 [P] [US4] `SyncLogScreen`

---

## Phase 7: User Story 5 - Theme and accent (P3) (M6)

**Independent Test**: spec.md US5.

- [ ] T053 [P] [US5] `ThemePreferences` in DataStore (light default, magenta default)
- [ ] T054 [US5] Settings "theme" pivot item with light/dark toggle and `MetroAccentGrid`; live update without restart
- [ ] T055 [P] [US5] Screenshot tests for three accents in both themes

---

## Phase 8: Polish and release (M7)

- [ ] T056 [P] Accessibility pass: TalkBack labels on app bar buttons and checkboxes, 48dp targets, panorama sections announced as headings, contrast check for every accent's light and dark value
- [ ] T057 [P] Performance: baseline profile, 1,000-task list benchmark (SC-005)
- [ ] T058 [P] Metro launcher icon (flat white glyph on accent square) and splash
- [ ] T059 Privacy policy and Google OAuth verification submission for the `tasks` scope
- [ ] T060 Play Console internal testing track and release signing via CI secrets
- [ ] T061 Run every quickstart.md scenario and record results

---

## Parallel opportunities

- Within Phase 2, the design system (T006-T015), Room (T016-T018) and provider seam (T019-T022) are
  three independent tracks.
- US5 only needs US1, so it can be built alongside US2-US4.
- Within each provider phase, the API/DTO task and the fixture/contract test task run in parallel.

## Implementation strategy

1. Phases 1-2, then US1: you have a Metro todo app you can try offline (MVP demo).
2. US2: switch to using it daily with Google Tasks.
3. US3 + US4: Microsoft To Do and switching.
4. US5 + polish: ship to Play internal testing.
