package edu.sjsu.cmpe172.tutorbook.ai;

import static edu.sjsu.cmpe172.tutorbook.ai.FakeAiConfig.ScriptedChatModel.text;
import static edu.sjsu.cmpe172.tutorbook.ai.FakeAiConfig.ScriptedChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import edu.sjsu.cmpe172.tutorbook.ai.FakeAiConfig.ScriptedChatModel;
import edu.sjsu.cmpe172.tutorbook.ai.agent.BookingChatAgent;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/**
 * Agent 1 end to end, with the LLM replaced by a script: the REAL ChatClient,
 * chat memory, tool-calling loop, tools and services all run. Shows that
 *  - a multi-step request (check availability -> stage booking) works,
 *  - nothing is written until the user confirms in a later turn,
 *  - even a misbehaving model that tries to book without asking cannot.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-agent-test;DB_CLOSE_DELAY=-1",
        "spring.ai.google.genai.api-key=test-key-not-used",
        "spring.ai.google.genai.embedding.api-key=test-key-not-used"})
@Import(FakeAiConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingChatAgentTest {

    static final AppUser ALICE = new AppUser(1, "alice@student.sjsu.edu", "x", "Alice Nguyen", "CUSTOMER", null);

    @Autowired
    BookingChatAgent agent;

    @Autowired
    ScriptedChatModel model;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanConversation() {
        agent.reset("c1");
        agent.reset("c2");
    }

    @Test
    void earliestSlotRequest_isStagedThenBookedOnlyAfterYes() {
        // Turn 1: "find the earliest calculus slot and book it"
        AtomicInteger step = new AtomicInteger();
        model.respondWith(prompt -> switch (step.getAndIncrement()) {
            case 0 -> toolCall("checkAvailability", "{\"serviceId\": 1}");
            case 1 -> {
                // the tool result (real data from SlotService) is now in the prompt
                assertThat(lastToolResult(prompt.getInstructions())).contains("\"slotId\":1");
                yield toolCall("bookAppointment", "{\"slotId\": 1}");
            }
            default -> text("The earliest is Calculus Tutoring with Dr. Priya Patel. Shall I go ahead?");
        });

        String reply1 = agent.chat(ALICE, "c1", "Find the earliest calculus slot this week and book it");

        assertThat(reply1).contains("Shall I go ahead?");
        assertThat(bookedCount(1)).isZero();                      // nothing written yet

        // Turn 2: "yes"
        AtomicInteger step2 = new AtomicInteger();
        model.respondWith(prompt -> step2.getAndIncrement() == 0
                ? toolCall("confirmPendingAction", "{}")
                : text("Done! " + lastToolResult(prompt.getInstructions())));

        String reply2 = agent.chat(ALICE, "c1", "yes please");

        assertThat(reply2).contains("BOOKED: appointment #");
        assertThat(bookedCount(1)).isEqualTo(1);
        // multi-turn memory: turn 2's prompt still contains turn 1's user message
        assertThat(model.prompts.get(0).getInstructions())
                .anyMatch(m -> m.getMessageType() == MessageType.USER && m.getText().contains("earliest calculus"));
    }

    @Test
    void modelThatTriesToBookWithoutAsking_isStoppedByTheConfirmGuard() {
        AtomicInteger step = new AtomicInteger();
        model.respondWith(prompt -> switch (step.getAndIncrement()) {
            case 0 -> toolCall("bookAppointment", "{\"slotId\": 7}");
            case 1 -> toolCall("confirmPendingAction", "{}");     // same turn: not allowed
            default -> text(lastToolResult(prompt.getInstructions()));
        });

        String reply = agent.chat(ALICE, "c2", "book slot 7");

        assertThat(reply).contains("REFUSED: the user has not confirmed yet");
        assertThat(bookedCount(7)).isZero();
    }

    private static String lastToolResult(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolResponseMessage t) {
                return t.getResponses().get(0).responseData();
            }
            if (messages.get(i) instanceof AssistantMessage) {
                break;
            }
        }
        return "";
    }

    private long bookedCount(long slotId) {
        return jdbc.sql("SELECT COUNT(*) FROM appointments WHERE slot_id = :s AND status = 'BOOKED'")
                .param("s", slotId).query(Long.class).single();
    }
}
