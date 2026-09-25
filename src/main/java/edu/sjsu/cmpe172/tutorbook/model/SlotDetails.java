package edu.sjsu.cmpe172.tutorbook.model;

import java.time.LocalDateTime;

/**
 * An availability_slots row joined with its provider and service, exactly as
 * the SlotRepository query returns it.
 */
public record SlotDetails(
        Long slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long providerId,
        String providerName,
        Long serviceId,
        String serviceName,
        int durationMinutes,
        int priceCents) {
}
