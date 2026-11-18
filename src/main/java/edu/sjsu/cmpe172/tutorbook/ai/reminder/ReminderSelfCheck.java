package edu.sjsu.cmpe172.tutorbook.ai.reminder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;

/**
 * BONUS: self-check (reflection) for AI-written reminders.
 *
 * An LLM can drop or change a fact. Before a reminder is sent, plain code checks
 * the draft against the real appointment row: it must name the customer and the
 * tutor, state the correct day and time, stay within the length limits, and
 * contain no links or "[placeholders]". If anything is wrong, the agent asks the
 * model to fix exactly those problems once; if it is still wrong, a safe
 * template is sent instead. A wrong reminder is never sent.
 */
@Component
public class ReminderSelfCheck {

    static final int MAX_SUBJECT = 200;
    static final int MAX_MESSAGE = 1000;

    public List<String> problems(ReminderDraft draft, AppointmentDetails a) {
        List<String> problems = new ArrayList<>();
        if (draft == null || isBlank(draft.subject()) || isBlank(draft.message())) {
            problems.add("subject and message must both be present");
            return problems;
        }
        String subject = draft.subject();
        String message = draft.message();
        String all = (subject + " " + message).toLowerCase(Locale.US);
        if (subject.length() > MAX_SUBJECT) {
            problems.add("subject is longer than " + MAX_SUBJECT + " characters");
        }
        if (message.length() > MAX_MESSAGE) {
            problems.add("message is longer than " + MAX_MESSAGE + " characters");
        }
        String firstName = a.customerName().split("\\s+")[0];
        if (!all.contains(firstName.toLowerCase(Locale.US))) {
            problems.add("must greet the customer by first name (" + firstName + ")");
        }
        if (!mentionsTutor(all, a.providerName())) {
            problems.add("must name the tutor (" + a.providerName() + ")");
        }
        if (!mentionsDay(all, a.startTime())) {
            problems.add("must state the day: " + a.startTime().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)));
        }
        if (!mentionsTime(all, a.startTime())) {
            problems.add("must state the start time: " + a.startTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)));
        }
        if (all.contains("http://") || all.contains("https://") || all.contains("www.")) {
            problems.add("must not contain links");
        }
        if (all.matches("(?s).*\\[[^\\]]*\\].*")) {
            problems.add("must not contain [placeholders]");
        }
        return problems;
    }

    static boolean mentionsTutor(String text, String providerName) {
        for (String part : providerName.replace(".", " ").split("\\s+")) {
            if (part.length() > 2 && !part.equalsIgnoreCase("dr") && text.contains(part.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    static boolean mentionsDay(String text, LocalDateTime start) {
        String weekday = start.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US).toLowerCase(Locale.US);
        String month = start.getMonth().getDisplayName(TextStyle.SHORT, Locale.US).toLowerCase(Locale.US);
        return text.contains(weekday) || text.matches("(?s).*" + month + "[a-z]*\\.?\\s+" + start.getDayOfMonth() + "\\b.*");
    }

    static boolean mentionsTime(String text, LocalDateTime start) {
        String compact = text.replace(" ", "").replace(".", "");
        int hour12 = start.getHour() % 12 == 0 ? 12 : start.getHour() % 12;
        String ampm = start.getHour() < 12 ? "am" : "pm";
        String hm = hour12 + ":" + String.format("%02d", start.getMinute());
        if (compact.contains(hm + ampm)) {
            return true;
        }
        return start.getMinute() == 0 && compact.matches("(?s).*(?<![0-9:])" + hour12 + ampm + ".*");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
