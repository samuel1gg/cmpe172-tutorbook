package edu.sjsu.cmpe172.tutorbook.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import edu.sjsu.cmpe172.tutorbook.dto.AppointmentDto;
import edu.sjsu.cmpe172.tutorbook.dto.AppointmentListDto;
import edu.sjsu.cmpe172.tutorbook.exception.ForbiddenException;
import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;
import edu.sjsu.cmpe172.tutorbook.notification.AppointmentEvent;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationType;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/** Viewing and cancelling appointments, with ownership checks. */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public AppointmentService(AppointmentRepository appointmentRepository, Clock clock,
                              ApplicationEventPublisher events) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.events = events;
    }

    /** One appointment; only its customer or its provider may see it. */
    public AppointmentDto getAppointment(AppUser user, long appointmentId) {
        AppointmentDetails a = find(appointmentId);
        boolean isOwner = a.customerId() == user.userId();
        boolean isItsProvider = user.providerId() != null && user.providerId().equals(a.providerId());
        if (!isOwner && !isItsProvider) {
            throw new ForbiddenException("You can only view your own appointments.");
        }
        return toDto(a, isOwner);
    }

    /** "View my appointments": upcoming and history for the logged-in customer. */
    public AppointmentListDto getCustomerAppointments(long customerId) {
        return split(appointmentRepository.findByCustomer(customerId), true);
    }

    /** "View appointments booked with me" for the logged-in provider. */
    public AppointmentListDto getProviderAppointments(long providerId) {
        return split(appointmentRepository.findByProvider(providerId), false);
    }

    /**
     * "Cancel my appointment (owner-only)". The conditional UPDATE
     * (WHERE status = 'BOOKED') makes it safe if two cancels race.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(AppUser user, long appointmentId) {
        AppointmentDetails a = find(appointmentId);
        if (a.customerId() != user.userId()) {
            throw new ForbiddenException("You can only cancel your own appointments.");
        }
        if (!"BOOKED".equals(a.status())) {
            throw new SlotConflictException("This appointment is already " + a.status().toLowerCase() + ".");
        }
        if (!a.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new SlotConflictException("This appointment has already started and cannot be cancelled.");
        }
        if (appointmentRepository.cancel(appointmentId) == 0) {
            throw new SlotConflictException("This appointment was already cancelled.");
        }
        log.info("Cancelled appointment {} (customer {})", appointmentId, user.userId());
        // delivered only after this transaction commits (commit-then-notify)
        events.publishEvent(new AppointmentEvent(appointmentId, NotificationType.BOOKING_CANCELLED));
    }

    private AppointmentDetails find(long appointmentId) {
        return appointmentRepository.findDetailsById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
    }

    private AppointmentListDto split(List<AppointmentDetails> all, boolean viewerIsCustomer) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AppointmentDto> upcoming = all.stream()
                .filter(a -> isUpcoming(a, now))
                .map(a -> toDto(a, viewerIsCustomer))
                .sorted((x, y) -> x.start().compareTo(y.start()))
                .toList();
        List<AppointmentDto> history = all.stream()
                .filter(a -> !isUpcoming(a, now))
                .map(a -> toDto(a, viewerIsCustomer))
                .toList();
        return new AppointmentListDto(upcoming, history);
    }

    private static boolean isUpcoming(AppointmentDetails a, LocalDateTime now) {
        return "BOOKED".equals(a.status()) && a.startTime().isAfter(now);
    }

    private AppointmentDto toDto(AppointmentDetails a, boolean viewerIsOwner) {
        boolean cancellable = viewerIsOwner && isUpcoming(a, LocalDateTime.now(clock));
        return new AppointmentDto(a.appointmentId(), a.status(), a.customerName(), a.customerEmail(),
                a.providerName(), a.serviceName(), a.startTime(), a.endTime(),
                Money.format(a.priceCents()), a.notes(), cancellable);
    }
}
