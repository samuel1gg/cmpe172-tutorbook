package edu.sjsu.cmpe172.tutorbook.exception;

/**
 * The request conflicts with the current state, e.g. the slot was just booked
 * by someone else or the appointment is already cancelled. Mapped to 409.
 */
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message) {
        super(message);
    }
}
