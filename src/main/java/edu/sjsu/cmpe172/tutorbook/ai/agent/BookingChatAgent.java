package edu.sjsu.cmpe172.tutorbook.ai.agent;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.ai.AiStatus;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/**
 * AGENT 1: the conversational (reactive) booking assistant.
 *
 * One call = one user message. Spring AI's ChatClient sends the message, the
 * system prompt, the conversation so far (MessageChatMemoryAdvisor) and the
 * tool definitions to Gemini. When Gemini answers with a tool call, the
 * ChatClient runs that @Tool method and sends the result back, looping until
 * Gemini produces a normal answer. That loop is what makes multi-step requests
 * work, e.g. "find the earliest slot this week and book it":
 *   checkAvailability -> pick earliest -> bookAppointment (stage) -> ask user.
 *
 * The logged-in AppUser and the conversation id travel in the ToolContext.
 * They are given to the tools by our code, never by the model.
 */
@Service
public class BookingChatAgent {

    private static final Logger log = LoggerFactory.getLogger(BookingChatAgent.class);
    private static final DateTimeFormatter NOW =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, h:mm a", Locale.US);

    static final String SYSTEM_PROMPT = """
            You are the TutorBook booking assistant for an SJSU tutoring center.
            You are talking to {name}, who is logged in as a {role}. Right now it is {now}.

            What you can do, only through your tools:
            - show services and tutors, check open slots, list the user's appointments,
            - book a slot or cancel one of the user's appointments,
            - answer policy questions using searchPolicies (never invent a policy).

            Rules:
            1. Always use tools for facts about slots, appointments and policies. Never make up
               slot ids, times, prices or appointment ids.
            2. Booking and cancelling are two steps. First call bookAppointment or
               cancelAppointment, show the user the summary, and ask "Shall I go ahead?".
               Call confirmPendingAction only after the user clearly says yes in a later message.
               If they say no or change their mind, call discardPendingAction.
            3. "Earliest", "this week", "tomorrow", "next Tuesday" are relative to the date above.
               For "earliest" requests call checkAvailability and pick the first matching slot.
            4. If a slot is taken or a write fails, say so plainly and offer the alternatives
               the tool returned.
            5. If a request is ambiguous (which subject? which appointment?), ask a short
               clarifying question instead of guessing.
            6. Keep answers short. Show times exactly as the tools return them.
            7. Reply in the same language the user writes in.
            """;

    private final ChatClient chatClient;
    private final BookingAgentTools tools;
    private final ChatMemory chatMemory;
    private final PendingActionStore pendingActions;
    private final AiStatus aiStatus;
    private final Clock clock;

    public BookingChatAgent(ChatClient.Builder builder, BookingAgentTools tools, ChatMemory chatMemory,
                            PendingActionStore pendingActions, AiStatus aiStatus, Clock clock) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.tools = tools;
        this.chatMemory = chatMemory;
        this.pendingActions = pendingActions;
        this.aiStatus = aiStatus;
        this.clock = clock;
    }

    /** The conversation is per user AND per browser session. */
    public static String conversationId(AppUser user, String sessionId) {
        return "user-" + user.userId() + "-" + sessionId;
    }

    public String chat(AppUser user, String conversationId, String message) {
        if (!aiStatus.isConfigured()) {
            return AiStatus.NOT_CONFIGURED_MESSAGE;
        }
        int turn = pendingActions.nextTurn(conversationId);
        log.info("Agent turn {} for {}", turn, conversationId);
        try {
            return chatClient.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT)
                            .param("name", user.fullName())
                            .param("role", user.role().toLowerCase())
                            .param("now", LocalDateTime.now(clock).format(NOW)))
                    .user(message)
                    .tools(tools)
                    .toolContext(Map.of(
                            BookingAgentTools.CTX_USER, user,
                            BookingAgentTools.CTX_CONVERSATION, conversationId))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
        } catch (RuntimeException e) {
            // Gemini down, rate limit (free tier), bad key... never a stack trace to the user.
            log.warn("Agent call failed for {}: {}", conversationId, e.toString());
            return "Sorry, the assistant is unavailable right now (" + shortReason(e)
                    + "). Nothing was booked or cancelled. You can still use the Available Slots page.";
        }
    }

    /** "New conversation": forget the messages and any unconfirmed action. */
    public void reset(String conversationId) {
        chatMemory.clear(conversationId);
        pendingActions.reset(conversationId);
    }

    static String shortReason(RuntimeException e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("429") || m.toLowerCase().contains("quota") || m.toLowerCase().contains("exhausted")) {
            return "the free Gemini quota is used up for now; try again in a minute";
        }
        if (m.contains("API key") || m.contains("401") || m.contains("403")) {
            return "the Gemini API key was rejected";
        }
        return "the AI service did not respond";
    }
}
