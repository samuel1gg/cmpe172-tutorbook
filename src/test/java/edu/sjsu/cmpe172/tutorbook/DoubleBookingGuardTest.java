package edu.sjsu.cmpe172.tutorbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/** Proves the UNIQUE(active_slot_id) constraint in schema.sql stops double-booking. */
@SpringBootTest
@Transactional // each test is rolled back so the seed data stays unchanged
class DoubleBookingGuardTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long OPEN_SLOT = 1;

    @Autowired
    AppointmentRepository appointments;

    @Autowired
    SlotRepository slots;

    @Test
    void secondActiveBookingForSameSlotIsRejectedByDatabase() {
        appointments.insertBooked(OPEN_SLOT, ALICE, "first");

        assertThatThrownBy(() -> appointments.insertBooked(OPEN_SLOT, BOB, "second"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void bookedSlotDisappearsFromAvailableSlots() {
        assertThat(slots.findAvailableById(OPEN_SLOT)).isPresent();

        appointments.insertBooked(OPEN_SLOT, ALICE, null);

        assertThat(slots.findAvailableById(OPEN_SLOT)).isEmpty();
    }

    @Test
    void cancelledSlotCanBeBookedAgain() {
        long first = appointments.insertBooked(OPEN_SLOT, ALICE, null);
        assertThat(appointments.cancel(first)).isEqualTo(1);

        long second = appointments.insertBooked(OPEN_SLOT, BOB, null);

        assertThat(second).isNotEqualTo(first);
        assertThat(appointments.findDetailsById(first).orElseThrow().status()).isEqualTo("CANCELLED");
        assertThat(appointments.findDetailsById(second).orElseThrow().status()).isEqualTo("BOOKED");
    }

    @Test
    void seedDataAlreadyHasOneCancelledAndReopenedSlot() {
        // seed.sql: slot 2 has an active booking, slot 5's booking was cancelled
        assertThat(slots.findAvailableById(2)).isEmpty();
        assertThat(slots.findAvailableById(5)).isPresent();
    }
}
