package edu.sjsu.cmpe172.tutorbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.dto.HomeDto;
import edu.sjsu.cmpe172.tutorbook.dto.ProviderDto;
import edu.sjsu.cmpe172.tutorbook.dto.ServiceDto;
import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.model.Provider;
import edu.sjsu.cmpe172.tutorbook.model.ServiceOffering;
import edu.sjsu.cmpe172.tutorbook.repository.ProviderRepository;
import edu.sjsu.cmpe172.tutorbook.repository.ServiceOfferingRepository;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/** Business logic for the provider and service catalog and the home page. */
@Service
public class CatalogService {

    private final ProviderRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final SlotRepository slotRepository;

    public CatalogService(ProviderRepository providerRepository,
                          ServiceOfferingRepository serviceRepository,
                          SlotRepository slotRepository) {
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
    }

    public List<ProviderDto> getProviders() {
        return providerRepository.findAll().stream().map(CatalogService::toDto).toList();
    }

    public List<ServiceDto> getServices() {
        return serviceRepository.findAll().stream().map(CatalogService::toDto).toList();
    }

    /** Builds the home page: counts plus the provider and service lists. */
    public HomeDto getHome() {
        List<ProviderDto> providers = getProviders();
        List<ServiceDto> services = getServices();
        long openSlots = slotRepository.countAvailable(
                new SlotSearchCriteria(null, null, null, null, null));
        return new HomeDto(providers.size(), services.size(), openSlots, providers, services);
    }

    private static ProviderDto toDto(Provider p) {
        return new ProviderDto(p.providerId(), p.displayName(), p.specialty(), p.bio());
    }

    private static ServiceDto toDto(ServiceOffering s) {
        return new ServiceDto(s.serviceId(), s.name(), s.description(),
                s.durationMinutes(), Money.format(s.priceCents()));
    }
}
