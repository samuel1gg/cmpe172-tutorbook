package edu.sjsu.cmpe172.tutorbook.dto;

import java.time.LocalDateTime;

/** One row of the provider's "My slots" table. */
public record ProviderSlotDto(
        Long id,
        LocalDateTime start,
        LocalDateTime end,
        String serviceName,
        boolean booked,
        String bookedBy) {
}
