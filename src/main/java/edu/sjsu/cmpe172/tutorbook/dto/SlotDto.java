package edu.sjsu.cmpe172.tutorbook.dto;

import java.time.LocalDateTime;

/** An available slot as returned by GET /slots and GET /api/slots. */
public record SlotDto(
        Long id,
        LocalDateTime start,
        LocalDateTime end,
        Long providerId,
        String providerName,
        Long serviceId,
        String serviceName,
        int durationMinutes,
        String price) {
}
