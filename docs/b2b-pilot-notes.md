# B2B / university pilot notes (documentation only — not implemented)

No multi-tenancy is built in this phase. This document exists so a future phase understands how
the domain model introduced here (`payer` / `insurance_plan` / `insurance_network` /
`provider_network_evidence`) could extend to a university or employer pilot, without committing
to any of it now.

## What "university student plan" or "employer plan" actually is, in this model

A student health plan or an employer-sponsored plan is not architecturally different from any
other `insurance_plan` row — it already has a `payer_id`, a `plan_type`, and can participate in
one or more `insurance_network` rows via `plan_network`. No new table is required to represent
"Cal State Long Beach's student plan" or "Acme Corp's employer plan" — it is simply another row
in `insurance_plan` with its own `external_plan_identifier`.

## What a pilot would add (not built)

1. **An organization/catalog concept** — e.g. an `organization` table (university or employer)
   with a curated subset of `insurance_plan` rows it wants surfaced first (a "your plans" shortcut
   above the general payer/plan selector). This is additive: it wraps existing `insurance_plan`
   rows rather than duplicating them.
2. **A pilot entry point** — e.g. a `docfitai.com/for/csulb` landing path that pre-filters the
   plan selector to that organization's catalog, while still allowing the general payer/plan
   selector underneath (per `CLAUDE.md`'s standing rule that search must never be blocked or
   narrowed without an escape hatch).
3. **Still no member-specific data.** A pilot does not mean collecting student/employee IDs,
   group numbers, or eligibility — the plan catalog narrows *which plans are shown first*, not
   *what data DocFit collects*. The privacy rules in `docs/insurance-network-architecture.md`
   ("Privacy") apply identically to pilot and non-pilot users.

## Why this is deferred

`CLAUDE.md` §95 explicitly says not to build multi-tenancy in this phase, and the core evidence
architecture (payer/plan/network/evidence + connector + search integration + evidence UI) is
already the full scope of this phase. Layering an organization/catalog concept on top before the
underlying evidence model has run in production would be premature — it's a UX/catalog feature on
top of the same data, not a data-model risk, so it's safe to defer without re-architecting
anything built here.
