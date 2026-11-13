package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.repository.DistrictZoneMappingRepository;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.DistrictMappingRequest;
import com.swiftdrop.pricing.dto.ZoneRequest;
import com.swiftdrop.pricing.dto.ZoneResponse;
import com.swiftdrop.pricing.exception.ZoneConflictException;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneAdminServiceTest {

    @Mock
    private PricingZoneRepository zoneRepository;

    @Mock
    private DistrictZoneMappingRepository mappingRepository;

    @InjectMocks
    private ZoneAdminService service;

    private ZoneRequest request(String code) {
        return new ZoneRequest(code, "Test Zone", "METRO", new BigDecimal("550.00"),
                new BigDecimal("120.00"), new BigDecimal("1.50"), new BigDecimal("2.00"), null);
    }

    @Test
    void createZone_rejectsDuplicateCode() {
        when(zoneRepository.existsByZoneCode("ZONE_X")).thenReturn(true);

        assertThrows(ZoneConflictException.class, () -> service.createZone(request("ZONE_X")));
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void createZone_defaultsStatusToActive() {
        when(zoneRepository.existsByZoneCode("ZONE_X")).thenReturn(false);
        when(zoneRepository.save(any(PricingZone.class))).thenAnswer(inv -> inv.getArgument(0));

        ZoneResponse response = service.createZone(request("ZONE_X"));

        assertEquals("ACTIVE", response.status());
    }

    @Test
    void updateZone_rejectsCodeChange() {
        assertThrows(IllegalArgumentException.class, () -> service.updateZone("ZONE_A", request("ZONE_B")));
    }

    @Test
    void deleteZone_rejectsZoneWithMappedDistricts() {
        PricingZone zone = PricingZone.builder().zoneCode("ZONE_X").build();
        when(zoneRepository.findByZoneCode("ZONE_X")).thenReturn(Optional.of(zone));
        when(mappingRepository.existsByZoneCode("ZONE_X")).thenReturn(true);

        assertThrows(ZoneConflictException.class, () -> service.deleteZone("ZONE_X"));
        verify(zoneRepository, never()).delete(any());
    }

    @Test
    void assignDistrict_failsWhenZoneMissing() {
        when(zoneRepository.findByZoneCode("ZONE_NONE")).thenReturn(Optional.empty());

        assertThrows(ZoneNotFoundException.class,
                () -> service.assignDistrict("COLOMBO", new DistrictMappingRequest("ZONE_NONE")));
    }
}
