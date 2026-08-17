# Insurance network intelligence — research

This document records the official/primary sources consulted before designing DocFit AI's
provider-network-evidence architecture, and states plainly what is IMPLEMENTED, SUPPORTED BY
SOURCE, PLANNED, or EXPERIMENTAL. It is a living document — update it as the implementation
changes, and correct anything that turns out to be wrong rather than leaving it stale.

## Branch-base note

The task that started this phase asked to branch from "the completed provider-data branch."
No branch with that literal name exists in this repository. The branches present are `main`,
`feature/overnight-product-polish`, and `feature/premium-account-and-discovery` (the repo's
`HEAD` at the time this phase started). A diff between the two feature branches shows
`overnight-product-polish` is strictly *behind* `premium-account-and-discovery` (it is missing
auth, saved providers/searches, and several UI components present on the other branch). So
`feature/premium-account-and-discovery` is the most complete, up-to-date branch and is the one
this phase treats as "the completed provider data platform." `feature/insurance-network-intelligence`
was branched from it.

## Sources consulted

1. **HL7 Da Vinci PDex Plan-Net Implementation Guide** (STU1, v1.2.0) —
   http://hl7.org/fhir/us/davinci-pdex-plan-net/ — the HL7-published FHIR IG for exposing a
   payer's insurance plans, networks, and participating organizations/practitioners. Profiles
   `Practitioner`, `PractitionerRole`, `Organization`, `OrganizationAffiliation`, `Location`,
   `HealthcareService`, `Endpoint`, and `InsurancePlan` from FHIR R4 / US Core.
2. **CMS Interoperability and Prior Authorization Final Rule (CMS-0057-F)** —
   https://www.cms.gov/newsroom/fact-sheets/cms-interoperability-prior-authorization-final-rule-cms-0057-f
   and https://www.cms.gov/files/document/cms-0057-f.pdf — requires impacted payers (Medicare
   Advantage, Medicaid/CHIP fee-for-service and managed care, QHP issuers on the FFEs) to expose
   a public-facing, FHIR R4-based **Provider Directory API**, generally without requiring
   authentication for read access, so that any application can query plan/network/provider
   participation. CMS recommends profiling this API per the Da Vinci PDex Plan-Net IG. The
   broader Provider Access / Payer-to-Payer / Prior Authorization APIs in the same rule are a
   distinct, member-authenticated surface with a 2027 compliance deadline — DocFit AI does not
   need those, since it never touches member-specific data (no member ID, no eligibility, no
   claims).
3. **CMS Transparency in Coverage (TiC) machine-readable files** —
   official technical implementation guide/schema repository:
   https://github.com/CMSgov/price-transparency-guide (in-network-rates and allowed-amounts
   JSON Schemas). Covered in depth in `docs/cost-intelligence-research.md`, since price data is
   research-only in this phase.
4. **CMS Hospital Price Transparency (CMS-1717-F2)** —
   https://www.cms.gov/files/document/hospital-price-transparency-final-rule-quick-reference-checklists.pdf
   and the CMS machine-readable data dictionary. Also covered in the cost-intelligence doc.
5. Existing NPPES / NPI Registry API usage already implemented in this repository
   (`backend/src/main/java/com/docfitai/backend/provider/nppes/`) — DocFit AI's existing,
   working pattern for consuming an official CMS data source respectfully (one-time,
   operator-triggered import; no live per-request calls).

Blog posts, vendor marketing pages, and unofficial summaries were used only to *locate* primary
sources (e.g. finding the CMS/HL7 URLs above) — never as the basis for a schema or architecture
decision. Every field/resource named in this document traces to an official CMS or HL7 page.

## What CMS-0057-F actually gives DocFit

