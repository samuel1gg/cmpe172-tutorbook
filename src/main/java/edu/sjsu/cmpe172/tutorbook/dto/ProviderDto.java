package edu.sjsu.cmpe172.tutorbook.dto;

/** What the web layer sees about a provider (no user_id or other internals). */
public record ProviderDto(Long id, String name, String specialty, String bio) {
}
