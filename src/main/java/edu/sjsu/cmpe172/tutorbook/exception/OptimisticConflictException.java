package edu.sjsu.cmpe172.tutorbook.exception;

/**
 * Internal signal: the slot's version changed between our read and our update,
 * so another transaction got there first. BookingService catches this and
 * retries; it never reaches the client.
 */
public class OptimisticConflictException extends RuntimeException {

    public OptimisticConflictException(long slotId) {
        super("Slot " + slotId + " was modified concurrently");
    }
}
