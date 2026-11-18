package edu.sjsu.cmpe172.tutorbook.ai.agent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import edu.sjsu.cmpe172.tutorbook.ai.agent.PendingActionStore.Kind;
import edu.sjsu.cmpe172.tutorbook.ai.agent.PendingActionStore.PendingAction;
import edu.sjsu.cmpe172.tutorbook.ai.rag.KnowledgeBase;
import edu.sjsu.cmpe172.tutorbook.dto.AppointmentDto;
import edu.sjsu.cmpe172.tutorbook.dto.AppointmentListDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.exception.ForbiddenException;
import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import edu.sjsu.cmpe172.tutorbook.service.AppointmentService;
import edu.sjsu.cmpe172.tutorbook.service.BookingService;
import edu.sjsu.cmpe172.tutorbook.service.CatalogService;
import edu.sjsu.cmpe172.tutorbook.service.SlotService;

/**
 * The tools Agent 1 (the booking assistant) can call.
 *
 * Every tool is a thin wrapper around the EXISTING service layer, the same
 * methods the web pages use. The agent never touches a repository or SQL, so:
 *   - bookings still go through BookingService -> BookingTransaction
 *     (optimistic version check + UNIQUE(active_slot_id) double-booking guard),
 *   - cancels still go through AppointmentService.cancel (owner-only check),
 *   - the agent acts as the logged-in user: the AppUser comes from the HTTP
 *     session via the ToolContext, never from the model, so the LLM cannot
 *     pick a different user id.
 *
 * Writes are two-step (see PendingActionStore): bookAppointment and
 * cancelAppointment only stage the action; confirmPendingAction performs it
 * after the user has confirmed in a later message.
 *
 * Tools return short text or small records. Errors are returned as text the
 * model can explain to the user (with alternatives), instead of exceptions.
 */
@Component
public class BookingAgentTools {

    public static final String CTX_USER = "user";
    public static final String CTX_CONVERSATION = "conversationId";

