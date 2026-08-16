# Shortlists

An optional, named-grouping layer on top of the existing saved-providers list. Full endpoint
reference: `API.md` ("Shortlists"). This document covers the product/data-model decisions.

## Relationship to saved providers

DocFit AI already had a one-click "Saved providers" list (`saved_provider` table) before this
phase. Shortlists (`provider_shortlist` / `shortlist_provider`, V11 migration) are **additive**,
not a replacement:

- Saving a provider stays a single click (heart icon) -- never forces a "which shortlist?" dialog.
- A saved provider can *optionally* also belong to one or more named shortlists (e.g.
  "Cardiology options", "Near campus").
- Deleting a shortlist removes only that grouping (`shortlist_provider` rows, cascaded at the DB
  level) -- it never removes the provider from the plain saved-providers list, and never touches
  provider directory data.

This is a deliberate, additive migration: existing saved-provider behavior and data are completely
unchanged by this phase.

## Privacy

Shortlist **names** are treated as private user data -- they may contain sensitive context (e.g.
"Parents", "Near campus" could imply a relationship or location a user wouldn't want exposed).
Names:

- Are only ever returned in authenticated, ownership-scoped API responses.
- Are never included in the public "Share selected providers" link (see below) -- that link
  carries only public provider ids, nothing about which shortlist (if any) they came from.
- Are never logged.

## Authorization

Every shortlist operation resolves the acting user from the validated access token, never a
client-supplied id. A shortlist owned by another user is always a **404**, never a 403 -- so a
request for someone else's shortlist id can't even confirm it exists. Covered by
`ShortlistAuthorizationTest` (HTTP-level IDOR test: register two users, attempt to read/rename/
delete/add-to/remove-from the first user's shortlist as the second, assert 404 throughout, assert
the first user's data is untouched).

## Concurrency

`ShortlistService.addProvider` uses `INSERT ... ON CONFLICT (shortlist_id, provider_id) DO NOTHING`
rather than a check-then-insert, for the same reason as `SavedProviderService` (see
`docs/release-checklist.md`'s account on that earlier bug): a `@Transactional` method can't
recover from a unique-constraint violation via try/catch once Hibernate has already marked the
transaction rollback-only during flush. Verified with a real two-thread concurrency test
(`ShortlistServiceTest.concurrentAddOfTheSameProviderNeverThrowsAndLeavesExactlyOneRow`), not just
reasoned about.

## No clinical notes field

Deliberately, there is no free-text "notes" field on a shortlist or a shortlisted provider. Adding
one would implicitly invite storing health/medical context DocFit AI has no reason to collect and
every reason to avoid (CLAUDE.md's hard scope boundary). Shortlist functionality is organizational
only -- a name and a set of provider ids.

## "Add to shortlist" UX placement

The "Add to shortlist" action lives only on the provider detail page, as a secondary action next
to Save -- not on every search-result card. Putting it on every card would crowd an already
information-dense card (CLAUDE.md "Card Design V3" explicitly warns against this) for a
lower-frequency action than Save/Compare/Call/Directions.

## Membership check

When "Add to shortlist" opens, it needs to show which of the user's existing shortlists already
contain this provider. There's no dedicated backend query for that yet, so the frontend fetches
full detail (including the provider list) for each of the user's shortlists once, client-side, and
derives membership from that. This is bounded, personal-list-management work (a user's shortlist
count is typically small, single digits) -- not the kind of per-search-result N+1 the backend
search path deliberately avoids. If shortlist counts ever grow large enough for this to matter, add
a dedicated `GET .../shortlists?providerId=` membership-check endpoint then, not preemptively.

## Share selected providers

A related but separate feature (not a shortlist-specific mechanism): `/share/providers?ids=...`
is reachable from a shortlist's detail page (select providers via checkbox, "Share selected") as
well as from the plain saved-providers list. No shortlist context leaks into that link -- see
"Privacy" above. There is no backend sharing table; the provider ids in the URL are the entire
share.
