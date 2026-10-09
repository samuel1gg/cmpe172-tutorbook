package edu.sjsu.cmpe172.tutorbook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.exception.OptimisticConflictException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;

/**
 * Books a slot, retrying when the optimistic version check fails.
 *
 * A failed version check only means "the slot changed while we were looking".
 * Retrying re-reads the slot in a NEW transaction: if the other transaction
 * booked it, the retry sees booked = true and fails fast with 409; if the
 * other transaction did something harmless, the retry succeeds.
 */
@Service
public class BookingService {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingTransaction bookingTransaction;

    public BookingService(BookingTransaction bookingTransaction) {
        this.bookingTransaction = bookingTransaction;
    }

    /** Returns the new appointment id, or throws SlotConflictException (409) / NotFoundException (404). */
    public long book(long customerId, long slotId, String notes) {
        for (int attempt = 1; ; attempt++) {
            try {
                return bookingTransaction.tryBook(customerId, slotId, notes);
            } catch (OptimisticConflictException e) {
                log.info("Version conflict booking slot {} (attempt {}/{})", slotId, attempt, MAX_ATTEMPTS);
                if (attempt >= MAX_ATTEMPTS) {
                    throw new SlotConflictException("This slot is in high demand right now. Please try again.");
                }
            }
        }
    }
}
