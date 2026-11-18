package edu.sjsu.cmpe172.tutorbook.ai.reminder;

/** The reminder the LLM writes, returned as structured output (JSON mapped to this record). */
public record ReminderDraft(String subject, String message) {
}
