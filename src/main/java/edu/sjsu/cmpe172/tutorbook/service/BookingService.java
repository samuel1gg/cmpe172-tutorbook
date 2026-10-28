package edu.sjsu.cmpe172.tutorbook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.OptimisticConflictException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Books a slot, retrying when the optimistic version check fails.
 *
 * A failed version check only means "the slot changed while we were looking".
 * Retrying re-reads the slot in a NEW transaction: if the other transaction
 * booked it, the retry sees booked = true and fails fast with 409; if the
 * other transaction did something harmless, the retry succeeds.
 *
 * Metrics: tutorbook.bookings{outcome} and tutorbook.booking.latency.
 * The confirmation notification is sent after the transaction commits
 * (see AppointmentNotifier), so it is not part of this method.
 */
@Service
public class BookingService {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingTransaction bookingTransaction;
    private final MeterRegistry meters;
    private final Timer latency;

    public BookingService(BookingTransaction bookingTransaction, MeterRegistry meters) {
        this.bookingTransaction = bookingTransaction;
        this.meters = meters;
        this.latency = Timer.builder("tutorbook.booking.latency")
                .description("Time to book a slot, including retries and the confirmation notification")
                .register(meters);
    }

    /** Returns the new appointment id, or throws SlotConflictException (409) / NotFoundException (404). */
    public long book(long customerId, long slotId, String notes) {
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        try {
            long appointmentId = bookWithRetry(customerId, slotId, notes);
            outcome = "success";
            log.info("Booked appointment {} (slot {}, customer {})", appointmentId, slotId, customerId);
            return appointmentId;
        } catch (SlotConflictException e) {
            outcome = "conflict";
            log.info("Booking slot {} by customer {} rejected: {}", slotId, customerId, e.getMessage());
            throw e;
        } catch (NotFoundException e) {
            outcome = "not_found";
            throw e;
        } finally {
            sample.stop(latency);
            meters.counter("tutorbook.bookings", "outcome", outcome).increment();
        }
    }

    private long bookWithRetry(long customerId, long slotId, String notes) {
        for (int attempt = 1; ; attempt++) {
            try {
                return bookingTransaction.tryBook(customerId, slotId, notes);
            } catch (OptimisticConflictException e) {
                log.info("Version conflict booking slot {} (attempt {}/{})", slotId, attempt, MAX_ATTEMPTS);
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Slot {} still contended after {} attempts; returning 409", slotId, MAX_ATTEMPTS);
                    throw new SlotConflictException("This slot is in high demand right now. Please try again.");
                }
            }
        }
    }
}