The Provider Directory API requirement is the load-bearing fact for this phase: it obligates a
large set of U.S. payers (Medicaid/CHIP FFS and managed care today; Medicare Advantage and QHP
issuers by the rule's phased deadlines) to publish **provider/network participation data via a
public FHIR API that does not require member authentication**. That is a fundamentally different
category from Transparency-in-Coverage pricing files (huge, payer-rate data, no provider-network
"is this doctor in this network" semantics) or from a payer's consumer-facing directory website
(HTML, meant for humans, scraping it would violate this project's "no scraping" rule in
`CLAUDE.md`).

This is exactly the kind of source DocFit AI's `ProviderNetworkConnector` architecture targets:
official, documented, standards-based, and (per the rule) not gated behind credentials for read
access.

## FHIR resources relevant to DocFit, and how they map to our model

| FHIR resource (Plan-Net) | What it represents | DocFit AI concept |
|---|---|---|
| `InsurancePlan` (with `type` = plan vs. network via `InsurancePlan.type`/`.network`) | A payer's plan or network product | `insurance_plan`, `insurance_network` |
| `Organization` (payer role) | The insurer/payer itself | `payer` |
| `Practitioner` | An individual clinician | matched against DocFit's existing `provider` (NPPES-sourced) by NPI |
| `PractitionerRole` | The *link* between a practitioner, an organization, one or more `InsurancePlan`/network references, and a `Location` — this is where "network participation" actually lives in the IG | `provider_network_evidence` (the core evidence row) |
| `Location` | A physical practice address | matched against DocFit's existing `provider` location fields (postal code / address) since DocFit does not yet have a separate `provider_location` table (see "Location model" below) |
| `Organization` (provider role) | A facility/group practice | future `provider` organization-type rows; out of scope this phase (see `CLAUDE.md` §54) |

`PractitionerRole.healthcareService`/`.location`/`.organization` plus a plan/network reference is
the FHIR-native expression of "this named practitioner, at this location, participates in this
plan/network" — i.e. exactly the location-specific evidence this phase's product concept requires
(`CLAUDE.md` §12). This is IMPLEMENTED conceptually in the data model (`provider_network_evidence`
has a nullable location-matching field) and SUPPORTED BY SOURCE for future real ingestion.

## Location model — an honest gap

DocFit AI's current `provider` table (`V4__create_provider_tables.sql`) stores exactly one
address per provider row — there is no separate `provider_location` table yet, because the
NPPES import only ever captured a single practice location per NPI record. Building a full
multi-location `provider_location` table is out of scope for this phase (`CLAUDE.md` explicitly
warns against creating tables "merely to satisfy this prompt" and against forcing
facility/multi-location modeling in prematurely). Instead, `provider_network_evidence` stores a
**snapshot of the matched location** (address line, city, state, postal code) directly on the
evidence row, plus the `match_method` that explains how confidently that location was tied to the
provider (see `docs/insurance-network-architecture.md`). This gives real location-specific
evidence today, without a schema change to the core provider model, and without losing the
information needed to build a proper `provider_location` table later if/when DocFit imports
multi-location provider data.

## Connector reality check (CLAUDE.md §20–21, §82–84)

A live search turned up several **vendor-hosted** FHIR Plan-Net endpoints reported as not
requiring authentication for read access (e.g. Opala's hosted Plan-Net API for at least one named
payer, HAP's public developer portal, Acentra's sandbox). These are real, spec-shaped services,
but:

- They are hosted by third-party API vendors on behalf of payers, not by CMS itself — their
  uptime, terms of use, and continued no-auth availability are not something this project can
  verify or commit to as a *default*, unattended dependency.
- CI must not depend on external network availability (`CLAUDE.md` §82–83) — so even a real
  connector's tests must run against a local mock HTTP server with recorded, spec-compliant FHIR
  fixtures, never a live third-party host.
- No specific payer/vendor endpoint was validated in a support/legal sense (rate limits, ToS,
  long-term stability) within the scope of this engineering phase.

**Decision:** build one real, spec-compliant `FhirPlanNetConnector` that speaks the Da Vinci
Plan-Net resource shapes (`PractitionerRole`, `Practitioner`, `Location`, `InsurancePlan`) over a
plain FHIR REST search, with its base URL supplied only via operator configuration
(`docfitai.insurance.fhir-plan-net.base-url`, unset by default — never a user-supplied URL, per
the SSRF rule in `CLAUDE.md` §80). This is real, testable code (tested against local fixtures),
but it is **not wired to any specific live payer by default** — turning it on for a specific
payer is a deliberate operator action documented in `docs/insurance-network-architecture.md`,
not something DocFit AI does out of the box. Alongside it, a `MockNetworkConnector` provides
deterministic synthetic evidence, active only under the `test` Spring profile, per `CLAUDE.md`
§42 ("never normal development UI unless explicitly labeled SYNTHETIC DEMO DATA").

| Status | Item |
|---|---|
| IMPLEMENTED | `ProviderNetworkConnector` interface; `MockNetworkConnector` (test profile only); domain model; network evidence lookup/match service; evidence API; search integration; evidence UI |
| SUPPORTED BY SOURCE | `FhirPlanNetConnector` — parses real Da Vinci Plan-Net resource shapes; not bound to a live payer by default |
| PLANNED | Operator-configured activation of `FhirPlanNetConnector` against a specific, vetted payer endpoint; scheduled bounded refresh jobs; `provider_location` table if/when multi-location provider data is imported |
| EXPERIMENTAL / NOT DONE | Any nationwide or automatic ingestion; any payer integration requiring credentials this project doesn't have; price/cost linkage (see `docs/cost-intelligence-research.md`) |

## Terminology carried into the product

Per `CLAUDE.md` §2, none of the above ever justifies saying "covered," "in network," or
"guaranteed." A `PractitionerRole` record existing in a payer's Plan-Net feed is *directory
evidence of a network relationship at the time it was retrieved* — not a coverage or payment
guarantee. This distinction is enforced in the status model (`docs/insurance-network-architecture.md`)
and in every piece of evidence-facing UI copy.
