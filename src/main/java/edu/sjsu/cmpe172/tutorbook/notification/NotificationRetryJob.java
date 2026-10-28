package edu.sjsu.cmpe172.tutorbook.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RECOVERY: when the notification service was down, bookings were still saved
 * and their notifications were stored as FAILED. Once a minute this job resends
 * them (same idempotency key, so no duplicates), until they are SENT or the
 * total attempts reach notification.max-total-attempts (then GAVE_UP).
 */
@Component
public class NotificationRetryJob {

    static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties props;

    public NotificationRetryJob(NotificationRepository notificationRepository,
                                NotificationDispatcher dispatcher,
                                NotificationProperties props) {
        this.notificationRepository = notificationRepository;
        this.dispatcher = dispatcher;
        this.props = props;
    }

    @Scheduled(initialDelayString = "${notification.retry-interval:60s}",
               fixedDelayString = "${notification.retry-interval:60s}")
    public void retryFailed() {
        List<NotificationRecord> failed = notificationRepository.findRetryable(props.maxTotalAttempts(), BATCH_SIZE);
        if (!failed.isEmpty()) {
            log.info("Retrying {} failed notification(s)", failed.size());
            failed.forEach(dispatcher::retry);
        }
    }
}
