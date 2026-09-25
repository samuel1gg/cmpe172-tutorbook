package edu.sjsu.cmpe172.tutorbook.model;

import java.time.LocalDateTime;

/** An appointments row joined with its customer, slot, provider and service. */
public record AppointmentDetails(
        Long appointmentId,
        String status,
        String notes,
        LocalDateTime createdAt,
        Long customerId,
        String customerName,
        String customerEmail,
        Long slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String providerName,
        String serviceName,
        int priceCents) {
}
