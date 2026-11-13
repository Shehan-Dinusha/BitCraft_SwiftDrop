package com.swiftdrop.pricing.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.repository.DistrictZoneMappingRepository;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.DistrictMappingRequest;
import com.swiftdrop.pricing.dto.DistrictMappingResponse;
import com.swiftdrop.pricing.dto.ZoneRequest;
import com.swiftdrop.pricing.dto.ZoneResponse;
import com.swiftdrop.pricing.exception.ZoneConflictException;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneAdminService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final PricingZoneRepository zoneRepository;
    private final DistrictZoneMappingRepository mappingRepository;

    @Transactional(readOnly = true)
    public List<ZoneResponse> listZones() {
        return zoneRepository.findAll().stream().map(ZoneResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse getZone(String zoneCode) {
        return ZoneResponse.from(findZone(zoneCode));
    }

    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        if (zoneRepository.existsByZoneCode(request.zoneCode())) {
            throw new ZoneConflictException("Zone code already exists: " + request.zoneCode());
        }
        PricingZone zone = PricingZone.builder()
                .zoneCode(request.zoneCode())
                .zoneName(request.zoneName())
                .tier(request.tier())
                .baseFee(request.baseFee())
                .perKgFee(request.perKgFee())
                .expressMultiplier(request.expressMultiplier())
                .sameDayMultiplier(request.sameDayMultiplier())
                .status(request.status() == null ? DEFAULT_STATUS : request.status())
                .build();
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse updateZone(String zoneCode, ZoneRequest request) {
        if (!zoneCode.equals(request.zoneCode())) {
            throw new IllegalArgumentException("Zone code cannot be changed");
        }
        PricingZone zone = findZone(zoneCode);
        zone.setZoneName(request.zoneName());
        zone.setTier(request.tier());
        zone.setBaseFee(request.baseFee());
        zone.setPerKgFee(request.perKgFee());
        zone.setExpressMultiplier(request.expressMultiplier());
        zone.setSameDayMultiplier(request.sameDayMultiplier());
        if (request.status() != null) {
            zone.setStatus(request.status());
        }
        zone.setUpdatedAt(Instant.now());
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public void deleteZone(String zoneCode) {
        PricingZone zone = findZone(zoneCode);
        if (mappingRepository.existsByZoneCode(zoneCode)) {
            throw new ZoneConflictException("Zone still has districts mapped to it: " + zoneCode);
        }
        zoneRepository.delete(zone);
    }

    @Transactional(readOnly = true)
    public List<DistrictMappingResponse> listMappings() {
        return mappingRepository.findAll().stream().map(DistrictMappingResponse::from).toList();
    }

    @Transactional
    public DistrictMappingResponse assignDistrict(String district, DistrictMappingRequest request) {
        String normalized = SriLankanDistrict.valueOf(district.toUpperCase()).name();
        findZone(request.zoneCode());
        DistrictZoneMapping mapping = mappingRepository.findByDistrict(normalized)
                .orElseGet(() -> DistrictZoneMapping.builder().district(normalized).build());
        mapping.setZoneCode(request.zoneCode());
        return DistrictMappingResponse.from(mappingRepository.save(mapping));
    }

    private PricingZone findZone(String zoneCode) {
        return zoneRepository.findByZoneCode(zoneCode)
                .orElseThrow(() -> new ZoneNotFoundException(zoneCode));
    }
}
