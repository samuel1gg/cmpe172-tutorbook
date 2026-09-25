package edu.sjsu.cmpe172.tutorbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.dto.PageDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.model.SlotDetails;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/** Business logic for browsing available slots. */
@Service
public class SlotService {

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    /** One page of available slots; page/size are turned into SQL LIMIT/OFFSET. */
    public PageDto<SlotDto> findAvailableSlots(SlotSearchCriteria criteria) {
        int page = criteria.pageOrDefault();
        int size = criteria.sizeOrDefault();
        List<SlotDto> items = slotRepository.findAvailable(criteria, size, page * size)
                .stream()
                .map(SlotService::toDto)
                .toList();
        long total = slotRepository.countAvailable(criteria);
        return PageDto.of(items, page, size, total);
    }

    public SlotDto getAvailableSlot(long slotId) {
        return slotRepository.findAvailableById(slotId)
                .map(SlotService::toDto)
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " is not available"));
    }

    private static SlotDto toDto(SlotDetails s) {
        return new SlotDto(s.slotId(), s.startTime(), s.endTime(),
                s.providerId(), s.providerName(), s.serviceId(), s.serviceName(),
                s.durationMinutes(), Money.format(s.priceCents()));
    }
}
