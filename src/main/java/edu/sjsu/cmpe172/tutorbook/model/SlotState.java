package edu.sjsu.cmpe172.tutorbook.model;

import java.time.LocalDateTime;

/**
 * What the booking transaction needs to know about a slot: who owns it, when
 * it starts, whether it was removed or is already booked, and the version
 * number used for the optimistic-lock check.
 */
public record SlotState(
        long slotId,
        long providerId,
        LocalDateTime startTime,
        boolean removed,
        boolean booked,
        int version) {
}
