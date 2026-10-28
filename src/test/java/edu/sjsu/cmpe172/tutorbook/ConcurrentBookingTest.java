package edu.sjsu.cmpe172.tutorbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.service.BookingService;

/**
 * The race condition from the report, run for real: two customers try to book
 * the SAME slot at the SAME moment on two threads. Exactly one must succeed,
 * the other must get SlotConflictException (HTTP 409), and the database must
 * contain exactly one BOOKED appointment for the slot.
 *
 * Not @Transactional: each thread must commit its own transaction, exactly
 * like two real HTTP requests. The extra property gives this class its OWN
 * Spring context (fresh database, whatever ran before), and @DirtiesContext
 * closes it afterwards so later test classes are not affected either.
 */
@SpringBootTest(properties = "spring.application.name=concurrency-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentBookingTest {

    static final long ALICE = 1, BOB = 2;

    @Autowired
    BookingService bookingService;

    @Autowired
    JdbcClient jdbc;

    /** Runs the two-thread race on five different open slots. */
    @ParameterizedTest(name = "slot {0}")
    @ValueSource(longs = {1, 3, 4, 6, 7})
    void twoConcurrentBookingsOfTheSameSlot_exactlyOneSucceeds(long slotId) throws Exception {
        List<Outcome> outcomes = race(slotId, List.of(ALICE, BOB));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof SlotConflictException).hasSize(1);
        assertThat(bookedCount(slotId)).isEqualTo(1);
    }

    /** Same guarantee under heavier contention: 8 customers, 1 slot. */
    @Test
    void eightConcurrentBookings_exactlyOneSucceeds() throws Exception {
        long slotId = 11;
        List<Long> customers = List.of(ALICE, BOB, 3L, ALICE, BOB, 3L, ALICE, BOB);

        List<Outcome> outcomes = race(slotId, customers);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof SlotConflictException).hasSize(7);
        assertThat(bookedCount(slotId)).isEqualTo(1);
    }

    /** Starts one thread per customer, releases them together, and collects what happened. */
    private List<Outcome> race(long slotId, List<Long> customerIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(customerIds.size());
        CountDownLatch ready = new CountDownLatch(customerIds.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (long customerId : customerIds) {
                Callable<Outcome> attempt = () -> {
                    ready.countDown();
                    go.await();                       // every thread waits at the starting line
                    try {
                        return new Outcome(bookingService.book(customerId, slotId, "race"), null);
                    } catch (RuntimeException e) {
                        return new Outcome(null, e);
                    }
                };
                futures.add(pool.submit(attempt));
            }
            ready.await();
            go.countDown();                           // ...and they all start at once
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private long bookedCount(long slotId) {
        return jdbc.sql("SELECT COUNT(*) FROM appointments WHERE slot_id = :id AND status = 'BOOKED'")
                .param("id", slotId)
                .query(Long.class)
                .single();
    }

    record Outcome(Long appointmentId, Exception error) {
        boolean succeeded() {
            return appointmentId != null;
        }
    }
}
