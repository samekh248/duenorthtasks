# Due North Tasks

Android todo app in the Windows Phone 8.1 Metro design language, syncing with Google Tasks or
Microsoft To Do (one at a time).

- Rules: `.specify/memory/constitution.md` (read before any change).
- Current feature: `specs/001-metro-todo-app/` (spec, plan, data model, contracts, tasks).
- Stack: Kotlin, Jetpack Compose (foundation only, no Material), Hilt, Room, WorkManager,
  Retrofit, MSAL, Google Identity Services.
- Never commit client IDs, `google-services.json` or keystores.
