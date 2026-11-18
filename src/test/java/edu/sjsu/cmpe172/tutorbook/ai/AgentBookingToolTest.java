package edu.sjsu.cmpe172.tutorbook.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import edu.sjsu.cmpe172.tutorbook.ai.agent.BookingAgentTools;
import edu.sjsu.cmpe172.tutorbook.ai.agent.PendingActionStore;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import edu.sjsu.cmpe172.tutorbook.service.BookingService;
import edu.sjsu.cmpe172.tutorbook.service.BookingTransaction;

/**
 * REQUIRED TEST (Milestone 4): the agent's book tool goes through the
 * transactional path, so double-booking is still blocked when the AGENT books.
 *
 * The tools are called exactly as the ChatClient calls them (with a ToolContext
 * holding the logged-in user and the conversation id). Not @Transactional: every
 * booking commits like a real request; own database + @DirtiesContext.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agent-tool-test;DB_CLOSE_DELAY=-1")
@Import(FakeAiConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AgentBookingToolTest {

    static final String HASH = "$2a$10$Lg17hpQnSXplXPux.iuMsetdCeSUazz2qqS85Y00aDRSLR4anCoJe";
    static final AppUser ALICE = new AppUser(1, "alice@student.sjsu.edu", HASH, "Alice Nguyen", "CUSTOMER", null);
    static final AppUser BOB = new AppUser(2, "bob@student.sjsu.edu", HASH, "Bob Martinez", "CUSTOMER", null);
    static final AppUser DR_PATEL = new AppUser(4, "dr.patel@tutorbook.edu", HASH, "Priya Patel", "PROVIDER", 1L);

    @Autowired
    BookingAgentTools tools;

    @Autowired
    PendingActionStore pendingActions;

    @Autowired
    BookingService bookingService;

    @MockitoSpyBean
    BookingTransaction bookingTransaction;

    @Autowired
    JdbcClient jdbc;

    @Test
    void agentBooking_goesThroughBookingTransaction_andDoubleBookingIsBlocked() {
        long slot = 1;
        String bobChat = "bob-chat-1";

        // Turn 1: Bob asks the agent for slot 1; the tool only stages it.
        pendingActions.nextTurn(bobChat);
        String staged = tools.bookAppointment(slot, "limits", ctx(BOB, bobChat));
        assertThat(staged).startsWith("PENDING CONFIRMATION");
        assertThat(bookedCount(slot)).isZero();

        // Meanwhile Alice books the same slot on the website.
        long aliceAppointment = bookingService.book(ALICE.userId(), slot, null);

        // Turn 2: Bob says "yes" and the agent confirms.
        pendingActions.nextTurn(bobChat);
        String result = tools.confirmPendingAction(ctx(BOB, bobChat));

        // The write went through the same @Transactional BookingTransaction as the website...
        verify(bookingTransaction, atLeastOnce()).tryBook(eq(BOB.userId()), eq(slot), any());
        // ...whose double-booking guard rejected it, and the agent offers alternatives.
        assertThat(result).startsWith("NOT BOOKED").contains("alternatives");
        assertThat(bookedCount(slot)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT customer_id FROM appointments WHERE slot_id = :s AND status = 'BOOKED'")
                .param("s", slot).query(Long.class).single()).isEqualTo(ALICE.userId());
        assertThat(aliceAppointment).isPositive();
    }

    @Test
    void twoAgentsConfirmingTheSameSlotAtOnce_exactlyOneBooks() throws Exception {
        long slot = 3;
        List<AppUser> users = List.of(ALICE, BOB);
        for (AppUser u : users) {
            String chat = "race-" + u.userId();
            pendingActions.nextTurn(chat);
            assertThat(tools.bookAppointment(slot, null, ctx(u, chat))).startsWith("PENDING CONFIRMATION");
            pendingActions.nextTurn(chat);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (AppUser u : users) {
            results.add(pool.submit(() -> {
                go.await();
                return tools.confirmPendingAction(ctx(u, "race-" + u.userId()));
            }));
        }
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(r -> r.startsWith("BOOKED")).hasSize(1);
        assertThat(outcomes).filteredOn(r -> r.startsWith("NOT BOOKED")).hasSize(1);
        assertThat(bookedCount(slot)).isEqualTo(1);
    }

    @Test
    void confirmingInTheSameTurnAsStaging_isRefused() {
        long slot = 4;
        String chat = "same-turn";
        pendingActions.nextTurn(chat);
        tools.bookAppointment(slot, null, ctx(ALICE, chat));

        String result = tools.confirmPendingAction(ctx(ALICE, chat));   // no new user message

        assertThat(result).startsWith("REFUSED");
        assertThat(bookedCount(slot)).isZero();
        verify(bookingTransaction, org.mockito.Mockito.never()).tryBook(anyLong(), eq(slot), any());
    }

    @Test
    void confirmWithoutAStagedAction_doesNothing() {
        String chat = "nothing-staged";
        pendingActions.nextTurn(chat);
        assertThat(tools.confirmPendingAction(ctx(ALICE, chat))).startsWith("NOTHING TO CONFIRM");
    }

    @Test
    void providerAccount_cannotBookThroughTheAgent() {
        String chat = "provider";
        pendingActions.nextTurn(chat);
        assertThat(tools.bookAppointment(6, null, ctx(DR_PATEL, chat))).startsWith("NOT ALLOWED");
        assertThat(bookedCount(6)).isZero();
    }

    @Test
    void agentCannotCancelSomeoneElsesAppointment() {
        String chat = "bob-cancel";
        pendingActions.nextTurn(chat);
        // appointment 1 belongs to Alice (seed.sql)
        assertThat(tools.cancelAppointment(1, ctx(BOB, chat))).startsWith("NOT ALLOWED");
        pendingActions.nextTurn(chat);
        assertThat(tools.confirmPendingAction(ctx(BOB, chat))).startsWith("NOTHING TO CONFIRM");
        assertThat(status(1)).isEqualTo("BOOKED");
    }

    @Test
    void ownerCancelsAfterConfirming() {
        String chat = "chen-cancel";
        AppUser chen = new AppUser(3, "chen@student.sjsu.edu", HASH, "Chen Li", "CUSTOMER", null);
        pendingActions.nextTurn(chat);
        assertThat(tools.cancelAppointment(3, ctx(chen, chat))).startsWith("PENDING CONFIRMATION");
        assertThat(status(3)).isEqualTo("BOOKED");

        pendingActions.nextTurn(chat);
        assertThat(tools.confirmPendingAction(ctx(chen, chat))).startsWith("CANCELLED");
        assertThat(status(3)).isEqualTo("CANCELLED");
    }

    private static ToolContext ctx(AppUser user, String conversationId) {
        return new ToolContext(Map.of(BookingAgentTools.CTX_USER, user,
                BookingAgentTools.CTX_CONVERSATION, conversationId));
    }

    private long bookedCount(long slotId) {
        return jdbc.sql("SELECT COUNT(*) FROM appointments WHERE slot_id = :s AND status = 'BOOKED'")
                .param("s", slotId).query(Long.class).single();
    }

    private String status(long appointmentId) {
        return jdbc.sql("SELECT status FROM appointments WHERE appointment_id = :id")
                .param("id", appointmentId).query(String.class).single();
    }
}
