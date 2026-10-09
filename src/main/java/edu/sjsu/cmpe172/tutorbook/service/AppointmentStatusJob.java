package edu.sjsu.cmpe172.tutorbook.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;

/** Marks BOOKED appointments as COMPLETED once their slot has ended (on startup, then every minute). */
@Component
public class AppointmentStatusJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusJob.class);

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public AppointmentStatusJob(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 0, fixedDelay = 60_000)
    public void completePastAppointments() {
        int changed = appointmentRepository.markPastAsCompleted(LocalDateTime.now(clock));
        if (changed > 0) {
            log.info("Marked {} past appointment(s) as COMPLETED", changed);
        }
    }
}
