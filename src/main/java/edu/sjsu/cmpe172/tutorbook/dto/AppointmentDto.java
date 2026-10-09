package edu.sjsu.cmpe172.tutorbook.dto;

import java.time.LocalDateTime;

/** An appointment as shown on the confirmation, "my appointments" and provider pages. */
public record AppointmentDto(
        Long id,
        String status,
        String customerName,
        String customerEmail,
        String providerName,
        String serviceName,
        LocalDateTime start,
        LocalDateTime end,
        String price,
        String notes,
        boolean cancellable) {
}
