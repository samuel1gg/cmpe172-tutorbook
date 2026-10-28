package edu.sjsu.cmpe172.tutorbook.notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationGateway.SendResult;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Turns "appointment 12 was booked" into a delivered notification.
 *
 *   1. prepare  (short DB transaction) build the DTO and save it as PENDING
 *   2. deliver  (NO transaction)       call the remote service through the gateway
 *   3. record   (short DB transaction) mark it SENT, FAILED (retry later) or GAVE_UP
 *
 * The network call sits between two tiny transactions, never inside one.
 * Every failure is caught here (the fallback), so a notification problem can
 * never turn a successful booking into an error page.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.US);

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationGateway gateway;
    private final NotificationProperties props;
    private final MeterRegistry meters;
    private final TransactionTemplate newTransaction;

    public NotificationDispatcher(AppointmentRepository appointmentRepository,
                                  NotificationRepository notificationRepository,
                                  NotificationGateway gateway,
                                  NotificationProperties props,
                                  MeterRegistry meters,
                                  PlatformTransactionManager txManager) {
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
        this.gateway = gateway;
        this.props = props;
        this.meters = meters;
        // REQUIRES_NEW: we are called from an AFTER_COMMIT callback, where the
        // finished booking transaction is still bound to the thread.
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Called after a booking or cancel has committed. Never throws. */
    public void notify(long appointmentId, NotificationType type) {
        try {
            Optional<NotificationRequest> request = newTransaction.execute(s -> prepare(appointmentId, type));
            if (request != null && request.isPresent()) {
                deliver(request.get(), 0);
            }
        } catch (RuntimeException e) {
            log.error("Could not create {} notification for appointment {}", type, appointmentId, e);
            count(type, "error");
        }
    }

    /** Called by NotificationRetryJob for a FAILED row. Never throws. */
    public void retry(NotificationRecord failed) {
        try {
            if (notificationRepository.claimForRetry(failed.idempotencyKey())) {
                log.info("Retrying notification {} (attempts so far: {})", failed.idempotencyKey(), failed.attempts());
                deliver(failed.toRequest(), failed.attempts());
            }
        } catch (RuntimeException e) {
            log.error("Retry of notification {} crashed", failed.idempotencyKey(), e);
        }
    }

    private Optional<NotificationRequest> prepare(long appointmentId, NotificationType type) {
        AppointmentDetails a = appointmentRepository.findDetailsById(appointmentId).orElse(null);
        if (a == null) {
            log.warn("Appointment {} vanished before its {} notification", appointmentId, type);
            return Optional.empty();
        }
        NotificationRequest request = build(a, type);
        if (!notificationRepository.insertPending(request)) {
            log.info("Notification {} already exists; not sending it twice", request.idempotencyKey());
            return Optional.empty();
        }
        return Optional.of(request);
    }

    private void deliver(NotificationRequest request, int attemptsSoFar) {
        SendResult result = gateway.send(request);
        String key = request.idempotencyKey();
        if (result.delivered()) {
            newTransaction.executeWithoutResult(s -> notificationRepository.markSent(key, result.attempts()));
            log.info("{} for appointment {} sent to {}", request.type(), request.appointmentId(), mask(request.recipient()));
            count(request.type(), "sent");
            return;
        }
        int total = attemptsSoFar + result.attempts();
        boolean giveUp = !result.retryableLater() || total >= props.maxTotalAttempts();
        String status = giveUp ? "GAVE_UP" : "FAILED";
        newTransaction.executeWithoutResult(s -> notificationRepository.markFailed(key, result.attempts(), status, result.error()));
        if (giveUp) {
            log.error("Gave up on notification {} after {} attempts ({})", key, total, result.error());
            count(request.type(), "gave_up");
        } else {
            log.warn("Notification {} saved as FAILED after {} attempts ({}); NotificationRetryJob will retry it",
                    key, total, result.error());
            count(request.type(), "failed");
        }
    }

    /** Builds the coarse-grained DTO: everything the remote service needs, in one object. */
    static NotificationRequest build(AppointmentDetails a, NotificationType type) {
        String when = a.startTime().format(WHEN);
        String subject;
        String message;
        if (type == NotificationType.BOOKING_CANCELLED) {
            subject = "Cancelled: " + a.serviceName() + " on " + when;
            message = "Hi " + a.customerName() + ", your " + a.serviceName() + " session with "
                    + a.providerName() + " on " + when + " (appointment #" + a.appointmentId()
                    + ") has been cancelled.";
        } else {
            subject = "Confirmed: " + a.serviceName() + " on " + when;
            message = "Hi " + a.customerName() + ", your " + a.serviceName() + " session with "
                    + a.providerName() + " on " + when + " is confirmed. Appointment #"
                    + a.appointmentId() + ".";
        }
        String key = "appointment-" + a.appointmentId() + "-" + type.name();
        return new NotificationRequest(key, a.appointmentId(), a.customerEmail(), type, subject, message);
    }

    /** alice@student.sjsu.edu -> a***@student.sjsu.edu (no full emails in the logs). */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }

    private void count(NotificationType type, String outcome) {
        meters.counter("tutorbook.notifications", "type", type.name(), "outcome", outcome).increment();
    }
}
