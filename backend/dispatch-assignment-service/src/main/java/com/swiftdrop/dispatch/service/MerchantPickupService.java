package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.AssignShopPickupRequest;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.InternalMerchantParcel;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.MerchantPickupState;
import com.swiftdrop.dispatch.dto.ShopCourierSuggestionResponse;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.security.DistrictScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A shop's parcels, collected in one visit. The shop says when they are packed; only then can a dispatcher
 * send a courier, for all of them at once.
 */
@Service
public class MerchantPickupService {

    private final DispatchAssignmentRepository assignmentRepository;
    private final CourierShiftRepository courierShiftRepository;
    private final ManualOverrideService manualOverrideService;

    public MerchantPickupService(DispatchAssignmentRepository assignmentRepository,
                                 CourierShiftRepository courierShiftRepository,
                                 ManualOverrideService manualOverrideService) {
        this.assignmentRepository = assignmentRepository;
        this.courierShiftRepository = courierShiftRepository;
        this.manualOverrideService = manualOverrideService;
    }

    /** What the shop still holds. */
    @Transactional(readOnly = true)
    public List<InternalMerchantParcel> atShop(UUID merchantId) {
        return assignmentRepository.findAtShop(merchantId).stream().map(MerchantPickupService::parcel).toList();
    }

    /** Everything the shop has booked so far is packed. Parcels already marked keep their time. */
    @Transactional
    public List<InternalMerchantParcel> markReady(UUID merchantId, String merchantName) {
        Instant now = Instant.now();
        List<DispatchAssignment> parcels = assignmentRepository.findAtShop(merchantId);
        for (DispatchAssignment parcel : parcels) {
            parcel.setMerchantName(merchantName);
            if (parcel.getReadyAt() == null) {
                parcel.setReadyAt(now);
            }
        }
        assignmentRepository.saveAll(parcels);
        return parcels.stream().map(MerchantPickupService::parcel).toList();
    }

    /** The couriers of the district on shift, and which of the shop's ready parcels each has room for. */
    @Transactional(readOnly = true)
    public List<ShopCourierSuggestionResponse> suggestions(CallerContext caller, UUID merchantId, SriLankanDistrict requested) {
        SriLankanDistrict district = DistrictScope.listDistrict(caller, requested);
        if (district == null) {
            throw new IllegalArgumentException("Choose a district");
        }
        List<DispatchAssignment> ready = assignmentRepository.findAtShop(merchantId).stream()
                .filter(parcel -> parcel.getStatus() == AssignmentStatus.UNASSIGNED && parcel.isShopPickup()
                        && parcel.isReadyForPickup() && parcel.getPickupDistrict() == district)
                .toList();
        List<CourierShift> couriers = courierShiftRepository
                .findByCurrentDistrictAndShiftStatusNot(district, ShiftStatus.OFFLINE);
        Map<UUID, CourierLoad> loads = CourierLoad.all(assignmentRepository,
                couriers.stream().map(CourierShift::getCourierId).toList());
        return couriers.stream()
                .map(courier -> new ShopCourierSuggestionResponse(courier.getCourierId(), courier.getCourierName(),
                        courier.getVehicleType(), courier.getActiveParcelsCount(), courier.getMaxCapacity(),
                        CourierFit.fitting(courier, loads.getOrDefault(courier.getCourierId(), CourierLoad.NONE), ready)
                                .stream().map(DispatchAssignment::getOrderId).toList()))
                // Most of the shop's parcels first, then the smallest vehicle that takes them.
                .sorted(Comparator.comparingInt((ShopCourierSuggestionResponse option) -> -option.fittingOrderIds().size())
                        .thenComparingInt(option -> option.vehicleType().ordinal())
                        .thenComparingInt(ShopCourierSuggestionResponse::activeParcels)
                        .thenComparing(ShopCourierSuggestionResponse::courierId))
                .toList();
    }

    /** All or nothing: each parcel passes the same checks as a single assignment. */
    @Transactional
    public List<DispatchAssignmentResponse> assign(CallerContext caller, UUID merchantId, AssignShopPickupRequest request,
                                                   UUID correlationId) {
        UUID correlation = correlationId != null ? correlationId : UUID.randomUUID();
        List<UUID> atShop = assignmentRepository.findAtShop(merchantId).stream()
                .filter(DispatchAssignment::isShopPickup).map(DispatchAssignment::getOrderId).toList();
        List<DispatchAssignmentResponse> assigned = new ArrayList<>();
        for (UUID orderId : request.orderIds().stream().distinct().toList()) {
            if (!atShop.contains(orderId)) {
                throw new AssignmentConflictException(
                        "One of these parcels is no longer waiting at the shop. Refresh and try again.");
            }
            assigned.add(DispatchAssignmentResponse.fromEntity(manualOverrideService.manualAssign(
                    new ManualAssignRequest(orderId, request.courierId(), "DISPATCHER", "Shop pickup"), correlation, caller)));
        }
        return assigned;
    }

    private static InternalMerchantParcel parcel(DispatchAssignment assignment) {
        MerchantPickupState state = assignment.getStatus() != AssignmentStatus.UNASSIGNED ? MerchantPickupState.ASSIGNED
                : assignment.isReadyForPickup() ? MerchantPickupState.READY : MerchantPickupState.WAITING;
        return new InternalMerchantParcel(assignment.getOrderId(), assignment.getTrackingNumber(), state,
                assignment.getCourierName(), assignment.getReadyAt());
    }
}
