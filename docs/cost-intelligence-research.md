# Cost intelligence — research (design only, not implemented)

Per this phase's scope, price/cost data is **research only**. Nothing described here is built.
No cost data model, no ingestion, no UI. This document exists so a future phase has an accurate
starting point, and so nobody assumes DocFit AI can show personal cost estimates today.

## Official sources consulted

1. **Transparency in Coverage (TiC) machine-readable files** — technical implementation guide
   and JSON Schemas maintained by CMS at https://github.com/CMSgov/price-transparency-guide
   (in particular `schemas/in-network-rates/in-network-rates.json` and
   `schemas/allowed-amounts/allowed-amounts.json`), required by the TiC final rules.
2. **CMS Hospital Price Transparency (CMS-1717-F2)** — quick-reference checklists and machine-
   readable data dictionary published at cms.gov (see `docs/insurance-network-research.md` for
   exact links).

## What data is available

- **In-network rates files** (payers/issuers): for every covered item/service, the negotiated
  rate(s) between a plan and in-network providers. Root object includes `reporting_entity_name`,
  `reporting_entity_type`, `last_updated_on`, and an `in_network` array of billing-code items,
  each with `negotiated_rates` pointing at providers either inline (`provider_groups`) or by
  reference (`provider_references`, a separate top-level array — CMS added this specifically
  because repeating provider group data on every billing code made files impractically large).
- **Allowed-amount files** (payers/issuers): historical **out-of-network** allowed amounts and
  billed charges, aggregated (not tied to a single identifiable provider in the same direct way).
- **Hospital standard-charges files** (facilities, CMS-1717-F2): gross charges, payer-specific
  negotiated charges, discounted cash price, and de-identified min/max negotiated charges per
  item/service, in a CMS-specified template (CSV or JSON) with a required data dictionary.

## Identifiers that exist, and what they can/can't link to

- **Provider identifiers**: the TiC in-network-rates schema's `provider_groups`/
  `provider_references` objects carry **NPI arrays** — both Type 1 (individual) and Type 2
  (organizational) NPIs are supported, and the schema explicitly allows Type-2-only entries when
  an individual NPI isn't available/applicable. This means, in principle, a TiC in-network-rates
  record **can** be joined to DocFit AI's existing `provider.npi_number` — the same join key
  already used for NPPES import and would be used for network evidence matching.
- **Plan/network identifiers**: the reporting root of a TiC file identifies the **reporting
  entity** (the issuer/plan sponsor) and applies to specific plan(s), but does not use the same
  identifier vocabulary DocFit AI would use for `insurance_plan`/`insurance_network` — a real
  integration would need an explicit crosswalk (reporting-entity name/EIN ↔ DocFit's internal
  `payer`/`insurance_plan` rows), which does not exist today and was not built in this phase.
- **Facility identifiers**: hospital price-transparency files are per-facility (one file per
  hospital, on that hospital's own website, per CMS-1717-F2), keyed by the hospital's own
  identifiers (CMS Certification Number where applicable) rather than NPI-only — DocFit AI has no
  facility/organization-provider model yet (see `docs/insurance-network-research.md`, "Location
  model" and `CLAUDE.md` §54), so linking hospital files to DocFit records isn't possible without
  that model existing first.

## Limitations

- **No member-specific cost is derivable from any of this.** A negotiated rate is not what a
  specific patient owes — deductible status, coinsurance, copay tier, and prior-authorization
  outcome all sit on top of it and none of that data is public or available to DocFit AI. This is
  the same distinction `CLAUDE.md` §52 draws between "published negotiated rate" and "your cost,"
  and this phase does not blur it.
- **Files are not designed for point lookups.** They are bulk compliance artifacts (one giant
  JSON per reporting entity, sometimes further split by plan), not a queryable API — consuming
  them means downloading and indexing, not calling an endpoint per provider/service the way the
  Plan-Net directory API works.
- **File size and update cadence.** CMS requires `in_network` and `allowed_amounts` files to be
  refreshed **monthly** and the root `last_updated_on` field kept current. CMS does not publish a
  fixed size limit, and actual file sizes vary enormously by payer/market (large national payers'
  in-network-rates files are widely reported, including by CMS's own technical guidance
  discussions, to run from hundreds of megabytes to many gigabytes per file, occasionally much
  larger for national carriers with broad networks) — this document intentionally does not quote
  a precise number it can't source to an official CMS figure, but the practical implication is
  the same either way: naive whole-file downloads/parsing are not viable for an on-demand user
  request path.
- **No stable, official bulk index exists that maps "give me the file for payer X, plan Y" in one
  call** the way NPPES's search API does for provider identity; discovery generally means finding
  each payer's own published index URL.

## What a safe MVP would require (not built)

If a future phase pursues this, the minimum defensible slice would be:

1. Pick **one** payer + **one** plan + a small, bounded set of billing codes (e.g. an office-visit
   E/M code) as a fixed prototype scope — never a nationwide ingest (`CLAUDE.md` §50, §53).
2. Download that one file once, offline/manually, store only the fields needed
   (`billing_code`, `negotiated_rate`, `negotiated_type`, provider NPI reference) — not the raw
   payload — matching the "no giant unbounded JSON dump" rule (`CLAUDE.md` §49).
3. Build the crosswalk from the file's reporting-entity identity to a DocFit `payer`/
   `insurance_plan` row explicitly, by hand, for that one prototype — not inferred.
4. Never surface a rate as "your cost" — label it exactly as what it is, e.g. "Published
   negotiated rate for [code] under [plan]," with the same kind of provenance/freshness framing
   already used for network evidence.
5. Keep it entirely out of the production/demo UI until a real crosswalk + refresh strategy exists
   for more than one hand-picked example, per `CLAUDE.md` §53.

## Conclusion for this phase

Cost/price intelligence is **PLANNED / EXPERIMENTAL only** — no schema, ingestion, or UI is
implemented in `feature/insurance-network-intelligence`. The one concrete, useful fact this
research produced for the *current* phase is confirmation that NPI is a valid join key across
both the network-evidence side (Plan-Net `Practitioner`/`PractitionerRole`) and the price-
transparency side (TiC `provider_groups`/`provider_references`), which is worth recording so a
future cost-intelligence phase doesn't have to re-derive it.
