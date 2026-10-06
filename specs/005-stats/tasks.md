# Tasks: Stats and the empty today

- [x] S001 Completed and open stats queries in `TaskDao`, exposed by `TaskRepository` (FR-410, FR-416)
- [x] S002 `SyncEngine.historyLoading` (FR-420)
- [x] S003 `Stats.compute`: this week, streak, on time, grid, right now, by list, all time (FR-410 to FR-416)
- [x] S004 Unit tests for S003 including week start and Microsoft midnight-UTC dates (SC-402)
- [x] S005 `StatsSource` / `StatsViewModel`: off the main thread, at most one recount a second, lazy start (FR-430, FR-431, FR-433)
- [x] S006 `StatsSection` UI with partial-history and nothing-done states (US1 to US3)
- [x] S007 Fourth panorama section; stats app bar matches "done" (FR-401 to FR-403)
- [x] S008 `EmptyToday`: centered logo drawing itself, replay on return and tap, still when animations are off (US4, FR-440 to FR-443)
- [x] S009 `PanoramaBenchmark` covers four sections (FR-432)
- [x] S010 Screenshots: stats top, lower, partial, empty today, light and dark (`StatsScreenshotTest`)
- [ ] S011 Verify Microsoft's `completedDateTime` on a real account (research R2)
