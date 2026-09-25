package edu.sjsu.cmpe172.tutorbook.model;

/** One row of the providers table. */
public record Provider(
        Long providerId,
        Long userId,
        String displayName,
        String specialty,
        String bio) {
}
