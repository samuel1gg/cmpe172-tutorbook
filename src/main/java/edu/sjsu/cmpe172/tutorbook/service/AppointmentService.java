package edu.sjsu.cmpe172.tutorbook.service;

import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.dto.AppointmentDto;
import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;

/** Business logic for appointments. Booking and cancelling are added in Milestone 2. */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public AppointmentDto getAppointment(long appointmentId) {
        AppointmentDetails a = appointmentRepository.findDetailsById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
        return new AppointmentDto(a.appointmentId(), a.status(), a.customerName(), a.customerEmail(),
                a.providerName(), a.serviceName(), a.startTime(), a.endTime(),
                Money.format(a.priceCents()), a.notes());
    }
}
