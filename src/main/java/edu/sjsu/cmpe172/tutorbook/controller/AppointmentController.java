package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import edu.sjsu.cmpe172.tutorbook.service.AppointmentService;

/** Page Controller for confirmation, "my appointments" and cancelling. */
@Controller
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /** Confirmation page: visible to the appointment's customer or provider only. */
    @GetMapping("/appointments/{appointmentId}/confirmation")
    public String confirmation(@PathVariable long appointmentId,
                               @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("appointment", appointmentService.getAppointment(user, appointmentId));
        return "confirmation";
    }

    /** View my appointments (customers only). */
    @GetMapping("/my/appointments")
    public String myAppointments(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("appointments", appointmentService.getCustomerAppointments(user.userId()));
        return "my-appointments";
    }

    /** Cancel my appointment (customers only; ownership is checked in the service). */
    @PostMapping("/my/appointments/{appointmentId}/cancel")
    public String cancel(@PathVariable long appointmentId,
                         @AuthenticationPrincipal AppUser user, RedirectAttributes flash) {
        appointmentService.cancel(user, appointmentId);
        flash.addFlashAttribute("notice", "Appointment #" + appointmentId + " was cancelled.");
        return "redirect:/my/appointments";
    }
}
