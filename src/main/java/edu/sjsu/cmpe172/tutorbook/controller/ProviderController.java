package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import edu.sjsu.cmpe172.tutorbook.dto.form.SlotForm;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import edu.sjsu.cmpe172.tutorbook.service.AppointmentService;
import edu.sjsu.cmpe172.tutorbook.service.CatalogService;
import edu.sjsu.cmpe172.tutorbook.service.ProviderSlotService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Provider-only pages (everything under /provider requires ROLE_PROVIDER). */
@Controller
public class ProviderController {

    private final ProviderSlotService providerSlotService;
    private final AppointmentService appointmentService;
    private final CatalogService catalogService;

    public ProviderController(ProviderSlotService providerSlotService,
                              AppointmentService appointmentService,
                              CatalogService catalogService) {
        this.providerSlotService = providerSlotService;
        this.appointmentService = appointmentService;
        this.catalogService = catalogService;
    }

    @GetMapping("/provider/slots")
    public String mySlots(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("slotForm", new SlotForm());
        return slotsPage(user, model);
    }

    /** Create an availability slot tied to a service. */
    @PostMapping("/provider/slots")
    public String createSlot(@Valid @ModelAttribute("slotForm") SlotForm form, BindingResult errors,
                             @AuthenticationPrincipal AppUser user, Model model,
                             HttpServletResponse response, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return slotsPage(user, model);
        }
        long slotId = providerSlotService.createSlot(user.providerId(), form);
        flash.addFlashAttribute("notice", "Slot #" + slotId + " created.");
        return "redirect:/provider/slots";
    }

    /** Remove one of my slots (only if nobody booked it). */
    @PostMapping("/provider/slots/{slotId}/remove")
    public String removeSlot(@PathVariable long slotId, @AuthenticationPrincipal AppUser user,
                             RedirectAttributes flash) {
        providerSlotService.removeSlot(user.providerId(), slotId);
        flash.addFlashAttribute("notice", "Slot #" + slotId + " removed.");
        return "redirect:/provider/slots";
    }

    /** View appointments booked with me. */
    @GetMapping("/provider/appointments")
    public String myAppointments(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("appointments", appointmentService.getProviderAppointments(user.providerId()));
        return "provider-appointments";
    }

    private String slotsPage(AppUser user, Model model) {
        model.addAttribute("slots", providerSlotService.getMySlots(user.providerId()));
        model.addAttribute("services", catalogService.getServices());
        return "provider-slots";
    }
}
