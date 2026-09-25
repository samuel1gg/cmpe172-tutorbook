package edu.sjsu.cmpe172.tutorbook.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.sjsu.cmpe172.tutorbook.dto.PageDto;
import edu.sjsu.cmpe172.tutorbook.dto.ProviderDto;
import edu.sjsu.cmpe172.tutorbook.dto.ServiceDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.service.CatalogService;
import edu.sjsu.cmpe172.tutorbook.service.SlotService;

/**
 * JSON (REST) versions of the same reads. It calls the same services as the
 * HTML controllers, which shows the layers are independent of the view.
 */
@RestController
@RequestMapping("/api")
public class CatalogApiController {

    private final SlotService slotService;
    private final CatalogService catalogService;

    public CatalogApiController(SlotService slotService, CatalogService catalogService) {
        this.slotService = slotService;
        this.catalogService = catalogService;
    }

    @GetMapping("/slots")
    public PageDto<SlotDto> slots(SlotSearchCriteria criteria) {
        return slotService.findAvailableSlots(criteria);
    }

    @GetMapping("/slots/{slotId}")
    public SlotDto slot(@PathVariable long slotId) {
        return slotService.getAvailableSlot(slotId);
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        return catalogService.getProviders();
    }

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return catalogService.getServices();
    }
}
