package edu.sjsu.cmpe172.tutorbook.ai.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * CONFIRM-BEFORE-WRITE, enforced in code (not just asked for in the prompt).
 *
 * The agent can never book or cancel in one step. The bookAppointment and
 * cancelAppointment tools only STAGE a PendingAction for the conversation and
 * return a summary. The write happens in confirmPendingAction, which refuses
 * unless:
 *   - there is a staged action for this conversation,
 *   - it was staged in an EARLIER user turn (so the user has seen the summary
 *     and answered it; the model cannot stage + confirm in the same turn), and
 *   - it is not older than 10 minutes.
 *
 * Every user message starts a new turn (nextTurn). State is in memory and per
 * conversation, like the chat memory.
 */
@Component
public class PendingActionStore {

    static final Duration EXPIRES_AFTER = Duration.ofMinutes(10);

    public enum Kind { BOOK, CANCEL }

    /** A write the agent has proposed but the user has not confirmed yet. */
    public record PendingAction(Kind kind, long targetId, Long serviceId, String notes, String summary,
                                int stagedInTurn, Instant stagedAt) {
    }

    private final Map<String, Integer> turns = new ConcurrentHashMap<>();
    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingActionStore(Clock clock) {
        this.clock = clock;
    }

    /** Called once per user message; returns the new turn number. */
    public int nextTurn(String conversationId) {
        return turns.merge(conversationId, 1, Integer::sum);
    }

    public int currentTurn(String conversationId) {
        return turns.getOrDefault(conversationId, 0);
    }

    /** Stage a new action; it replaces any earlier unconfirmed one. */
    public PendingAction stage(String conversationId, Kind kind, long targetId, Long serviceId,
                               String notes, String summary) {
        PendingAction action = new PendingAction(kind, targetId, serviceId, notes, summary,
                currentTurn(conversationId), clock.instant());
        pending.put(conversationId, action);
        return action;
    }

    public Optional<PendingAction> get(String conversationId) {
        PendingAction action = pending.get(conversationId);
        if (action != null && action.stagedAt().plus(EXPIRES_AFTER).isBefore(clock.instant())) {
            pending.remove(conversationId, action);
            return Optional.empty();
        }
        return Optional.ofNullable(action);
    }

    /** Remove the action only if it is still the one we looked at (two confirms cannot both run it). */
    public boolean take(String conversationId, PendingAction action) {
        return pending.remove(conversationId, action);
    }

    public void discard(String conversationId) {
        pending.remove(conversationId);
    }

    public void reset(String conversationId) {
        pending.remove(conversationId);
        turns.remove(conversationId);
    }
}
