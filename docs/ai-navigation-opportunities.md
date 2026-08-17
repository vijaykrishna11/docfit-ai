# AI navigation opportunities — research only, not implemented

Nothing in this document is built. It exists so a future phase has a considered starting point,
and so nobody assumes "DocFit AI" implies an ML/LLM feature exists today -- it doesn't. Practical
fit, filtering, and sorting are all fully deterministic (CLAUDE.md "No Fake AI").

## Why nothing was added this phase

The brand name containing "AI" is not, by itself, a reason to add a model. Every feature this
phase needed -- filters, a map, shortlists, reports, recent searches -- was fully served by
deterministic logic. Introducing an LLM/ML component where a plain filter or a SQL query already
solves the problem would add real operational cost (latency, a new failure mode, a new thing to
keep safe within the healthcare-navigation boundary) for no user-facing benefit.

## Ideas that would stay safely on the navigation side of the line

1. **Natural-language administrative search parsing.** A prototype could parse a query like
   "cardiologists within 10 miles of Long Beach" into the same deterministic filters
   (`specialty=CARDIOLOGY`, `location=Long Beach`, `radius=10`) the search form already produces.
   This is intent parsing into an existing, already-safe filter contract -- the model (if one were
   used at all) would never itself decide what's medically relevant; it would only map words to
   filter fields DocFit AI already validates and applies the same way regardless of how they were
   entered.

   **Hard boundary**: input framed as a symptom or medical question --
   e.g. "I have chest pain, which doctor do I need?" -- must **never** be parsed into a specialty
   inference. That's diagnosis-adjacent, squarely outside CLAUDE.md's boundary. A safe
   implementation would need to detect and decline that class of input explicitly (e.g. route it to
   a static "DocFit AI doesn't provide medical guidance -- if this is urgent, contact a medical
   professional" message), not attempt to interpret it more "helpfully."

2. **Provider directory data normalization.** NPPES/CSV source data has real-world messiness
   (inconsistent casing, abbreviation variants, punctuation). A model could assist a human review
   step for deduplication/normalization suggestions -- but any such suggestion would need to
   remain a *suggestion* an operator approves, never an automatic overwrite of imported data,
   consistent with how directory-correction reports already work (review signal, not auto-apply).

3. **Explanation generation from deterministic evidence.** `WhyThisResult`'s existing panel is
   template-generated from real fields (specialty match, distance, source, insurance evidence
   status). A model could theoretically generate more natural prose from those same fields --
   but the underlying facts must stay identical to what deterministic logic already computes; the
   model would only be a phrasing layer, never a new source of claims. Given the existing
   template-based copy is already clear and tested against the "no unqualified claims" invariant
   (`networkEvidenceDisplay.test.ts`), this isn't clearly worth the added complexity/failure
   surface unless a real UX problem with the current copy shows up.

## Explicitly not on this list

Symptom interpretation, diagnosis, treatment suggestions, medication guidance, or any ranking of
providers by inferred "quality" or "best fit" -- all remain outside DocFit AI's scope regardless of
whether a model is involved. This isn't a gap to fill later; it's the boundary the whole product is
built around (CLAUDE.md "Hard scope boundary").

## If this is ever picked up

Treat it as its own phase with its own directive, not a quick add-on: it needs a decision on
provider/vendor for the model, a cost/latency budget, a fallback behavior when the model is
unavailable (the deterministic filter UI must keep working with zero degradation), and explicit
test coverage for the "declines to answer a medical question" boundary before anything ships.
