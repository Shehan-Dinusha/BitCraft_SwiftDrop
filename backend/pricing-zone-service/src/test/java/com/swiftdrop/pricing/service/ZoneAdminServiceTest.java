package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        return request(code, null);
    }

    private ZoneRequest request(String code, String status) {
        return new ZoneRequest(code, "Test Zone", "METRO", new BigDecimal("550.00"),
                new BigDecimal("120.00"), new BigDecimal("1.50"), new BigDecimal("2.00"), status);
    }

    private PricingZone zone(String code) {
        return PricingZone.builder()
                .zoneCode(code)
                .zoneName("Old Name")
                .tier("URBAN")
                .baseFee(new BigDecimal("350.00"))
                .perKgFee(new BigDecimal("80.00"))
                .expressMultiplier(new BigDecimal("1.50"))
                .sameDayMultiplier(new BigDecimal("2.00"))
                .status("ACTIVE")
                .updatedAt(Instant.EPOCH)
                .build();
    }

    // ---------- createZone ----------

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
    void createZone_keepsExplicitStatus() {
        when(zoneRepository.existsByZoneCode("ZONE_X")).thenReturn(false);
        when(zoneRepository.save(any(PricingZone.class))).thenAnswer(inv -> inv.getArgument(0));

        ZoneResponse response = service.createZone(request("ZONE_X", "INACTIVE"));

        assertEquals("INACTIVE", response.status());
    }

    // ---------- listZones and getZone ----------

    @Test
    void listZones_returnsAllZones() {
        when(zoneRepository.findAll()).thenReturn(List.of(zone("ZONE_A"), zone("ZONE_B")));

        List<ZoneResponse> zones = service.listZones();

        assertEquals(2, zones.size());
    }

    @Test
    void getZone_returnsExistingZone() {
        when(zoneRepository.findByZoneCode("ZONE_A")).thenReturn(Optional.of(zone("ZONE_A")));

        ZoneResponse response = service.getZone("ZONE_A");

        assertEquals("ACTIVE", response.status());
    }

    @Test
    void getZone_failsWhenZoneMissing() {
        when(zoneRepository.findByZoneCode("ZONE_NONE")).thenReturn(Optional.empty());

        assertThrows(ZoneNotFoundException.class, () -> service.getZone("ZONE_NONE"));
    }

    // ---------- updateZone ----------

    @Test
    void updateZone_rejectsCodeChange() {
        assertThrows(IllegalArgumentException.class, () -> service.updateZone("ZONE_A", request("ZONE_B")));
    }

    @Test
    void updateZone_failsWhenZoneMissing() {
        when(zoneRepository.findByZoneCode("ZONE_A")).thenReturn(Optional.empty());

        assertThrows(ZoneNotFoundException.class, () -> service.updateZone("ZONE_A", request("ZONE_A")));
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void updateZone_appliesNewValuesAndKeepsStatusWhenNull() {
        PricingZone existing = zone("ZONE_A");
        when(zoneRepository.findByZoneCode("ZONE_A")).thenReturn(Optional.of(existing));
        when(zoneRepository.save(any(PricingZone.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateZone("ZONE_A", request("ZONE_A", null));

        assertEquals("Test Zone", existing.getZoneName());
        assertEquals("METRO", existing.getTier());
        assertEquals(new BigDecimal("550.00"), existing.getBaseFee());
        assertEquals(new BigDecimal("120.00"), existing.getPerKgFee());
        assertEquals("ACTIVE", existing.getStatus());
        assertTrue(existing.getUpdatedAt().isAfter(Instant.EPOCH));
    }

    @Test
    void updateZone_changesStatusWhenProvided() {
        PricingZone existing = zone("ZONE_A");
        when(zoneRepository.findByZoneCode("ZONE_A")).thenReturn(Optional.of(existing));
        when(zoneRepository.save(any(PricingZone.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateZone("ZONE_A", request("ZONE_A", "INACTIVE"));

        assertEquals("INACTIVE", existing.getStatus());
    }

    // ---------- deleteZone ----------

    @Test
    void deleteZone_rejectsZoneWithMappedDistricts() {
        PricingZone zone = PricingZone.builder().zoneCode("ZONE_X").build();
        when(zoneRepository.findByZoneCode("ZONE_X")).thenReturn(Optional.of(zone));
        when(mappingRepository.existsByZoneCode("ZONE_X")).thenReturn(true);

        assertThrows(ZoneConflictException.class, () -> service.deleteZone("ZONE_X"));
        verify(zoneRepository, never()).delete(any());
    }

    @Test
    void deleteZone_failsWhenZoneMissing() {
        when(zoneRepository.findByZoneCode("ZONE_NONE")).thenReturn(Optional.empty());

        assertThrows(ZoneNotFoundException.class, () -> service.deleteZone("ZONE_NONE"));
        verify(zoneRepository, never()).delete(any());
    }

    @Test
    void deleteZone_deletesZoneWithoutMappedDistricts() {
        PricingZone existing = zone("ZONE_X");
        when(zoneRepository.findByZoneCode("ZONE_X")).thenReturn(Optional.of(existing));
        when(mappingRepository.existsByZoneCode("ZONE_X")).thenReturn(false);

        service.deleteZone("ZONE_X");

        verify(zoneRepository).delete(existing);
    }

    // ---------- district mappings ----------

    @Test
    void listMappings_returnsAllMappings() {
        DistrictZoneMapping mapping = DistrictZoneMapping.builder()
                .district("COLOMBO").zoneCode("ZONE_WESTERN").build();
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));

        assertEquals(1, service.listMappings().size());
    }

    @Test
    void assignDistrict_failsWhenZoneMissing() {
        when(zoneRepository.findByZoneCode("ZONE_NONE")).thenReturn(Optional.empty());

        assertThrows(ZoneNotFoundException.class,
                () -> service.assignDistrict("COLOMBO", new DistrictMappingRequest("ZONE_NONE")));
    }

    @Test
    void assignDistrict_rejectsUnknownDistrict() {
        assertThrows(IllegalArgumentException.class,
                () -> service.assignDistrict("ATLANTIS", new DistrictMappingRequest("ZONE_WESTERN")));
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void assignDistrict_createsMappingAndNormalizesDistrictName() {
        when(zoneRepository.findByZoneCode("ZONE_WESTERN")).thenReturn(Optional.of(zone("ZONE_WESTERN")));
        when(mappingRepository.findByDistrict("COLOMBO")).thenReturn(Optional.empty());
        when(mappingRepository.save(any(DistrictZoneMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignDistrict("colombo", new DistrictMappingRequest("ZONE_WESTERN"));

        ArgumentCaptor<DistrictZoneMapping> captor = ArgumentCaptor.forClass(DistrictZoneMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertEquals("COLOMBO", captor.getValue().getDistrict());
        assertEquals("ZONE_WESTERN", captor.getValue().getZoneCode());
    }

    @Test
    void assignDistrict_movesExistingMappingToNewZone() {
        DistrictZoneMapping existing = DistrictZoneMapping.builder()
                .district("KANDY").zoneCode("ZONE_CENTRAL").build();
        when(zoneRepository.findByZoneCode("ZONE_WESTERN")).thenReturn(Optional.of(zone("ZONE_WESTERN")));
        when(mappingRepository.findByDistrict("KANDY")).thenReturn(Optional.of(existing));
        when(mappingRepository.save(any(DistrictZoneMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignDistrict("KANDY", new DistrictMappingRequest("ZONE_WESTERN"));

        assertEquals("ZONE_WESTERN", existing.getZoneCode());
    }
}