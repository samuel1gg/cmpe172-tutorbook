package edu.sjsu.cmpe172.tutorbook.notification;

/**
 * Published INSIDE the booking / cancel transaction. Spring holds it until the
 * transaction commits and only then hands it to AppointmentNotifier; if the
 * transaction rolls back, the event is dropped and nobody is notified.
 */
public record AppointmentEvent(long appointmentId, NotificationType type) {
}
