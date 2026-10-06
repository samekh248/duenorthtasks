# Specification Quality Checklist: Shared lists

**Purpose**: Validate specification completeness before planning
**Created**: 2026-10-05
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
- [x] Every claim about a provider API is sourced in research.md, and unconfirmed behavior is marked "verify live"

## Constitution

- [x] I. Metro: new glyph, header line and sharing page mocked up in light and dark
- [x] II. Fast and Fluid: no new network requests for sharing marks; updates fade in place
- [x] III. One provider: behavior defined per provider behind `TaskProvider`
- [x] IV. Offline first: marks come from the local database
- [x] V. Test the seams: new `NotAllowed` error and fields added to the contract suite
- [x] VI. Small and simple: no data invented that the providers don't give; shades stay local

## Notes

- Plan and tasks come next (`/speckit-plan`, `/speckit-tasks`) once the spec is approved.
