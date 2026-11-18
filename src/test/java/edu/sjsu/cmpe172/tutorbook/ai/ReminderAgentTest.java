package edu.sjsu.cmpe172.tutorbook.ai;

import static edu.sjsu.cmpe172.tutorbook.ai.FakeAiConfig.ScriptedChatModel.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import edu.sjsu.cmpe172.tutorbook.ai.FakeAiConfig.ScriptedChatModel;
import edu.sjsu.cmpe172.tutorbook.ai.reminder.ReminderAgent;
import edu.sjsu.cmpe172.tutorbook.ai.reminder.ReminderAgent.ReminderResult;
import edu.sjsu.cmpe172.tutorbook.ai.reminder.ReminderAgent.RunReport;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationGateway;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationGateway.SendResult;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationRequest;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationType;

/**
 * Agent 2: the autonomous reminder agent, with the LLM scripted and the external
 * notification service mocked. Seed data has two upcoming BOOKED appointments
 * (#3 Chen, #1 Alice); a 7-day window makes both due whatever time the test runs.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reminder-agent-test;DB_CLOSE_DELAY=-1",
        "spring.ai.google.genai.api-key=test-key-not-used",
        "spring.ai.google.genai.embedding.api-key=test-key-not-used",
        "tutorbook.ai.reminders.window=7d"})
@Import(FakeAiConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderAgentTest {

    @Autowired
    ReminderAgent agent;

    @Autowired
    ScriptedChatModel model;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    NotificationGateway gateway;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM notification_log WHERE type = 'REMINDER'").update();
        reset(gateway);
        when(gateway.send(any())).thenReturn(SendResult.delivered(1));
    }

    @Test
    void writesPersonalizedRemindersAndSendsThemThroughTheNotificationService() {
        model.respondWith(prompt -> text(goodDraft(prompt)));

        RunReport report = agent.run("manual");

        assertThat(report.due()).isEqualTo(2);
        assertThat(report.reminders()).extracting(ReminderResult::appointmentId).containsExactly(3L, 1L);
        assertThat(report.reminders()).allSatisfy(r -> {
            assertThat(r.writtenBy()).isEqualTo("AI");
            assertThat(r.delivery()).isEqualTo("SENT");
        });
        // the outbound action: two REMINDER requests went to the external service
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(gateway, times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(r -> assertThat(r.type()).isEqualTo(NotificationType.REMINDER));
        assertThat(sent.getAllValues().get(1).recipient()).isEqualTo("alice@student.sjsu.edu");
        assertThat(sent.getAllValues().get(1).message()).contains("Hi Alice").contains("integration by parts");
        // the prompt was grounded with the policy file (RAG)
        assertThat(userText(model.prompts.get(0))).contains("Cancellation policy");

        // running again does not remind anyone twice
        assertThat(agent.run("manual").due()).isZero();
        verify(gateway, times(2)).send(any());
    }

    @Test
    void wrongDraftIsFixedInASelfCorrectionRound() {
        AtomicInteger calls = new AtomicInteger();
        model.respondWith(prompt -> calls.getAndIncrement() % 2 == 0
                ? text("{\"subject\":\"Reminder\",\"message\":\"See you at 10:30 PM!\"}")   // wrong time, no names
                : text(goodDraft(prompt)));

        RunReport report = agent.run("manual");

        assertThat(report.reminders()).allSatisfy(r -> {
            assertThat(r.writtenBy()).isEqualTo("AI (self-corrected)");
            assertThat(r.delivery()).isEqualTo("SENT");
        });
        assertThat(userText(model.prompts.get(1))).contains("previous reminder had these problems");
    }

    @Test
    void draftThatStaysWrong_isReplacedByTheSafeTemplate() {
        model.respondWith(prompt -> text("{\"subject\":\"Hi\",\"message\":\"Visit https://example.com [Your Name]\"}"));

        RunReport report = agent.run("manual");

        assertThat(report.reminders()).allSatisfy(r -> {
            assertThat(r.writtenBy()).startsWith("TEMPLATE");
            assertThat(r.message()).doesNotContain("http").contains("appointment #");
            assertThat(r.delivery()).isEqualTo("SENT");
        });
    }

    @Test
    void geminiDown_stillSendsTheTemplateReminder() {
        model.respondWith(prompt -> { throw new IllegalStateException("503 from Gemini"); });

        List<ReminderResult> reminders = agent.run("manual").reminders();

        assertThat(reminders).hasSize(2).allSatisfy(r -> assertThat(r.writtenBy()).startsWith("TEMPLATE"));
    }

    /** What a well-behaved model would write, using the facts given in the prompt. */
    private static String goodDraft(Prompt prompt) {
        String facts = userText(prompt);
        String student = field(facts, "Student").split(" ")[0];
        String tutor = field(facts, "Tutor");
        String when = field(facts, "Day and start time");
        String note = field(facts, "Student's note");
        return "{\"subject\":\"Your session on " + when + "\",\"message\":\"Hi " + student + ", see you on "
                + when + " with " + tutor + ". Bring your questions about " + note
                + ". Please cancel at least 12 hours ahead if you can't make it.\"}";
    }

    private static String field(String text, String name) {
        Matcher m = Pattern.compile(Pattern.quote(name) + ": (.*)").matcher(text);
        return m.find() ? m.group(1).strip().replace("\"", "") : "";
    }

    private static String userText(Prompt prompt) {
        return prompt.getInstructions().stream().filter(m -> m.getMessageType() == MessageType.USER)
                .map(m -> m.getText()).reduce("", String::concat);
    }
}
