# Research: List and task templates

## R1. Do the services have templates?

- **Google Tasks API**: resources are `tasklists` (id, title, etag, updated, selfLink) and `tasks`
  (title, notes, due, status, parent, position, links...). No template resource or flag. Source:
  Tasks API reference, cross-checked with `GoogleTasksApi` in the repo.
- **Microsoft Graph To Do**: `todoTaskList` (displayName, id, isOwner, isShared,
  wellknownListName), `todoTask` and `checklistItem`. No template resource. (Spec 002 research and
  the "List groups" thread, 2026-10-06.)

**Decision**: templates live on the phone only (Dustin, 2026-10-06). Nothing new is sent to either
service; using a template only calls the create endpoints the app already uses.

**Rejected**: a hidden "Due North templates" list on the service, so templates would sync across
phones. It would show up in Google Tasks and To Do as a real list, and its tasks would show up in
other apps' "today" views if they had due dates.

## R2. What can a created task carry?

| Field | Google | Microsoft | Template |
|---|---|---|---|
| title | ✔ | ✔ | ✔ |
| details | ✔ `notes` | ✔ `body` | ✔ |
| due date (date only) | ✔ `due` | ✔ `dueDateTime` at midnight | ✔ as an offset |
| steps | ✔ subtasks (`parent`) | ✔ `checklistItems` | ✔ titles |
| important | ✘ | ✔ `importance=high` | ✔ Microsoft mode only |
| list color | ✘ | ✘ | ✔ shade, kept on the phone like today |

All of these are already written by the existing create paths (spec 001 research, Google and
Microsoft sections), so templates need no provider or contract changes.

## R3. Cost of creating many tasks at once

A 200-task template makes 1 list create + 200 task creates + their steps in the outbox. The outbox
already sends in order and Microsoft uses `$batch` (20 per request), so no new sync code is needed.
The list's creates must be sent after the list's own create returns its remote id, as the outbox
already does for hand-made lists.
