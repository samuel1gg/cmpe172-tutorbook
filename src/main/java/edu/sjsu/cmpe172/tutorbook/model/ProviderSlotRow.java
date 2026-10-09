package edu.sjsu.cmpe172.tutorbook.model;

import java.time.LocalDateTime;

/** One of a provider's upcoming slots, with the student who booked it (if any). */
public record ProviderSlotRow(
        long slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String serviceName,
        Long appointmentId,
        String customerName) {

    public boolean booked() {
        return appointmentId != null;
    }
}
