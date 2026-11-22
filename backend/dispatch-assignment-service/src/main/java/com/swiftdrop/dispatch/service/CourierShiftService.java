package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.exception.ShiftEndNotAllowedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CourierShiftService {

    private static final Logger log = LoggerFactory.getLogger(CourierShiftService.class);

    private final CourierShiftRepository courierShiftRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final DispatchAssignmentRepository assignmentRepository;

    public CourierShiftService(CourierShiftRepository courierShiftRepository,
                               CourierProfileRepository courierProfileRepository,
                               DispatchAssignmentRepository assignmentRepository) {
        this.courierShiftRepository = courierShiftRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public CourierShiftResponse startShift(StartShiftRequest request) {
        log.info("Starting shift for courier: {} ({}) in district: {}",
                request.courierName(), request.courierId(), request.initialDistrict());

        // Couriers use their own registered vehicle; without one there is nothing to dispatch with.
        CourierProfile profile = courierProfileRepository.findByCourierId(request.courierId())
                .orElseThrow(() -> new CourierNotAvailableException(
                        "Add your vehicle details before starting a shift"));

        CourierShift shift = courierShiftRepository.findByCourierId(request.courierId())
                .orElseGet(() -> {
                    CourierShift newShift = new CourierShift();
                    newShift.setCourierId(request.courierId());
                    newShift.setActiveParcelsCount(0);
                    return newShift;
                });

        shift.setCourierName(request.courierName());
        if (request.phone() != null) shift.setPhone(request.phone());
        shift.setVehicleType(profile.getVehicleType());
        shift.setMaxCapacity(profile.getMaxCapacity());
        shift.setCurrentDistrict(request.initialDistrict());
        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        shift.setShiftStartedAt(Instant.now());
        shift.setShiftEndedAt(null);
        shift.setLastHeartbeatAt(Instant.now());

        shift = courierShiftRepository.save(shift);
        return toResponse(shift);
    }

    @Transactional
    public CourierShiftResponse endShift(UUID courierId) {
        log.info("Ending shift for courier: {}", courierId);

        CourierShift shift = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));

        if (shift.getActiveParcelsCount() > 0) {
            throw new ShiftEndNotAllowedException("Cannot end shift: the courier still has "
                    + shift.getActiveParcelsCount() + " active parcel(s). Complete or reassign them first.");
        }

        shift.setShiftStatus(ShiftStatus.OFFLINE);
        shift.setShiftEndedAt(Instant.now());
        shift = courierShiftRepository.save(shift);
        return toResponse(shift);
    }

    @Transactional
    public CourierShiftResponse updateStatus(UUID courierId, ShiftStatus status) {
        log.info("Updating shift status for courier: {} to {}", courierId, status);

        CourierShift shift = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));

        shift.setShiftStatus(status);
        shift.setLastHeartbeatAt(Instant.now());
        shift = courierShiftRepository.save(shift);
        return toResponse(shift);
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
        Map<UUID, CourierLoad> loads = CourierLoad.all(assignmentRepository,
                list.stream().map(CourierShift::getCourierId).toList());
        return list.stream()
                .map(shift -> CourierShiftResponse.fromEntity(shift,
                        loads.getOrDefault(shift.getCourierId(), CourierLoad.NONE)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourierShiftResponse getCourier(UUID courierId) {
        return courierShiftRepository.findByCourierId(courierId)
                .map(this::toResponse)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));
    }

    private CourierShiftResponse toResponse(CourierShift shift) {
        return CourierShiftResponse.fromEntity(shift, CourierLoad.of(assignmentRepository, shift.getCourierId()));
    }

    @Transactional(readOnly = true)
    public List<CourierWorkloadResponse> getWorkloads() {
        return getWorkloads(null);
    }

    /** Workload of the couriers in {@code district}, or of all couriers when it is {@code null}. */
    @Transactional(readOnly = true)
    public List<CourierWorkloadResponse> getWorkloads(SriLankanDistrict district) {
        return (district == null ? courierShiftRepository.findAll() : courierShiftRepository.findByCurrentDistrict(district))
                .stream()
                .map(CourierWorkloadResponse::fromEntity)
                .toList();
    }
}
