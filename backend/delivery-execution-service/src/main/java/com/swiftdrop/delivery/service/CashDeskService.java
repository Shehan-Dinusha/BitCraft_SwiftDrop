package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.delivery.domain.entity.CashDeposit;
import com.swiftdrop.delivery.domain.entity.CashEntry;
import com.swiftdrop.delivery.domain.entity.CashHandover;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.enums.CashDepositStatus;
import com.swiftdrop.delivery.domain.enums.CashEntryType;
import com.swiftdrop.delivery.domain.enums.CashHandoverStatus;
import com.swiftdrop.delivery.domain.repository.CashDepositRepository;
import com.swiftdrop.delivery.domain.repository.CashEntryRepository;
import com.swiftdrop.delivery.domain.repository.CashHandoverRepository;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.CashDepositResponse;
import com.swiftdrop.delivery.dto.CashDeskResponse;
import com.swiftdrop.delivery.dto.CashEntryResponse;
import com.swiftdrop.delivery.dto.CashHandoverResponse;
import com.swiftdrop.delivery.dto.CashHoldingResponse;
import com.swiftdrop.delivery.dto.CountHandoverRequest;
import com.swiftdrop.delivery.dto.CreateDepositRequest;
import com.swiftdrop.delivery.dto.RefundFeeRequest;
import com.swiftdrop.delivery.dto.RejectDepositRequest;
import com.swiftdrop.delivery.exception.CashConflictException;
import com.swiftdrop.delivery.exception.CashRecordNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The hub's side of the cash. A dispatcher counts and banks only their own district's cash; a SYSTEM_ADMIN
 * sees every district and confirms the deposits, so nobody confirms their own.
 */
@Service
public class CashDeskService {

    private static final PageRequest RECENT = PageRequest.of(0, 50);

    /** The only reasons a sender gets the fee back: SwiftDrop damaged the parcel, or (OTHER) lost it. */
    private static final Set<String> REFUNDABLE = Set.of(
            DeliveryFailureReason.DAMAGED.name(),
            DeliveryFailureReason.DAMAGED_IN_TRANSIT.name(),
            DeliveryFailureReason.OTHER.name());

    private final CashEntryRepository entryRepository;
    private final CashHandoverRepository handoverRepository;
    private final CashDepositRepository depositRepository;
    private final DeliverySessionRepository deliverySessionRepository;

    public CashDeskService(CashEntryRepository entryRepository, CashHandoverRepository handoverRepository,
                           CashDepositRepository depositRepository, DeliverySessionRepository deliverySessionRepository) {
        this.entryRepository = entryRepository;
        this.handoverRepository = handoverRepository;
        this.depositRepository = depositRepository;
        this.deliverySessionRepository = deliverySessionRepository;
    }

    @Transactional(readOnly = true)
    public CashDeskResponse desk(CallerContext caller, SriLankanDistrict requested) {
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        SriLankanDistrict district = caller.role() == UserRole.DISPATCHER ? ownDistrict(caller) : requested;
        List<CashHandover> atHub = handoverRepository.findAtHub(district);
        List<CashEntry> refunds = entryRepository.findOpenRefunds(district);
        return new CashDeskResponse(
                district,
                "LKR",
                entryRepository.findHoldings(district).stream().map(CashHoldingResponse::of).toList(),
                handoverRepository.findByDistrictAndStatus(district, CashHandoverStatus.SUBMITTED, RECENT).stream()
                        .map(CashHandoverResponse::of).toList(),
                atHub.stream().map(CashHandoverResponse::of).toList(),
                refunds.stream().map(CashEntryResponse::of).toList(),
                counted(atHub).subtract(total(refunds)),
                depositRepository.findRecent(district, RECENT).stream().map(CashDepositResponse::of).toList());
    }

    /** The parcels a handover came from, for checking a difference. */
    @Transactional(readOnly = true)
    public List<CashEntryResponse> handoverEntries(CallerContext caller, UUID handoverId) {
        CashHandover handover = handover(handoverId);
        if (caller.role() == UserRole.COURIER) {
            caller.requireSelfOrRole(handover.getCourierId());
        } else {
            requireDesk(caller, handover.getDistrict());
        }
        return entryRepository.findByHandoverIdOrderByRecordedAtDesc(handoverId).stream()
                .map(CashEntryResponse::of).toList();
    }

    @Transactional
    public CashHandoverResponse count(CallerContext caller, UUID handoverId, CountHandoverRequest request) {
        CashHandover handover = handover(handoverId);
        requireDispatcherOf(caller, handover.getDistrict());
        if (handover.getStatus() != CashHandoverStatus.SUBMITTED) {
            throw new CashConflictException("This handover was already counted.");
        }
        String note = blankToNull(request.note());
        if (request.countedAmount().compareTo(handover.getExpectedAmount()) != 0 && note == null) {
            throw new CashConflictException("The amount differs from what the courier collected. Add a note that says why.");
        }
        handover.count(request.countedAmount(), note, caller.userId());
        return CashHandoverResponse.of(handoverRepository.saveAndFlush(handover));
    }

