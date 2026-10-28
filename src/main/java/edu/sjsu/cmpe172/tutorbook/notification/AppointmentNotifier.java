package edu.sjsu.cmpe172.tutorbook.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * COMMIT FIRST, THEN NOTIFY.
 *
 * phase = AFTER_COMMIT means this runs only after the booking or cancel
 * transaction has committed: the database row is final and no locks are held
 * while we wait on the network. A slow or dead notification service therefore
 * cannot roll back, block or slow down the database work.
 */
@Component
public class AppointmentNotifier {

    private final NotificationDispatcher dispatcher;

    public AppointmentNotifier(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentChanged(AppointmentEvent event) {
        dispatcher.notify(event.appointmentId(), event.type());
    }
}
