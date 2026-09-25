package edu.sjsu.cmpe172.tutorbook.dto;

/** What the web layer sees about a service; price is pre-formatted, e.g. "$40.00". */
public record ServiceDto(Long id, String name, String description, int durationMinutes, String price) {
}
