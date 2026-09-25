package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import edu.sjsu.cmpe172.tutorbook.service.AppointmentService;

/** Page Controller for the booking confirmation page. */
@Controller
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/appointments/{appointmentId}/confirmation")
    public String confirmation(@PathVariable long appointmentId, Model model) {
        model.addAttribute("appointment", appointmentService.getAppointment(appointmentId));
        return "confirmation";
    }
}
