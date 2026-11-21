package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.repository.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.CourierProfileResponse;
import com.swiftdrop.dispatch.dto.CourierVehicleRequest;
import com.swiftdrop.dispatch.dto.OrderCourierResponse;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.CourierProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Couriers' own vehicles. A courier registers their vehicle once; after that only a dispatcher of the
 * courier's district (or a SYSTEM_ADMIN) can change it. Every change is copied onto the courier's shift
 * row, which is what assignment reads.
 */
@Service
public class CourierProfileService {

    /**
     * Sri Lankan plates: optional province code, then letters or the older numeric series, a dash and
     * four digits, e.g. {@code WP BAB-1234}, {@code CAB-1234}, {@code 65-1234}, {@code 250-1234}.
     */
    private static final Pattern PLATE = Pattern.compile("^(?:[A-Z]{2} )?(?:[A-Z]{1,3}|\\d{1,3})-\\d{4}$");
    private static final Pattern LICENCE = Pattern.compile("^[A-Z0-9]{5,15}$");

    private final CourierProfileRepository profileRepository;
    private final CourierShiftRepository shiftRepository;
    private final DispatchAssignmentRepository assignmentRepository;

    public CourierProfileService(CourierProfileRepository profileRepository,
                                 CourierShiftRepository shiftRepository,
                                 DispatchAssignmentRepository assignmentRepository) {
        this.profileRepository = profileRepository;
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public CourierProfileResponse getOwn(CallerContext caller) {
        caller.requireRole(UserRole.COURIER);
        return profileRepository.findByCourierId(caller.userId())
                .map(CourierProfileResponse::fromEntity)
                .orElseThrow(CourierProfileException::notFound);
    }

    /** The courier registers their vehicle, once. The capacity is the vehicle type's default. */
    @Transactional
    public CourierProfileResponse registerOwn(CallerContext caller, CourierVehicleRequest request) {
        caller.requireRole(UserRole.COURIER);
        if (profileRepository.findByCourierId(caller.userId()).isPresent()) {
            throw new CourierProfileException(HttpStatus.CONFLICT,
                    "Your vehicle is already registered. Ask your dispatcher to change it.");
        }
        if (caller.districtId() == null) {
            throw new CourierProfileException(HttpStatus.CONFLICT,
                    "Your account has no district. Ask your dispatcher to fix your account.");
        }
        CourierProfile profile = new CourierProfile();
        profile.setCourierId(caller.userId());
        profile.setDistrict(caller.districtId());
        applyVehicle(profile, request);
        profile.setMaxCapacity(request.vehicleType().defaultCapacity());
        return save(profile);
    }

    /** Profiles a dispatcher may see: their own district; a SYSTEM_ADMIN sees all or one district. */
    @Transactional(readOnly = true)
    public List<CourierProfileResponse> list(CallerContext caller, SriLankanDistrict requestedDistrict) {
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        SriLankanDistrict district = caller.role() == UserRole.DISPATCHER ? requireOwnDistrict(caller) : requestedDistrict;
        List<CourierProfile> profiles = district == null ? profileRepository.findAll() : profileRepository.findByDistrict(district);
        return profiles.stream().map(CourierProfileResponse::fromEntity).toList();
    }

    /**
     * A dispatcher corrects a courier's vehicle. Without an explicit capacity, a changed vehicle type
     * resets the capacity to the new type's default; an unchanged type keeps the current capacity.
     */
    @Transactional
    public CourierProfileResponse updateByOperations(CallerContext caller, UUID courierId, CourierVehicleRequest request) {
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        CourierProfile profile = profileRepository.findByCourierId(courierId)
                .orElseThrow(CourierProfileException::notFound);
        if (caller.role() == UserRole.DISPATCHER && profile.getDistrict() != requireOwnDistrict(caller)) {
            throw new ForbiddenException("This courier belongs to another district");
        }
        boolean typeChanged = profile.getVehicleType() != request.vehicleType();
        applyVehicle(profile, request);
        if (request.maxCapacity() != null) {
            profile.setMaxCapacity(request.maxCapacity());
        } else if (typeChanged) {
            profile.setMaxCapacity(request.vehicleType().defaultCapacity());
        }
        return save(profile);
    }

    /**
     * The courier handling an order, for the customer (or merchant) who owns it. Only while a courier
     * is actively assigned; otherwise {@code null}.
     */
    @Transactional(readOnly = true)
    public OrderCourierResponse courierForOrder(CallerContext caller, UUID orderId) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AssignmentNotFoundException("No dispatch record for this order"));
        requireOrderAccess(caller, assignment);

