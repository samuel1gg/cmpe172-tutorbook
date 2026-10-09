package edu.sjsu.cmpe172.tutorbook.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import edu.sjsu.cmpe172.tutorbook.dto.ProviderSlotDto;
import edu.sjsu.cmpe172.tutorbook.dto.form.SlotForm;
import edu.sjsu.cmpe172.tutorbook.exception.BadRequestException;
import edu.sjsu.cmpe172.tutorbook.exception.ForbiddenException;
import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.model.ServiceOffering;
import edu.sjsu.cmpe172.tutorbook.model.SlotState;
import edu.sjsu.cmpe172.tutorbook.repository.ServiceOfferingRepository;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/** Provider features: list, create and remove availability slots. */
@Service
public class ProviderSlotService {

    private final SlotRepository slotRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final Clock clock;

    public ProviderSlotService(SlotRepository slotRepository,
                               ServiceOfferingRepository serviceRepository,
                               Clock clock) {
        this.slotRepository = slotRepository;
        this.serviceRepository = serviceRepository;
        this.clock = clock;
    }

    public List<ProviderSlotDto> getMySlots(long providerId) {
        return slotRepository.findUpcomingByProvider(providerId).stream()
                .map(r -> new ProviderSlotDto(r.slotId(), r.startTime(), r.endTime(),
                        r.serviceName(), r.booked(), r.customerName()))
                .toList();
    }

    /** Creates a slot tied to a service; end time = start + the service's duration. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long createSlot(long providerId, SlotForm form) {
        ServiceOffering service = serviceRepository.findById(form.getServiceId())
                .orElseThrow(() -> new BadRequestException("Unknown service."));
        LocalDateTime start = form.getDate().atTime(form.getStartTime());
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new BadRequestException("The start time must be in the future.");
        }
        LocalDateTime end = start.plusMinutes(service.durationMinutes());
        if (slotRepository.overlapExists(providerId, start, end)) {
            throw new SlotConflictException("This time overlaps one of your existing slots.");
        }
        try {
            return slotRepository.insert(providerId, service.serviceId(), start, end);
        } catch (DuplicateKeyException e) { // UNIQUE(provider_id, active_start_time) backstop
            throw new SlotConflictException("You already have a slot starting at that time.");
        }
    }

    /**
     * Removes one of the provider's own slots, only if nobody has booked it.
     * It bumps the slot's version like a booking does, so a removal and a
     * booking of the same slot cannot both succeed.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeSlot(long providerId, long slotId) {
        SlotState slot = slotRepository.findState(slotId)
                .filter(s -> !s.removed())
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " does not exist"));
        if (slot.providerId() != providerId) {
            throw new ForbiddenException("You can only remove your own slots.");
        }
        if (slot.booked()) {
            throw new SlotConflictException("This slot is booked. The student must cancel first.");
        }
        if (!slotRepository.bumpVersion(slotId, slot.version())) {
            throw new SlotConflictException("This slot was just changed. Refresh and try again.");
        }
        slotRepository.markRemoved(slotId);
    }
}
