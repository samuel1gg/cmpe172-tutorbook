package edu.sjsu.cmpe172.tutorbook.ai.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.ai.AiStatus;
import edu.sjsu.cmpe172.tutorbook.ai.rag.KnowledgeBase;
import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationDispatcher;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * AGENT 2: the autonomous (proactive) reminder agent.
 *
 * Nobody prompts it. A Spring @Scheduled job wakes up on its own (every hour by
 * default) and, for each BOOKED appointment starting within the next 24 hours
 * that has not been reminded yet:
 *   1. OBSERVE  load the appointment (customer, tutor, service, time, notes)
 *               and retrieve the relevant policies from the knowledge base (RAG),
 *   2. THINK    ask Gemini to write a short personalized reminder
 *               (structured output: subject + message),
 *   3. CHECK    ReminderSelfCheck verifies the facts; one self-correction round,
 *               then a safe template if the model still gets it wrong,
 *   4. ACT      send it through the notification service (NotificationDispatcher,
 *               the Milestone 3 boundary with retries and idempotency).
 *
 * POST /admin/agents/reminders/run triggers the same run manually for the demo.
 */
@Service
public class ReminderAgent {

    private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US);
    static final int MAX_PER_RUN = 20;

    static final String SYSTEM_PROMPT = """
            You write appointment reminders for TutorBook, an SJSU tutoring center.
            Write a friendly, personal reminder of 2-4 sentences (under 600 characters).
            It must: greet the student by first name, name the tutor, state the subject and the exact
            day and start time as given, mention one relevant tip (use the student's note if there is
            one, otherwise something to bring or prepare), and briefly mention the cancellation rule
            from the policies if one is provided.
            Do not include links, sign-offs with placeholder names, or anything in [brackets].
            Do not invent facts that are not given.
            """;

    public record ReminderResult(long appointmentId, String customer, String startsAt,
                                 String writtenBy, String delivery, String subject, String message) {
    }

    public record RunReport(String trigger, LocalDateTime ranAt, int due, List<ReminderResult> reminders) {
    }

    private final ChatClient chatClient;
    private final AppointmentRepository appointments;
    private final NotificationDispatcher dispatcher;
    private final KnowledgeBase knowledgeBase;
    private final ReminderSelfCheck selfCheck;
    private final AiStatus aiStatus;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Duration window;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ReminderAgent(ChatClient.Builder builder, AppointmentRepository appointments,
                         NotificationDispatcher dispatcher, KnowledgeBase knowledgeBase,
                         ReminderSelfCheck selfCheck, AiStatus aiStatus, Clock clock, MeterRegistry meters,
                         @Value("${tutorbook.ai.reminders.window:24h}") Duration window) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.appointments = appointments;
        this.dispatcher = dispatcher;
        this.knowledgeBase = knowledgeBase;
        this.selfCheck = selfCheck;
        this.aiStatus = aiStatus;
        this.clock = clock;
        this.meters = meters;
        this.window = window;
    }

    /** The autonomous trigger: no user, no prompt; runs on its own schedule. */
    @Scheduled(cron = "${tutorbook.ai.reminders.cron:0 0 * * * *}")
    public void scheduledRun() {
        if (!aiStatus.isConfigured()) {
            return;
        }
        RunReport report = run("schedule");
        if (report.due() > 0) {
            log.info("Reminder agent (scheduled) handled {} appointment(s)", report.due());
        }
    }

    /** One pass over the due appointments. Used by the schedule and the manual endpoint. */
    public RunReport run(String trigger) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!aiStatus.isConfigured()) {
            return new RunReport(trigger + " (skipped: " + AiStatus.NOT_CONFIGURED_MESSAGE + ")", now, 0, List.of());
        }
        if (!running.compareAndSet(false, true)) {
            return new RunReport(trigger + " (skipped: a run is already in progress)", now, 0, List.of());
        }
        try {
            List<AppointmentDetails> due = appointments.findDueForReminder(now, now.plus(window), MAX_PER_RUN);
            log.info("Reminder agent ({}): {} appointment(s) due in the next {}", trigger, due.size(), window);
            List<ReminderResult> results = new ArrayList<>();
            for (AppointmentDetails a : due) {
                results.add(remind(a));
            }
            return new RunReport(trigger, now, due.size(), results);
        } finally {
            running.set(false);
        }
    }

    private ReminderResult remind(AppointmentDetails a) {
        String policies = relevantPolicies(a);
        String writtenBy;
        ReminderDraft draft = generate(a, policies, List.of());
        List<String> problems = selfCheck.problems(draft, a);
        if (problems.isEmpty()) {
            writtenBy = "AI";
        } else {
            log.info("Reminder for appointment {} failed self-check {}; asking the model to fix it",
                    a.appointmentId(), problems);
            draft = generate(a, policies, problems);
            List<String> remaining = selfCheck.problems(draft, a);
            if (remaining.isEmpty()) {
                writtenBy = "AI (self-corrected)";
            } else {
                log.warn("Reminder for appointment {} still failed self-check {}; using template",
                        a.appointmentId(), remaining);
                draft = template(a);
                writtenBy = "TEMPLATE (AI draft failed self-check)";
            }
        }
        String delivery = dispatcher.sendReminder(a, draft.subject(), draft.message());
        meters.counter("tutorbook.ai.reminders", "written_by", writtenBy.startsWith("AI") ? "ai" : "template",
                "delivery", delivery.toLowerCase(Locale.US)).increment();
        return new ReminderResult(a.appointmentId(), a.customerName(), a.startTime().format(WHEN),
                writtenBy, delivery, draft.subject(), draft.message());
    }

    /** Asks Gemini for a reminder; returns null if the call fails (the self-check then rejects it). */
    ReminderDraft generate(AppointmentDetails a, String policies, List<String> fixThese) {
        String facts = """
                Student: %s
                Tutor: %s
                Subject: %s
                Day and start time: %s
                Length: until %s
                Student's note: %s
                Appointment number: %d

                Relevant TutorBook policies:
                %s
                """.formatted(a.customerName(), a.providerName(), a.serviceName(),
                a.startTime().format(WHEN), a.endTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)),
                a.notes() == null || a.notes().isBlank() ? "(none)" : a.notes(), a.appointmentId(), policies);
        String request = fixThese.isEmpty()
                ? "Write the reminder for this appointment.\n\n" + facts
                : "Your previous reminder had these problems: " + String.join("; ", fixThese)
                  + ". Write it again and fix them.\n\n" + facts;
        try {
            return chatClient.prompt().user(u -> u.text("{request}").param("request", request))
                    .call().entity(ReminderDraft.class);
        } catch (RuntimeException e) {
            log.warn("LLM call for reminder {} failed: {}", a.appointmentId(), e.toString());
            return null;
        }
    }

    /** RAG: the policy chunks most relevant to this appointment (cancellation rule, what to bring). */
    private String relevantPolicies(AppointmentDetails a) {
        try {
            List<Document> hits = knowledgeBase.search(
                    "cancellation policy and what to bring to a " + a.serviceName() + " session");
            return hits.stream().limit(2).map(Document::getText).collect(Collectors.joining("\n\n"));
        } catch (RuntimeException e) {
            return "(policies unavailable)";
        }
    }

    /** Safe fallback that always passes the self-check. */
    static ReminderDraft template(AppointmentDetails a) {
        String when = a.startTime().format(WHEN);
        String first = a.customerName().split("\\s+")[0];
        return new ReminderDraft(
                "Reminder: " + a.serviceName() + " on " + when,
                "Hi " + first + ", this is a reminder of your " + a.serviceName() + " session with "
                        + a.providerName() + " on " + when + " (appointment #" + a.appointmentId()
                        + "). If you can no longer make it, please cancel in TutorBook.");
    }
}