    /** Banks everything at the dispatcher's hub. */
    @Transactional
    public CashDepositResponse deposit(CallerContext caller, CreateDepositRequest request) {
        caller.requireRole(UserRole.DISPATCHER);
        SriLankanDistrict district = ownDistrict(caller);
        List<CashHandover> atHub = handoverRepository.findAtHub(district);
        List<CashEntry> refunds = entryRepository.findOpenRefunds(district);
        BigDecimal hubCash = counted(atHub).subtract(total(refunds));
        if (hubCash.signum() <= 0) {
            throw new CashConflictException("There is no cash at the hub to deposit.");
        }
        if (hubCash.compareTo(request.amount()) != 0) {
            throw new CashConflictException("The cash at the hub changed to LKR %,.2f. Check it and try again.".formatted(hubCash));
        }
        CashDeposit deposit = depositRepository.saveAndFlush(
                new CashDeposit(district, hubCash, request.reference().trim(), caller.userId()));
        int handovers = handoverRepository.attachToDeposit(deposit.getDepositId(),
                atHub.stream().map(CashHandover::getHandoverId).toList());
        int refunded = refunds.isEmpty() ? 0 : entryRepository.attachRefundsToDeposit(deposit.getDepositId(),
                refunds.stream().map(CashEntry::getEntryId).toList());
        if (handovers != atHub.size() || refunded != refunds.size()) {
            throw new CashConflictException("The cash at the hub changed. Check it and try again.");
        }
        return CashDepositResponse.of(deposit);
    }

    @Transactional
    public CashDepositResponse confirm(CallerContext caller, UUID depositId) {
        CashDeposit deposit = openDeposit(caller, depositId);
        deposit.review(CashDepositStatus.CONFIRMED, null, caller.userId());
        return CashDepositResponse.of(depositRepository.saveAndFlush(deposit));
    }

    /** The cash goes back to the hub, so the dispatcher can bank it again with the right slip. */
    @Transactional
    public CashDepositResponse reject(CallerContext caller, UUID depositId, RejectDepositRequest request) {
        CashDeposit deposit = openDeposit(caller, depositId);
        deposit.review(CashDepositStatus.REJECTED, request.reason().trim(), caller.userId());
        depositRepository.saveAndFlush(deposit);
        handoverRepository.release(depositId);
        entryRepository.releaseRefunds(depositId);
        return CashDepositResponse.of(deposit);
    }

    /**
     * Gives the sender their fee back, from the cash at the pickup district's hub. Only when the delivery
     * failed because SwiftDrop lost or damaged the parcel.
     */
    @Transactional
    public CashEntryResponse refund(CallerContext caller, RefundFeeRequest request) {
        caller.requireRole(UserRole.DISPATCHER);
        SriLankanDistrict district = ownDistrict(caller);
        DeliverySession session = deliverySessionRepository.findByTrackingNumber(request.trackingNumber().trim().toUpperCase())
                .orElseThrow(() -> new CashRecordNotFoundException("No parcel with this tracking number."));
        CashEntry fee = entryRepository.findByDeliveryIdAndEntryType(
                        session.getDeliveryId(), CashEntryType.COLLECTION)
                .filter(entry -> entry.getDistrict() == district)
                .orElseThrow(() -> new CashRecordNotFoundException("Your district collected no fee for this parcel."));
        if (session.getCurrentState() != DeliveryState.FAILED || !REFUNDABLE.contains(session.getFailedReason())) {
            throw new CashConflictException(
                    "Only a delivery that failed because the parcel was lost or damaged can be refunded.");
        }
        if (entryRepository.findByDeliveryIdAndEntryType(
                session.getDeliveryId(), CashEntryType.REFUND).isPresent()) {
            throw new CashConflictException("This fee was already refunded.");
        }
        BigDecimal hubCash = counted(handoverRepository.findAtHub(district)).subtract(entryRepository.sumOpenRefunds(district));
        if (hubCash.compareTo(fee.getAmount()) < 0) {
            throw new CashConflictException("There is not enough counted cash at the hub for this refund.");
        }
        return CashEntryResponse.of(entryRepository.saveAndFlush(new CashEntry(CashEntryType.REFUND,
                session, fee.getCourierId(), fee.getCourierName(), district, fee.getAmount(), fee.getCurrency(),
                request.reason().trim(), caller.userId(), null)));
    }

    private CashHandover handover(UUID handoverId) {
        return handoverRepository.findByHandoverId(handoverId)
                .orElseThrow(() -> new CashRecordNotFoundException("Handover not found."));
    }

    private CashDeposit openDeposit(CallerContext caller, UUID depositId) {
        caller.requireRole(UserRole.SYSTEM_ADMIN);
        CashDeposit deposit = depositRepository.findByDepositId(depositId)
                .orElseThrow(() -> new CashRecordNotFoundException("Deposit not found."));
        if (deposit.getStatus() != CashDepositStatus.SUBMITTED) {
            throw new CashConflictException("This deposit was already reviewed.");
        }
        return deposit;
    }

    private static void requireDesk(CallerContext caller, SriLankanDistrict district) {
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        if (caller.role() == UserRole.DISPATCHER) {
            requireDispatcherOf(caller, district);
        }
    }

    private static void requireDispatcherOf(CallerContext caller, SriLankanDistrict district) {
        caller.requireRole(UserRole.DISPATCHER);
        if (ownDistrict(caller) != district) {
            throw new ForbiddenException("This cash belongs to another district");
        }
    }

    private static SriLankanDistrict ownDistrict(CallerContext caller) {
        if (caller.districtId() == null) {
            throw new ForbiddenException("Your account has no district");
        }
        return caller.districtId();
    }

    private static BigDecimal counted(List<CashHandover> handovers) {
        return handovers.stream().map(CashHandover::getCountedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(List<CashEntry> entries) {
        return entries.stream().map(CashEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
