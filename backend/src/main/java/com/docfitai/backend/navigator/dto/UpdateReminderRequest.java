package com.docfitai.backend.navigator.dto;

import jakarta.validation.constraints.NotNull;

/** Marks a reminder done/undone. Rescheduling or renaming is not supported -- delete and recreate instead (CLAUDE.md "Reminder Completion": no complicated task manager). */
public record UpdateReminderRequest(@NotNull Boolean completed) {
}
