package edu.sjsu.cmpe172.tutorbook.mocknotification;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.sjsu.cmpe172.tutorbook.notification.NotificationRequest;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationResponse;
import jakarta.validation.Valid;

/**
 * MOCK NOTIFICATION SERVICE: a second controller that stands in for an
 * external messaging system (email/SMS provider). TutorBook reaches it only
 * over HTTP through HttpNotificationGateway, exactly as it would reach a real
 * remote service, so swapping in Discord, Slack or Gmail SMTP later does not
 * change the rest of the app.
 *
 *   POST /mock-notification-service/notifications      receive one notification (JSON DTO)
 *   GET  /mock-notification-service/notifications      what it has "sent" (for the demo)
 *   GET  /mock-notification-service/health             is it up?
 *   PUT  /mock-notification-service/mode/{UP|DOWN|SLOW} simulate an outage (demo only)
 */
@RestController
@RequestMapping("/mock-notification-service")
public class MockNotificationController {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);

    private final MockNotificationService service;

    public MockNotificationController(MockNotificationService service) {
        this.service = service;
    }

    @PostMapping("/notifications")
    public ResponseEntity<NotificationResponse> receive(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationRequest request) throws InterruptedException {
        service.countRequest();
        switch (service.mode()) {
            case DOWN -> {
                log.warn("[mock-notification-service] DOWN: rejecting {}", request.idempotencyKey());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            case SLOW -> Thread.sleep(service.slowDelayMs());
            case UP -> { }
        }
        if (idempotencyKey != null && !idempotencyKey.equals(request.idempotencyKey())) {
            return ResponseEntity.badRequest().build();
        }
        MockNotificationService.Delivered d = service.accept(request);
        if (d == null) {
            MockNotificationService.Delivered first = service.find(request.idempotencyKey());
            log.info("[mock-notification-service] duplicate {} ignored", request.idempotencyKey());
            return ResponseEntity.ok(new NotificationResponse(first.notificationId(), "DUPLICATE", first.receivedAt()));
        }
        log.info("[mock-notification-service] {} {} -> {}: {}", d.notificationId(), request.type(),
                request.recipient(), request.subject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new NotificationResponse(d.notificationId(), "ACCEPTED", d.receivedAt()));
    }

    @GetMapping("/notifications")
    public List<MockNotificationService.Delivered> sent() {
        return service.all();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean up = service.mode() != MockNotificationService.Mode.DOWN;
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", up ? "UP" : "DOWN", "mode", service.mode().name()));
    }

    @PutMapping("/mode/{mode}")
    public Map<String, String> setMode(@PathVariable MockNotificationService.Mode mode) {
        service.setMode(mode);
        log.warn("[mock-notification-service] mode set to {}", mode);
        return Map.of("mode", mode.name());
    }
}
