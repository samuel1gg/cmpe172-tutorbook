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

import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.dto.form.BookingForm;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import edu.sjsu.cmpe172.tutorbook.service.BookingService;
import edu.sjsu.cmpe172.tutorbook.service.CatalogService;
import edu.sjsu.cmpe172.tutorbook.service.SlotService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Page Controller for browsing slots and booking one. */
@Controller
public class SlotController {

    private final SlotService slotService;
    private final CatalogService catalogService;
    private final BookingService bookingService;

    public SlotController(SlotService slotService, CatalogService catalogService, BookingService bookingService) {
        this.slotService = slotService;
        this.catalogService = catalogService;
        this.bookingService = bookingService;
    }

    /** GET /slots?providerId=&serviceId=&date=&page=&size= (public) */
    @GetMapping("/slots")
    public String listSlots(SlotSearchCriteria criteria, Model model) {
        model.addAttribute("slots", slotService.findAvailableSlots(criteria));
        model.addAttribute("criteria", criteria);
        model.addAttribute("providers", catalogService.getProviders());
        model.addAttribute("services", catalogService.getServices());
        return "slots";
    }

    /** GET /slots/{id}/book - the booking form (customers only, see SecurityConfig). */
    @GetMapping("/slots/{slotId}/book")
    public String bookingForm(@PathVariable long slotId, Model model) {
        model.addAttribute("slot", slotService.getAvailableSlot(slotId));
        model.addAttribute("bookingForm", new BookingForm());
        return "book";
    }

    /**
     * POST /slots/{id}/book - books the slot for the logged-in customer.
     * Invalid input re-shows the form with 400; a conflict becomes 409 via
     * GlobalExceptionHandler; success redirects to the confirmation page.
     */
    @PostMapping("/slots/{slotId}/book")
    public String book(@PathVariable long slotId,
                       @Valid @ModelAttribute("bookingForm") BookingForm form,
                       BindingResult errors,
                       @AuthenticationPrincipal AppUser user,
                       Model model,
                       HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("slot", slotService.getAvailableSlot(slotId));
            return "book";
        }
        long appointmentId = bookingService.book(user.userId(), slotId, form.notesOrNull());
        return "redirect:/appointments/" + appointmentId + "/confirmation";
    }
}
