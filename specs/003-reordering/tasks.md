---

description: "Task list for reordering tasks, lists and steps"
---

# Tasks: Reordering tasks, lists and steps

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md)

**Tests**: Included (constitution Principle V).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Foundation

- [x] R001 [P] `OrderKeys` in `core:data` (`between`, `timeKey`) with unit tests
- [x] R002 [P] `MetroIcon.Reorder` and `MetroIcon.Gripper` glyphs in `core:design`
- [x] R003 `MetroReorderList` in `core:design`: list-level drag (gripper on touch-down, row after 150 ms), lifted row (105%, 4dp accent edge), others at 45%, dashed slot, neighbor slide, edge auto-scroll, haptic tick, "move up"/"move down" accessibility actions, remove-animations support
- [x] R004 [P] Screenshot test of `MetroReorderList` mid-drag in light and dark

## Phase 2: US1 + US2 tasks (P1)

- [x] R005 `TaskRepository.moveTask(id, afterId, beforeId)`: key between neighbors, Google top = null, `MOVE` queued only when the service stores order
- [x] R006 Microsoft task keys: `createTask` and "move to list" put tasks first; `TaskOrder` comparator shared by the list page
- [x] R007 [P] `RemoteTask.createdAt` (Microsoft `createdDateTime`)
- [x] R008 Puller: keep local position when a `MOVE` is pending or the service has no order; key new Microsoft tasks (creation time on a first fetch, top otherwise); backfill keys for older Microsoft tasks
- [x] R009 Pusher: keep local position on create when a move is queued or the service has no order; `recreate` keeps position
- [x] R010 List page reorder mode: app bar "reorder", context menu "reorder" first, sort switches to "my order", compact rows, done button and back, footer for completed tasks
- [x] R011 Microsoft one-time "order stays on this phone" dialog
- [x] R012 Tests: repository move (Google and Microsoft), sync push of task `MOVE` against the fake, Microsoft pull keeps order

## Phase 3: US3 steps (P2)

- [x] R013 `StepPatch.Move`; Google `tasks.move` with `parent`; Microsoft ignores; fake reorders
- [x] R014 `TaskRepository.moveStep`: renumber, `MOVE` for Google
- [x] R015 Pusher step `MOVE`; `mergeSteps` keeps local order for Microsoft or a pending move
- [x] R016 Task page: "reorder steps" in •••, step menu "reorder", reorder layout
- [x] R017 Tests: step move pushed for Google, kept for Microsoft

## Phase 4: US4 + US5 lists and honesty (P2)

- [x] R018 `ListOrderStore` + `ListOrder` (DataStore, adopt local keys) applied to lists section and every list picker
- [x] R019 Reorder lists page and route; lists section ••• "reorder lists" and tile menu "reorder"
- [x] R020 Sync account page line on what order syncs (FR-232)
- [x] R021 Tests: list order store, ordering of new and unknown lists

## Phase 5: Polish

- [x] R022 Screen screenshots (list reorder, steps, lists page) light and dark
- [ ] R023 Drag benchmark across a 200-task list (FR-242), follow-up: needs the benchmark seeder to fill one list
- [x] R024 Full local suite: unit tests, lint, both builds, benchmark compile
