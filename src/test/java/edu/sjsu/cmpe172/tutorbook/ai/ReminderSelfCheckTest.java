package edu.sjsu.cmpe172.tutorbook.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import edu.sjsu.cmpe172.tutorbook.ai.reminder.ReminderDraft;
import edu.sjsu.cmpe172.tutorbook.ai.reminder.ReminderSelfCheck;
import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;

/** Unit tests for the bonus self-check: wrong facts are caught before anything is sent. */
class ReminderSelfCheckTest {

    final ReminderSelfCheck check = new ReminderSelfCheck();
    final AppointmentDetails appt = new AppointmentDetails(1L, "BOOKED", null, LocalDateTime.now(), 1L,
            "Alice Nguyen", "alice@student.sjsu.edu", 2L, 1L,
            LocalDateTime.of(2026, 11, 18, 11, 0), LocalDateTime.of(2026, 11, 18, 12, 0),
            "Dr. Priya Patel", "Calculus Tutoring", 4000);

    @Test
    void correctDraftPasses() {
        var draft = new ReminderDraft("Calculus tomorrow",
                "Hi Alice! Your Calculus Tutoring with Dr. Patel is on Wednesday, November 18 at 11:00 AM.");
        assertThat(check.problems(draft, appt)).isEmpty();
    }

    @Test
    void acceptsShortTimeAndMonthDayForms() {
        var draft = new ReminderDraft("Reminder", "Hi Alice, Priya Patel will see you Nov 18 at 11am.");
        assertThat(check.problems(draft, appt)).isEmpty();
    }

    @Test
    void wrongTimeIsCaught() {
        var draft = new ReminderDraft("Reminder", "Hi Alice, Dr. Patel will see you Wednesday at 1:00 PM.");
        assertThat(check.problems(draft, appt)).anyMatch(p -> p.contains("start time"));
    }

    @Test
    void wrongDayMissingNamesLinksAndPlaceholdersAreCaught() {
        var draft = new ReminderDraft("Reminder", "See you Friday at 11:00 AM: https://zoom.us/x [Tutor Name]");
        assertThat(check.problems(draft, appt)).hasSize(5);
    }

    @Test
    void emptyDraftIsRejected() {
        assertThat(check.problems(null, appt)).isNotEmpty();
        assertThat(check.problems(new ReminderDraft(" ", ""), appt)).isNotEmpty();
    }
}
