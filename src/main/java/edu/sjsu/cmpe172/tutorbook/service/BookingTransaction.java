package edu.sjsu.cmpe172.tutorbook.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.OptimisticConflictException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.model.SlotState;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/**
 * ONE booking attempt as ONE database transaction (all-or-nothing).
 *
 *   1. read the slot and its version
 *   2. check the booking rules (exists, not removed, in the future, not booked)
 *   3. UPDATE slot SET version = version + 1 WHERE version = <read value>
 *        -> 0 rows means someone else changed the slot: OptimisticConflictException
 *   4. INSERT the appointment
 *        -> UNIQUE(active_slot_id) is the last line of defence (DuplicateKeyException)
 *
 * It is a separate bean from BookingService so that Spring's @Transactional
 * proxy applies (a method calling itself would bypass the proxy) and so each
 * retry gets a brand-new transaction.
 */
@Component
public class BookingTransaction {

    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public BookingTransaction(SlotRepository slotRepository,
                              AppointmentRepository appointmentRepository,
                              Clock clock) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long tryBook(long customerId, long slotId, String notes) {
        // 1. read
        SlotState slot = slotRepository.findState(slotId)
                .filter(s -> !s.removed())
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " does not exist"));

        // 2. business rules
        if (!slot.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new SlotConflictException("This slot has already started and can no longer be booked.");
        }
        if (slot.booked()) {
            throw new SlotConflictException("Sorry, this slot was just booked by someone else.");
        }

        // 3. optimistic version check: claim the slot
        if (!slotRepository.bumpVersion(slotId, slot.version())) {
            throw new OptimisticConflictException(slotId);
        }

        // 4. create the appointment; the unique constraint is the backstop
        try {
            return appointmentRepository.insertBooked(slotId, customerId, notes);
        } catch (DuplicateKeyException e) {
            throw new SlotConflictException("Sorry, this slot was just booked by someone else.");
        }
    }
}
