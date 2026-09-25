package edu.sjsu.cmpe172.tutorbook.dto;

import java.util.List;

/** Everything the home page (GET /) needs, assembled by CatalogService. */
public record HomeDto(
        int providerCount,
        int serviceCount,
        long availableSlotCount,
        List<ProviderDto> providers,
        List<ServiceDto> services) {
}
