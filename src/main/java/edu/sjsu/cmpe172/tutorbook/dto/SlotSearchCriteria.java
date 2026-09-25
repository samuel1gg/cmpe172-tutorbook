package edu.sjsu.cmpe172.tutorbook.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * Optional filters for GET /slots, bound from query parameters, e.g.
 * /slots?providerId=1&serviceId=2&date=2026-09-25&page=0&size=5
 */
public record SlotSearchCriteria(
        Long providerId,
        Long serviceId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Integer page,
        Integer size) {

    public static final int DEFAULT_SIZE = 5;
    public static final int MAX_SIZE = 50;

    /** Zero-based page number; negative or missing becomes 0. */
    public int pageOrDefault() {
        return (page == null || page < 0) ? 0 : page;
    }

    /** Page size clamped to 1..MAX_SIZE. */
    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
