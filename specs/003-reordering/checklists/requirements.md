# Specification Quality Checklist: Reordering tasks, lists and steps

**Purpose**: Validate specification completeness before planning
**Created**: 2026-10-06
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Focused on what the user sees and why
- [x] Written for the project owner, not only for developers
- [x] All mandatory sections completed
- [x] Implementation details kept to data-model.md and research.md

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Acceptance scenarios defined for every story
- [x] Edge cases identified
- [x] Scope is bounded (out-of-scope list in Assumptions)
- [x] Every claim about a provider API is in research.md, and unconfirmed behavior is marked "verify live"

## Constitution

- [x] I. Metro: reorder mode follows WP8.1 reorder/rearrange; mocked up in light and dark; no shadows or elevation
- [x] II. Fast and Fluid: moves save locally at once; sync held during reorder mode; drag benchmark added
- [x] III. One provider: behavior defined per provider behind `TaskProvider` and `manualOrder`
- [x] IV. Offline first: order lives in Room/DataStore; pending local move wins and is logged
- [x] V. Test the seams: step move added to the provider contract suite and the fake
- [x] VI. Small and simple: no order data invented for services that can't store it; phone-only order is a display preference

## Notes

- Plan and tasks come next (`/speckit-plan`, `/speckit-tasks`) once the spec is approved.
