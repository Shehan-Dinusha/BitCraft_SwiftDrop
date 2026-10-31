package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.dto.CourierLocationPingRequest;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CourierShiftService {

    private static final Logger log = LoggerFactory.getLogger(CourierShiftService.class);

    private final CourierShiftRepository courierShiftRepository;

    public CourierShiftService(CourierShiftRepository courierShiftRepository) {
        this.courierShiftRepository = courierShiftRepository;
    }

    @Transactional
    public CourierShiftResponse startShift(StartShiftRequest request) {
        log.info("Starting shift for courier: {} ({}) in district: {}",
                request.courierName(), request.courierId(), request.initialDistrict());

        CourierShift shift = courierShiftRepository.findByCourierId(request.courierId())
                .orElseGet(() -> {
                    CourierShift newShift = new CourierShift();
                    newShift.setCourierId(request.courierId());
                    newShift.setActiveParcelsCount(0);
                    return newShift;
                });

        shift.setCourierName(request.courierName());
        if (request.phone() != null) shift.setPhone(request.phone());
        if (request.vehicleType() != null) shift.setVehicleType(request.vehicleType());
        if (request.maxCapacity() != null) shift.setMaxCapacity(request.maxCapacity());
        shift.setCurrentDistrict(request.initialDistrict());
        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        shift.setShiftStartedAt(Instant.now());
        shift.setShiftEndedAt(null);
        shift.setLastHeartbeatAt(Instant.now());

        shift = courierShiftRepository.save(shift);
        return CourierShiftResponse.fromEntity(shift);
    }

    @Transactional
    public CourierShiftResponse endShift(UUID courierId) {
        log.info("Ending shift for courier: {}", courierId);

        CourierShift shift = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));

        if (shift.getActiveParcelsCount() > 0) {
            throw new IllegalStateException("Cannot end shift: courier still has "
                    + shift.getActiveParcelsCount() + " active parcels assigned");
        }

        shift.setShiftStatus(ShiftStatus.OFFLINE);
        shift.setShiftEndedAt(Instant.now());
        shift = courierShiftRepository.save(shift);
        return CourierShiftResponse.fromEntity(shift);
    }

    @Transactional
    public CourierShiftResponse updateStatus(UUID courierId, ShiftStatus status) {
        log.info("Updating shift status for courier: {} to {}", courierId, status);

        CourierShift shift = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));

        shift.setShiftStatus(status);
        shift.setLastHeartbeatAt(Instant.now());
        shift = courierShiftRepository.save(shift);
        return CourierShiftResponse.fromEntity(shift);
    }

    @Transactional
    public CourierShiftResponse updateLocation(CourierLocationPingRequest request) {
        log.debug("Heartbeat location ping for courier: {} at {},{}",
                request.courierId(), request.latitude(), request.longitude());

        CourierShift shift = courierShiftRepository.findByCourierId(request.courierId())
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + request.courierId()));

        shift.setCurrentDistrict(request.district());
        if (request.latitude() != null) shift.setLatitude(request.latitude());
        if (request.longitude() != null) shift.setLongitude(request.longitude());
        shift.setLastHeartbeatAt(Instant.now());

        shift = courierShiftRepository.save(shift);
        return CourierShiftResponse.fromEntity(shift);
    }

    @Transactional(readOnly = true)
    public List<CourierShiftResponse> getCouriers(SriLankanDistrict district, ShiftStatus status) {
        List<CourierShift> list;
        if (district != null && status != null) {
            list = courierShiftRepository.findByCurrentDistrictAndShiftStatus(district, status);
        } else if (district != null) {
            list = courierShiftRepository.findByCurrentDistrict(district);
        } else if (status != null) {
            list = courierShiftRepository.findByShiftStatus(status);
        } else {
            list = courierShiftRepository.findAll();
        }
        return list.stream().map(CourierShiftResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public CourierShiftResponse getCourier(UUID courierId) {
        return courierShiftRepository.findByCourierId(courierId)
                .map(CourierShiftResponse::fromEntity)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));
    }
}
