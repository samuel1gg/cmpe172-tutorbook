package edu.sjsu.cmpe172.tutorbook.dto;

import java.util.List;

/** Appointments split for display: upcoming (BOOKED, in the future) and history (everything else). */
public record AppointmentListDto(List<AppointmentDto> upcoming, List<AppointmentDto> history) {
}