        boolean active = assignment.getStatus() == AssignmentStatus.ASSIGNED
                || assignment.getStatus() == AssignmentStatus.REASSIGNED;
        if (!active || assignment.getCourierId() == null) {
            return null;
        }
        CourierProfile profile = profileRepository.findByCourierId(assignment.getCourierId()).orElse(null);
        return new OrderCourierResponse(
                firstName(assignment.getCourierName()),
                profile == null ? null : profile.getVehicleType(),
                profile == null ? null : profile.getPlateNumber(),
                profile == null ? null : profile.getVehicleModel(),
                profile == null ? null : profile.getVehicleColour());
    }

    /** Normalises {@code " wp bab 1234 "} to {@code "WP BAB-1234"}; rejects anything that is not a plate. */
    static String normalisePlate(String raw) {
        String plate = raw.trim().toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*-\\s*", "-")
                .replaceFirst("^(.*[A-Z0-9]) (\\d{4})$", "$1-$2");
        if (!PLATE.matcher(plate).matches()) {
            throw new IllegalArgumentException("Enter the number plate as shown on the vehicle, for example WP BAB-1234");
        }
        return plate;
    }

    static String normaliseLicence(String raw) {
        String licence = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!LICENCE.matcher(licence).matches()) {
            throw new IllegalArgumentException("Enter the driving licence number as printed on the licence");
        }
        return licence;
    }

    private void applyVehicle(CourierProfile profile, CourierVehicleRequest request) {
        String plate = normalisePlate(request.plateNumber());
        profileRepository.findByPlateNumber(plate)
                .filter(other -> !other.getCourierId().equals(profile.getCourierId()))
                .ifPresent(other -> {
                    throw new CourierProfileException(HttpStatus.CONFLICT,
                            "This number plate is already registered to another courier");
                });
        profile.setVehicleType(request.vehicleType());
        profile.setPlateNumber(plate);
        profile.setVehicleModel(request.vehicleModel().trim());
        profile.setVehicleColour(request.vehicleColour().trim());
        profile.setLicenceNumber(normaliseLicence(request.licenceNumber()));
    }

    /** Saves the profile and copies vehicle and capacity onto the courier's shift, if they have one. */
    private CourierProfileResponse save(CourierProfile profile) {
        CourierProfile saved = profileRepository.save(profile);
        shiftRepository.findByCourierId(saved.getCourierId()).ifPresent(shift -> {
            shift.setVehicleType(saved.getVehicleType());
            shift.setMaxCapacity(saved.getMaxCapacity());
            shiftRepository.save(shift);
        });
        return CourierProfileResponse.fromEntity(saved);
    }

    private static SriLankanDistrict requireOwnDistrict(CallerContext caller) {
        if (caller.districtId() == null) {
            throw new ForbiddenException("Your dispatcher account has no district");
        }
        return caller.districtId();
    }

    private static void requireOrderAccess(CallerContext caller, DispatchAssignment assignment) {
        boolean allowed = switch (caller.role()) {
            case DISPATCHER, SYSTEM_ADMIN -> true;
            case CUSTOMER -> caller.userId().equals(assignment.getCustomerId());
            case MERCHANT -> caller.merchantId() != null && caller.merchantId().equals(assignment.getMerchantId());
            case COURIER -> caller.userId().equals(assignment.getCourierId());
        };
        if (!allowed) {
            throw new ForbiddenException("Caller cannot access this order");
        }
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+")[0];
    }
}