    static final int MAX_SLOTS_RETURNED = 15;
    private static final Logger log = LoggerFactory.getLogger(BookingAgentTools.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE MMM d, yyyy 'at' h:mm a", Locale.US);

    private final SlotService slotService;
    private final CatalogService catalogService;
    private final BookingService bookingService;
    private final AppointmentService appointmentService;
    private final PendingActionStore pendingActions;
    private final KnowledgeBase knowledgeBase;

    public BookingAgentTools(SlotService slotService, CatalogService catalogService,
                             BookingService bookingService, AppointmentService appointmentService,
                             PendingActionStore pendingActions, KnowledgeBase knowledgeBase) {
        this.slotService = slotService;
        this.catalogService = catalogService;
        this.bookingService = bookingService;
        this.appointmentService = appointmentService;
        this.pendingActions = pendingActions;
        this.knowledgeBase = knowledgeBase;
    }

    /** What the model sees for a slot (times pre-formatted so it cannot misread them). */
    public record SlotInfo(long slotId, String when, String provider, String service,
                           int minutes, String price) {
    }

    /** What the model sees for an appointment. */
    public record AppointmentInfo(long appointmentId, String status, String when, String provider,
                                  String service, String price, boolean cancellable) {
    }

    // ------------------------------------------------------------------
    // Read tools
    // ------------------------------------------------------------------

    @Tool(description = """
            List the tutoring services (subjects, length, price) and the tutors (providers) with their ids.
            Use it to turn a subject or tutor name into a serviceId or providerId.""")
    public String listServicesAndTutors() {
        StringBuilder sb = new StringBuilder("Services:\n");
        catalogService.getServices().forEach(s -> sb.append("- serviceId ").append(s.id()).append(": ")
                .append(s.name()).append(" (").append(s.durationMinutes()).append(" min, ").append(s.price())
                .append(") ").append(s.description()).append('\n'));
        sb.append("Tutors:\n");
        catalogService.getProviders().forEach(p -> sb.append("- providerId ").append(p.id()).append(": ")
                .append(p.name()).append(", ").append(p.specialty()).append('\n'));
        return sb.toString();
    }

    @Tool(description = """
            Check availability: list open (bookable) slots, earliest first. All filters are optional.
            Call it with no date to see every upcoming open slot, then pick the ones that match.""")
    public List<SlotInfo> checkAvailability(
            @ToolParam(required = false, description = "Only this day, format YYYY-MM-DD") String date,
            @ToolParam(required = false, description = "Only this tutor (providerId)") Long providerId,
            @ToolParam(required = false, description = "Only this service (serviceId)") Long serviceId) {
        LocalDate day = parseDate(date);
        var criteria = new SlotSearchCriteria(providerId, serviceId, day, 0, MAX_SLOTS_RETURNED);
        return slotService.findAvailableSlots(criteria).items().stream().map(BookingAgentTools::toInfo).toList();
    }

    @Tool(description = "List the logged-in user's appointments: upcoming ones and history (cancelled/completed).")
    public String listMyAppointments(ToolContext context) {
        AppUser user = user(context);
        AppointmentListDto list = user.isProvider() && user.providerId() != null
                ? appointmentService.getProviderAppointments(user.providerId())
                : appointmentService.getCustomerAppointments(user.userId());
        return "Upcoming: " + list.upcoming().stream().map(BookingAgentTools::toInfo).toList()
                + "\nHistory: " + list.history().stream().map(BookingAgentTools::toInfo).toList();
    }

    @Tool(description = """
            Search the TutorBook policy and FAQ knowledge base (hours, cancellation policy, rules, what to bring).
            Use it for any question about policies instead of guessing.""")
    public String searchPolicies(@ToolParam(description = "The question to look up") String question) {
        return knowledgeBase.searchAsText(question);
    }

    // ------------------------------------------------------------------
    // Write tools (stage, then confirm)
    // ------------------------------------------------------------------

    @Tool(description = """
            STEP 1 of booking. Prepares a booking of one slot and returns a summary. It does NOT book yet.
            Show the summary to the user and ask them to confirm. Only after the user confirms in their
            next message, call confirmPendingAction.""")
    public String bookAppointment(
            @ToolParam(description = "The slotId to book (from checkAvailability)") long slotId,
            @ToolParam(required = false, description = "Optional note for the tutor") String notes,
            ToolContext context) {
        AppUser user = user(context);
        if (!user.isCustomer()) {
            return "NOT ALLOWED: only customer accounts can book appointments. You are logged in as "
                    + user.role().toLowerCase() + ".";
        }
        SlotDto slot;
        try {
            slot = slotService.getAvailableSlot(slotId);
        } catch (NotFoundException e) {
            return "UNAVAILABLE: slot " + slotId + " is not open (already booked or does not exist). "
                    + alternatives(null, slotId);
        }
        String summary = "Book " + slot.serviceName() + " with " + slot.providerName() + " on "
                + slot.start().format(WHEN) + " (" + slot.durationMinutes() + " min, " + slot.price() + ")"
                + (notes == null || notes.isBlank() ? "" : ", note: \"" + notes.strip() + "\"");
        pendingActions.stage(conversation(context), Kind.BOOK, slotId, slot.serviceId(), clean(notes), summary);
        return "PENDING CONFIRMATION (nothing booked yet): " + summary
                + ". Ask the user to confirm (yes/no).";
    }

    @Tool(description = """
            STEP 1 of cancelling. Prepares the cancellation of one of the user's appointments and returns a
            summary. It does NOT cancel yet. Ask the user to confirm; only after they confirm in their next
            message, call confirmPendingAction.""")
    public String cancelAppointment(
            @ToolParam(description = "The appointmentId to cancel (from listMyAppointments)") long appointmentId,
            ToolContext context) {
        AppUser user = user(context);
        AppointmentDto a;
        try {
            a = appointmentService.getAppointment(user, appointmentId);   // ownership check
        } catch (NotFoundException | ForbiddenException e) {
            return "NOT ALLOWED: appointment " + appointmentId + " is not one of your appointments.";
        }
        if (!a.cancellable()) {
            return "CANNOT CANCEL: appointment " + appointmentId + " is " + a.status().toLowerCase()
                    + " or has already started.";
        }
        String summary = "Cancel appointment #" + appointmentId + ": " + a.serviceName() + " with "
                + a.providerName() + " on " + a.start().format(WHEN);
        pendingActions.stage(conversation(context), Kind.CANCEL, appointmentId, null, null, summary);
        return "PENDING CONFIRMATION (nothing cancelled yet): " + summary
                + ". Ask the user to confirm (yes/no).";
    }

    @Tool(description = """
            STEP 2. Performs the booking or cancellation that was prepared earlier, ONLY after the user has
            clearly said yes to the summary in their latest message. Never call it in the same turn as
            bookAppointment or cancelAppointment.""")
    public String confirmPendingAction(ToolContext context) {
        AppUser user = user(context);
        String conversationId = conversation(context);
        Optional<PendingAction> staged = pendingActions.get(conversationId);
        if (staged.isEmpty()) {
            return "NOTHING TO CONFIRM: no booking or cancellation is waiting for confirmation.";
        }
        PendingAction action = staged.get();
        if (action.stagedInTurn() >= pendingActions.currentTurn(conversationId)) {
            log.info("Agent tried to confirm in the same turn it staged ({}); refused", action.summary());
            return "REFUSED: the user has not confirmed yet. Show this summary and ask them to confirm: "
                    + action.summary();
        }
        if (!pendingActions.take(conversationId, action)) {
            return "NOTHING TO CONFIRM: this action was already handled.";
        }
        return action.kind() == Kind.BOOK ? doBook(user, action) : doCancel(user, action);
    }

    @Tool(description = "Discard the prepared booking or cancellation when the user says no or changes their mind.")
    public String discardPendingAction(ToolContext context) {
        pendingActions.discard(conversation(context));
        return "Discarded. Nothing was booked or cancelled.";
    }

    // ------------------------------------------------------------------

    /** The actual write: the same transactional path as the "Book" button. */
    private String doBook(AppUser user, PendingAction action) {
        try {
            long appointmentId = bookingService.book(user.userId(), action.targetId(), action.notes());
            log.info("Agent booked appointment {} for user {}", appointmentId, user.userId());
            return "BOOKED: appointment #" + appointmentId + ". " + action.summary()
                    + ". A confirmation notification is being sent.";
        } catch (SlotConflictException | NotFoundException e) {
            // e.g. someone booked the slot between the summary and the confirmation:
            // the double-booking guard in BookingTransaction rejected it.
            log.info("Agent booking of slot {} rejected: {}", action.targetId(), e.getMessage());
            return "NOT BOOKED: " + e.getMessage() + " " + alternatives(action.serviceId(), action.targetId());
        }
    }

    private String doCancel(AppUser user, PendingAction action) {
        try {
            appointmentService.cancel(user, action.targetId());
            return "CANCELLED: appointment #" + action.targetId() + ".";
        } catch (SlotConflictException | NotFoundException | ForbiddenException e) {
            return "NOT CANCELLED: " + e.getMessage();
        }
    }

    /** Up to 3 open slots to offer instead, earliest first. */
    private String alternatives(Long serviceId, Long excludeSlotId) {
        var criteria = new SlotSearchCriteria(null, serviceId, null, 0, 10);
        List<SlotInfo> options = slotService.findAvailableSlots(criteria).items().stream()
                .filter(s -> excludeSlotId == null || !s.id().equals(excludeSlotId))
                .limit(3).map(BookingAgentTools::toInfo).toList();
        return options.isEmpty() ? "There are no other open slots right now."
                : "Offer these open alternatives instead: " + options;
    }

    private static AppUser user(ToolContext context) {
        Object user = context == null ? null : context.getContext().get(CTX_USER);
        if (user instanceof AppUser appUser) {
            return appUser;
        }
        throw new IllegalStateException("Agent tools need the logged-in user in the ToolContext");
    }

    private static String conversation(ToolContext context) {
        return String.valueOf(context.getContext().get(CTX_CONVERSATION));
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clean(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String n = notes.strip();
        return n.length() > 500 ? n.substring(0, 500) : n;
    }

    static String format(LocalDateTime t) {
        return t.format(WHEN);
    }

    private static SlotInfo toInfo(SlotDto s) {
        return new SlotInfo(s.id(), format(s.start()), s.providerName(), s.serviceName(),
                s.durationMinutes(), s.price());
    }

    private static AppointmentInfo toInfo(AppointmentDto a) {
        return new AppointmentInfo(a.id(), a.status(), format(a.start()), a.providerName(),
                a.serviceName(), a.price(), a.cancellable());
    }
}
