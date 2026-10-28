package edu.sjsu.cmpe172.tutorbook.mocknotification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.sjsu.cmpe172.tutorbook.notification.NotificationRequest;

/**
 * State of the MOCK external messaging system. It "sends" a notification by
 * keeping it in memory. Its mode lets us simulate an outage for the demo and tests:
 *
 *   UP    accept every request
 *   DOWN  answer 503 Service Unavailable
 *   SLOW  wait slowDelay before answering (longer than TutorBook's read timeout)
 *
 * Idempotent: a second request with the same idempotency key is not stored
 * again; it is answered as DUPLICATE.
 */
@Component
public class MockNotificationService {

    public enum Mode { UP, DOWN, SLOW }

    /** A notification as the external service received it. */
    public record Delivered(String notificationId, NotificationRequest request, Instant receivedAt) {
    }

    private final Map<String, Delivered> byKey = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicLong requestsReceived = new AtomicLong();
    private volatile Mode mode;
    private volatile long slowDelayMs;

    public MockNotificationService(@Value("${notification.mock.mode:UP}") Mode mode,
                                   @Value("${notification.mock.slow-delay:3s}") java.time.Duration slowDelay) {
        this.mode = mode;
        this.slowDelayMs = slowDelay.toMillis();
    }

    /** Stores the notification; returns null when it was a duplicate. */
    public synchronized Delivered accept(NotificationRequest request) {
        if (byKey.containsKey(request.idempotencyKey())) {
            return null;
        }
        Delivered d = new Delivered("ntf-" + ids.incrementAndGet(), request, Instant.now());
        byKey.put(request.idempotencyKey(), d);
        return d;
    }

    public synchronized Delivered find(String key) {
        return byKey.get(key);
    }

    public synchronized List<Delivered> all() {
        return new ArrayList<>(byKey.values());
    }

    public synchronized void clear() {
        byKey.clear();
        requestsReceived.set(0);
    }

    long countRequest() {
        return requestsReceived.incrementAndGet();
    }

    /** Every HTTP request that reached the service, including duplicates and ones answered 503. */
    public long requestsReceived() {
        return requestsReceived.get();
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public long slowDelayMs() {
        return slowDelayMs;
    }

    public void setSlowDelayMs(long slowDelayMs) {
        this.slowDelayMs = slowDelayMs;
    }
}
