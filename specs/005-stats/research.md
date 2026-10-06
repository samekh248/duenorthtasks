# Research: Stats

## R1. What history do the services give?

| Field | Google Tasks | Microsoft To Do | Used for |
|---|---|---|---|
| completed date | ✔ `completed` (RFC 3339 timestamp) | ✔ `completedDateTime` (dateTimeTimeZone) | every "done" number |
| due date | ✔ `due` (date only) | ✔ `dueDateTime` | on time |
| created date | ✘ none | ✔ `createdDateTime` | not used (no parity) |
| who completed it | ✘ | ✘ | not possible |
| deleted tasks | ✘ gone | ✘ gone | can't be counted |
| cleared ("hidden") completed tasks | ✔ with `showHidden=true` (already sent, `GoogleTasksApi`) | n/a | counted |

Both are already stored: `TaskEntity.completedAt`, `dueDate`, `listId` (`core/data/.../Entities.kt`),
with an index on `completedAt`. Sources: Tasks API and Graph `todoTask` references as cross-checked
with `GoogleTasksProvider` and `TodoMapping` in the repo.

**Decision**: compute stats from local rows only. No provider or contract changes.

**Rejected**: "tasks added per week". Google has no created date, so it would work only on
Microsoft (Principle III asks for the same app on both).

## R2. Microsoft's completed time

`TodoMapping` reads `completedDateTime` as a local date-time in the stated zone. To Do clients have
been seen to send it as midnight UTC on the completion day, which shifted into US time zones becomes
the evening before. **Verify live** on Dustin's account before implementing.

**Decision**: for Microsoft, when the stated time is exactly 00:00:00, treat the date part as the
calendar day. Tasks ticked on the phone keep the phone's exact time (the puller keeps the local
`completedAt` unless the completion changed remotely). No time-of-day stats on either service.

## R3. Partial history

A first sync brings open tasks fast and leaves each list's completed history to a background job
(`SyncEngine.backfill`, PR #21/#33). `SyncEngine` already knows which lists are still deferred.

**Decision**: expose "history still loading" as a `StateFlow<Boolean>` next to `isSyncing`, and
let stats mark numbers as lower bounds while it is true (FR-420). If the app is killed mid-load,
the next sync re-defers those lists, so the flag comes back on.

## R4. Cost

10,000 completed tasks × (completedAt, dueDate, listId) is about 240 KB; one indexed query plus
a single pass in Kotlin (bucketing by local day) is well under the 150 ms budget on a mid-range
phone. SQL date bucketing was rejected: `completedAt` is stored as UTC millis and local-day
bucketing needs the zone rules, which SQLite doesn't have.

## R5. Panorama with four sections

`MetroPanorama` (PR #30) places each section once on a ring and needs at least 3; 4 works with no
layout change. The title drifts per section, so check that "tasks" still fits the 4-section
travel. The cold-start swipe benchmark (`PanoramaBenchmark.swipePanorama`) loops 3 swipes each
way and must loop 4 so it still crosses the seam. All four sections are composed at start, so the
stats section composes only placeholders until its data arrives (FR-431).
