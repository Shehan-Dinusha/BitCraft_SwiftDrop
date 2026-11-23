package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.phone.SriLankanPhone;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.dispatch.client.CourierCashClient;
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
import com.swiftdrop.dispatch.exception.ShiftRuleException;
import com.swiftdrop.dispatch.security.DistrictScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourierShiftService {

    private static final Logger log = LoggerFactory.getLogger(CourierShiftService.class);

    private final CourierShiftRepository courierShiftRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final DispatchAssignmentRepository assignmentRepository;
    private final CourierCashClient courierCashClient;
    private final WorkingDay workingDay;

    public CourierShiftService(CourierShiftRepository courierShiftRepository,
                               CourierProfileRepository courierProfileRepository,
                               DispatchAssignmentRepository assignmentRepository,
                               CourierCashClient courierCashClient,
                               WorkingDay workingDay) {
        this.courierShiftRepository = courierShiftRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.assignmentRepository = assignmentRepository;
        this.courierCashClient = courierCashClient;
        this.workingDay = workingDay;
    }

    /**
     * Starts the courier's shift in their home district, never one named in the request: the parcels they
     * carry and the cash they collect belong to that district's dispatcher. Only on a working day, from 7:30
     * until closing ({@link WorkingDay}). Starting a shift that is already running changes nothing.
     */
    @Transactional
    public CourierShiftResponse startShift(StartShiftRequest request, CallerContext caller) {
        // Couriers use their own registered vehicle; without one there is nothing to dispatch with.
        CourierProfile profile = courierProfileRepository.findByCourierId(request.courierId())
                .orElseThrow(() -> new CourierNotAvailableException(
                        "Add your vehicle details before starting a shift"));
        // Their account decides the district; the profile keeps a copy, refreshed when an admin moved them.
        if (caller.role() == UserRole.COURIER && caller.districtId() != null
                && caller.districtId() != profile.getDistrict()) {
            profile.setDistrict(caller.districtId());
        }
        DistrictScope.requireCourier(caller, profile.getDistrict());
        log.info("Starting shift for courier: {} in district: {}", request.courierId(), profile.getDistrict());

        Optional<CourierShift> existing = courierShiftRepository.findByCourierId(request.courierId());
        if (existing.isPresent() && existing.get().getShiftStatus() != ShiftStatus.OFFLINE) {
            return toResponse(existing.get());
        }
        workingDay.requireShiftCanStart();

        CourierShift shift = existing
                .orElseGet(() -> {
                    CourierShift newShift = new CourierShift();
                    newShift.setCourierId(request.courierId());
                    newShift.setActiveParcelsCount(0);
                    return newShift;
                });

        shift.setCourierName(request.courierName());
        if (request.phone() != null) shift.setPhone(SriLankanPhone.toE164OrNull(request.phone()));
        shift.setVehicleType(profile.getVehicleType());
        shift.setMaxCapacity(profile.getMaxCapacity());
        shift.setCurrentDistrict(profile.getDistrict());
        shift.setShiftStatus(shift.workingStatus());
        Instant now = workingDay.now();
        shift.setShiftStartedAt(now);
        shift.setShiftEndedAt(null);
        shift.setLastHeartbeatAt(now);
        shift.setBreakStartedAt(null);
        shift.setBreakEndedAt(null);
        shift.setClosedAutomatically(false);

        shift = courierShiftRepository.save(shift);
        return toResponse(shift);
    }

    /**
     * A courier hands over all their cash before ending a shift. Operations may end it without: the cash stays
     * listed under the courier on the cash desk.
     */
    @Transactional
    public CourierShiftResponse endShift(UUID courierId, CallerContext caller) {
        log.info("Ending shift for courier: {}", courierId);

        CourierShift shift = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));

        if (shift.getActiveParcelsCount() > 0) {
            throw new ShiftEndNotAllowedException("Cannot end shift: the courier still has "
                    + shift.getActiveParcelsCount() + " active parcel(s). Complete or reassign them first.");
        }

        if (caller.role() == UserRole.COURIER) {
            BigDecimal inHand = courierCashClient.inHand(courierId);
            if (inHand.signum() > 0) {
                throw new ShiftEndNotAllowedException(
                        "Hand over your cash first: LKR %,.2f.".formatted(inHand));
            }
        }

        close(shift);
        shift = courierShiftRepository.save(shift);
        return toResponse(shift);
    }

    /**
     * Starts the lunch break (ON_BREAK) or ends it (AVAILABLE). A shift ends only through {@link #endShift},
     * which checks parcels and cash, and BUSY follows the parcel count, so neither can be set here. A courier on
     * break keeps their parcels and can still be given more.
     */
    @Transactional
    public CourierShiftResponse updateStatus(UUID courierId, ShiftStatus status) {
        log.info("Updating shift status for courier: {} to {}", courierId, status);

        CourierShift shift = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found: " + courierId));
        if (status == ShiftStatus.OFFLINE) {
            throw new ShiftRuleException("End the shift instead.");
        }
        if (status == ShiftStatus.BUSY) {
            throw new ShiftRuleException("Busy follows the parcels a courier holds and cannot be set.");
        }
        if (shift.getShiftStatus() == ShiftStatus.OFFLINE) {
            throw new ShiftRuleException("Start the shift first.");
        }

        Instant now = workingDay.now();
        if (status == ShiftStatus.ON_BREAK && shift.getShiftStatus() != ShiftStatus.ON_BREAK) {
            workingDay.requireBreakCanStart(shift);
            shift.setBreakStartedAt(now);
            shift.setShiftStatus(ShiftStatus.ON_BREAK);
        } else if (status == ShiftStatus.AVAILABLE && shift.getShiftStatus() == ShiftStatus.ON_BREAK) {
            shift.setBreakEndedAt(now);
            shift.setShiftStatus(shift.workingStatus());
        }
        shift.setLastHeartbeatAt(now);
        shift = courierShiftRepository.save(shift);
        return toResponse(shift);
    }

    /**
     * Nobody works at night: a shift still open is closed and flagged, so the courier does not look available
     * and the dispatcher sees who did not close properly. Runs every hour from 20:00 to 6:00, so a missed run
     * is caught by the next one; parcels and cash still held stay listed under the courier.
     */
    @Scheduled(cron = "0 0 20-23,0-6 * * *", zone = "Asia/Colombo")
    @Transactional
    public int closeForgottenShifts() {
        List<CourierShift> open = courierShiftRepository.findByShiftStatusNot(ShiftStatus.OFFLINE);
        for (CourierShift shift : open) {
            close(shift);
            shift.setClosedAutomatically(true);
            log.warn("Closed the shift courier {} left open", shift.getCourierId());
        }
        courierShiftRepository.saveAll(open);
        return open.size();
    }

    private void close(CourierShift shift) {
        Instant now = workingDay.now();
        if (shift.getShiftStatus() == ShiftStatus.ON_BREAK) {
            shift.setBreakEndedAt(now);
        }
        shift.setShiftStatus(ShiftStatus.OFFLINE);
        shift.setShiftEndedAt(now);
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
