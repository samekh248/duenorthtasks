# Data model: List and task templates

Three new Room tables in the next database version (v3, AutoMigration from v2). They have no
`remoteId`, are never in the outbox or sync log. `template_list` and `template_task` carry an `accountId`
foreign key to `account` with `ON DELETE CASCADE`, so `AccountRepository.disconnect` (which deletes
the account row) removes them with the lists and tasks (FR-303).

```mermaid
erDiagram
    TEMPLATE_LIST ||--o{ TEMPLATE_TASK : holds
    TEMPLATE_TASK ||--o{ TEMPLATE_STEP : holds
    ACCOUNT ||--o{ TEMPLATE_LIST : owns
    ACCOUNT ||--o{ TEMPLATE_TASK : owns
    TEMPLATE_LIST {
        string id PK
        int accountId FK
        string name
        int shadeStep "-3..3, 0 = app accent"
        instant updatedAt
    }
    TEMPLATE_TASK {
        string id PK
        int accountId FK
        string templateListId FK "null = a standalone task template"
        string title
        string notes "nullable"
        boolean important "Microsoft mode only"
        int dueOffsetDays "nullable; list: from start, task: from today (>= 0)"
        int sortOrder
        instant lastUsedAt "nullable; orders the add-box picker"
    }
    TEMPLATE_STEP {
        string id PK
        string templateTaskId FK
        string title
        int sortOrder
    }
```

Rules:

- Deleting a list template cascades to its tasks and steps.
- Using a template copies rows into `task_list`, `task` and `step` in one transaction and enqueues
  `CREATE` operations, exactly like hand-made items (FR-323). The new list's shade is written to
  `ListShadeStore` under its local id and adopted to the remote id after sync, as today.
- Task order in a list template uses `sortOrder` integers; renumbering at most 200 rows is fine.
