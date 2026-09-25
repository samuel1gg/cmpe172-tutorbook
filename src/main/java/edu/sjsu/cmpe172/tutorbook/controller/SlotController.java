package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.service.CatalogService;
import edu.sjsu.cmpe172.tutorbook.service.SlotService;

/** Page Controller for browsing slots and opening the booking form. */
@Controller
public class SlotController {

    private final SlotService slotService;
    private final CatalogService catalogService;

    public SlotController(SlotService slotService, CatalogService catalogService) {
        this.slotService = slotService;
        this.catalogService = catalogService;
    }

    /** GET /slots?providerId=&serviceId=&date=&page=&size= */
    @GetMapping("/slots")
    public String listSlots(SlotSearchCriteria criteria, Model model) {
        model.addAttribute("slots", slotService.findAvailableSlots(criteria));
        model.addAttribute("criteria", criteria);
        model.addAttribute("providers", catalogService.getProviders());
        model.addAttribute("services", catalogService.getServices());
        return "slots";
    }

    /** GET /slots/{id}/book - shows the booking form (submitting it is Milestone 2). */
    @GetMapping("/slots/{slotId}/book")
    public String bookingForm(@PathVariable long slotId, Model model) {
        model.addAttribute("slot", slotService.getAvailableSlot(slotId));
        return "book";
    }
}
