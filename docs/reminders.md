# Reminders

Lightweight, in-app-only follow-up reminders on the Care Navigator dashboard. Full endpoint
reference: `API.md` ("Reminders"). This document covers the product/data decisions.

## What it is, and isn't

A reminder is a user-created note-to-self with a due date -- "Follow up with provider,"
"Confirm insurance," "Check appointment availability," "Review shortlist" (four presets), or a
custom title. It is **not** a calendar appointment, **not** a medical to-do, and **not** a
notification system: DocFit AI never sends email, SMS, or push for a reminder. The only place a
reminder is ever surfaced is the Navigator dashboard itself, grouped into Overdue / Today / This
week / Upcoming / Completed.

## Why presets first

The title field defaults to a dropdown of four nonclinical presets rather than a free-text box,
specifically to steer away from someone typing something like "discuss depression diagnosis" into
a reminder title. Custom text remains available (`Custom…`) because blocking it entirely would be
both paternalistic and easy to work around -- but the presets are the first thing a user sees, and
the custom-title placeholder itself says "avoid sharing medical details here." DocFit AI does not
scan or reject custom reminder titles; this is a UX nudge, not an enforced content filter, matching
the same posture used elsewhere (e.g. the directory-correction report's optional comment field).

## Data model

`user_reminder` (V13 migration): `user_id` (required), `provider_id` (optional FK),
`shortlist_id` (optional FK) -- both independent of each other and of the title/presets; a general
reminder needs neither. `title` (required, max 200 chars), `due_at` (required, stored UTC),
`completed_at` (nullable), `created_at`.

## Date handling

`due_at` is always stored and transmitted as UTC (ISO-8601 with offset); the frontend renders it
in the browser's local timezone via `Date#toLocaleDateString`. Two deliberate validation
decisions, both documented here per CLAUDE.md's "Reminder Date Validation":

- **A past due date is accepted**, not rejected. It simply renders as immediately "Overdue" on the
  dashboard. Rejecting it would be an arbitrary restriction -- there's a legitimate use case for
  "I meant to do this yesterday, track it anyway."
- **A date more than 5 years in the future is rejected** (400). This isn't a meaningful product
  restriction; it exists purely to catch client-side date-math mistakes (e.g. a unit conversion
  bug producing a year-3000 timestamp) rather than silently accepting an absurd value.

## Authorization

Every reminder operation resolves the acting user from the validated access token. Creating a
reminder that references a `providerId` requires that provider to exist (404 otherwise);
referencing a `shortlistId` requires it to be owned by the caller (404 otherwise, same
"existence isn't confirmed to non-owners" pattern used by shortlists/saved searches). Reading,
completing, and deleting a reminder are all scoped to `(id, user_id)` -- another user's reminder
id is always a 404. Covered by `ReminderServiceTest` (validation, ownership) and
`NavigatorAuthorizationTest` (HTTP-level IDOR).

## Completion

`PATCH /api/account/reminders/{id}` with `{ "completed": true|false }` toggles done/undone
in place -- no separate "complete" vs. "reopen" endpoint, and no automatic expiry or archival of
completed reminders (they stay visible, dimmed, under "Completed" until the user deletes them).
Deliberately simple: CLAUDE.md's "Reminder Completion" explicitly warns against building a
complicated task manager here.

## Deliberately not built this phase

- **No push/SMS/email delivery.** The architecture (a `due_at` timestamp and a completion flag)
  would support a future delivery mechanism, but none is wired up -- CLAUDE.md's "Scheduled
  Refresh Architecture"-style caution against building infrastructure before a real need is
  demonstrated applies here too.
- **No recurring reminders.** Every reminder is a one-time, user-created row.
- **No calendar (.ics) export.** A reasonable, self-contained stretch goal (local file generation,
  no calendar-account integration) that wasn't reached this phase.
