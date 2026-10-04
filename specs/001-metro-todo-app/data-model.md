# Data Model: Due North Tasks v1

Phase 1 output for [plan.md](plan.md). Local Room schema in `core:data`.

```mermaid
erDiagram
    ACCOUNT ||--o{ TASK_LIST : owns
    TASK_LIST ||--o{ TASK : contains
    TASK ||--o{ STEP : has
    ACCOUNT ||--o{ PENDING_OPERATION : queues
    ACCOUNT ||--o{ SYNC_LOG_ENTRY : records

    ACCOUNT {
        int id PK "always 1, at most one row"
        enum provider "GOOGLE or MICROSOFT"
        string displayName
        string email
        string listsCursor "To Do lists deltaLink, null for Google"
        instant lastSyncAt
        enum authState "OK or NEEDS_SIGN_IN"
    }
    TASK_LIST {
        uuid localId PK
        string remoteId "null until created remotely"
        string title
        bool isDefault
        string tasksCursor "Google updatedMin or To Do deltaLink"
        string etag
        instant remoteUpdatedAt
        instant localUpdatedAt
        bool deletedLocally
    }
    TASK {
        uuid localId PK
        uuid listId FK
        string remoteId
        string title
        string notes
        date dueDate
        bool completed
        instant completedAt
        bool important "Microsoft only"
        string position "Google position or local order key"
        string remoteStatusRaw "keeps To Do inProgress etc."
        string etag
        instant remoteUpdatedAt
        instant localUpdatedAt
        bool deletedLocally
    }
    STEP {
        uuid localId PK
        uuid taskId FK
        string remoteId "Google child task id or To Do checklistItem id"
        string title
        bool done
        int order
    }
    PENDING_OPERATION {
        long seq PK "push order"
        enum entity "LIST, TASK, STEP"
        uuid entityLocalId
        enum kind "CREATE, UPDATE, DELETE, MOVE"
        string changedFields "JSON list for PATCH"
        int attempts
        instant createdAt
    }
    SYNC_LOG_ENTRY {
        long id PK
        instant at
        enum type "CONFLICT, ERROR, RECOVERED"
        string summary
        string losingVersionJson
    }
```

## Rules

- **One account**: `ACCOUNT.id` is fixed to 1, so a second provider cannot be stored. Switching
  provider deletes the row, which cascades to every other table (constitution Principle III).
- **Validation**: list title 1-256 chars; task title 1-1024 chars (Google's limit is the
  tighter one); notes up to 8,192 chars; steps up to 100 per task.
- **Soft delete**: a local delete sets `deletedLocally` and enqueues `DELETE`; the row is removed
  once the provider confirms.
- **Outbox coalescing**: a new `UPDATE` for an entity that already has a queued `UPDATE` merges
  `changedFields` instead of adding a row; `CREATE` followed by `DELETE` before push cancels both.

## Task state

```mermaid
stateDiagram-v2
    [*] --> LocalOnly: created on phone
    LocalOnly --> Synced: push CREATE ok
    [*] --> Synced: pulled from provider
    Synced --> Dirty: edited on phone
    Dirty --> Synced: push UPDATE ok
    Dirty --> Conflict: remote also changed
    Conflict --> Synced: newest wins, loser logged
    Synced --> PendingDelete: deleted on phone
    Dirty --> PendingDelete: deleted on phone
    PendingDelete --> [*]: push DELETE ok
    Synced --> [*]: deleted remotely
```

## Provider field mapping

| Local | Google Tasks | Microsoft To Do |
|---|---|---|
| TaskList.title | `tasklist.title` | `todoTaskList.displayName` |
| TaskList.isDefault | first list (`@default`) | `wellknownListName == defaultList` |
| Task.title | `task.title` | `todoTask.title` |
| Task.notes (shown as "details") | `task.notes` | `todoTask.body.content` (`contentType=text`) |
| Task.dueDate | `task.due` (date part) | `todoTask.dueDateTime` (midnight, device zone) |
| Task.completed | `status == completed` | `status == completed` |
| Task.important | not supported, hidden | `importance == high` |
| Task.position | `task.position` | local only (no API ordering) |
| Step | child `task` with `parent` | `checklistItem` (`displayName`, `isChecked`) |
