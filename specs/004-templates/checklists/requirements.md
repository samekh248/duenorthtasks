# Specification Quality Checklist: List and task templates

**Purpose**: Validate specification completeness before planning
**Created**: 2026-10-06
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Focused on what the user sees and why
- [x] Written for the project owner, not only for developers
- [x] All mandatory sections completed
- [x] Implementation details kept to data-model.md and research.md

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain (Dustin answered the five open questions 2026-10-06)
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Edge cases identified
- [x] Scope is bounded (Assumptions)
- [x] Every claim about a provider API is in research.md

## Constitution

- [x] I. Metro: context menu and ••• entries, pivot, editors that look like the pages they edit; mocked up in light and dark
- [x] II. Fast and Fluid: created items appear at once from one transaction; budgets in FR-340/341
- [x] III. One provider: templates cleared on switch; important flag only in Microsoft mode
- [x] IV. Offline first: templates and created items are local first; creates go through the outbox
- [x] V. Test the seams: no provider changes; created items checked against both fakes (SC-303)
- [x] VI. Small and simple: no service-side storage, no placeholders, no recurrence

## Notes

- Plan and tasks come next (`/speckit-plan`, `/speckit-tasks`) once the spec is approved.
