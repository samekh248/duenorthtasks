# Tasks: List and task templates

**Input**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md)

## Phase 1: Data

- [x] P001 Template entities and `TemplateDao`; Room v3 with AutoMigration 2→3; schema 3.json
- [x] P002 `TaskRepository.createTaskWithSteps` and `createListWithTasks` (tasks queued last first)
- [x] P003 `TemplateRepository`: create, edit, reorder, save task/list as template, use list/task template
- [x] P004 `TemplateRepositoryTest`: save copies, offsets from earliest date, use in both order modes, task offset, reorder, sign-out clears

## Phase 2: Screens

- [x] P010 Templates page (pivot lists · tasks, new, rename, delete)
- [x] P011 List template editor (shade, add, edit, delete, reorder mode, use)
- [x] P012 Template task editor (title, details, steps, due offset picker, important on To Do, reorder steps)
- [x] P013 New list from template (name, start date only when needed, create opens the new list)
- [x] P014 "use a template" under the add box on today and list pages
- [x] P015 "save as template" in the task context menu (home, list page) and the list page ••• menu
- [x] P016 "templates" in the home ••• menu; routes in `NavGraph`
- [x] P017 Sign-out and switch dialogs say templates are removed

## Phase 3: Checks

- [x] P020 `TemplatesScreenshotTest`: every template screen light and dark, and adding from the picker
- [ ] P021 Benchmark: create a list from a 200-task template within 300 ms on the CI emulator (FR-340); needs a seeded template
- [x] P022 Back from a list made from a template goes to home lists, not the templates pages (FR-325); `TemplateNavigationTest`
