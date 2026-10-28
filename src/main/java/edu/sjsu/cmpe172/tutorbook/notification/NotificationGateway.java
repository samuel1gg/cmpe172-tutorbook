package edu.sjsu.cmpe172.tutorbook.notification;

/**
 * Gateway to the external notification service.
 *
 * The rest of TutorBook only knows this interface; whether the other side is
 * the mock controller, a Discord webhook or an email server is hidden behind it.
 * Implementations never throw: every failure comes back as a SendResult.
 */
public interface NotificationGateway {

    SendResult send(NotificationRequest request);

    /** Outcome of one send() call, which may include several HTTP attempts. */
    record SendResult(boolean delivered, int attempts, boolean retryableLater, String error) {

        public static SendResult delivered(int attempts) {
            return new SendResult(true, attempts, false, null);
        }

        public static SendResult failed(int attempts, boolean retryableLater, String error) {
            return new SendResult(false, attempts, retryableLater, error);
        }
    }
}
