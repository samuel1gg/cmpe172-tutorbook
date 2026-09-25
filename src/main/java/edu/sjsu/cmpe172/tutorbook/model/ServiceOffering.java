package edu.sjsu.cmpe172.tutorbook.model;

/**
 * One row of the services table. Named ServiceOffering (not Service) so it
 * does not clash with Spring's @Service annotation.
 */
public record ServiceOffering(
        Long serviceId,
        String name,
        String description,
        int durationMinutes,
        int priceCents) {
}
