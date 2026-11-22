package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.delivery.domain.entity.CashEntry;
import com.swiftdrop.delivery.domain.entity.CashHandover;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.enums.CashEntryType;
import com.swiftdrop.delivery.domain.enums.CashKind;
import com.swiftdrop.delivery.domain.repository.CashEntryRepository;
import com.swiftdrop.delivery.domain.repository.CashHandoverRepository;
import com.swiftdrop.delivery.dto.CashEntryResponse;
import com.swiftdrop.delivery.dto.CashHandoverResponse;
import com.swiftdrop.delivery.dto.CourierCashResponse;
import com.swiftdrop.delivery.exception.CashConflictException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The courier's side of the cash: collecting it and handing it to their dispatcher. */
@Service
public class CourierCashService {

    private static final int RECENT_HANDOVERS = 10;

    private final CashEntryRepository entryRepository;
    private final CashHandoverRepository handoverRepository;

    public CourierCashService(CashEntryRepository entryRepository, CashHandoverRepository handoverRepository) {
        this.entryRepository = entryRepository;
        this.handoverRepository = handoverRepository;
    }

    /** Joins the delivery step's transaction. The courier on the delivery holds the cash, whoever took the step. */
    @Transactional
    public void recordCollection(DeliverySession session, CashDue due, CallerContext caller) {
        SriLankanDistrict district = due.kind() == CashKind.DELIVERY_FEE
                ? session.getPickupDistrict() : session.getDeliveryDistrict();
        if (district == null && caller != null) {
            district = caller.districtId(); // sessions from before districts were recorded
        }
        entryRepository.save(new CashEntry(CashEntryType.COLLECTION, due.kind(), session, session.getCourierId(),
                session.getCourierName(), district, due.amount(), due.currency(), null,
                caller != null ? caller.userId() : null));
    }

    @Transactional(readOnly = true)
    public CourierCashResponse mine(CallerContext caller) {
        caller.requireRole(UserRole.COURIER);
        List<CashEntry> entries = entryRepository.findInHand(caller.userId());
        BigDecimal fees = sum(entries, CashKind.DELIVERY_FEE);
        BigDecimal cod = sum(entries, CashKind.COD);
        return new CourierCashResponse(fees.add(cod), fees, cod, "LKR",
                entries.stream().map(CashEntryResponse::of).toList(),
                handoverRepository.findByCourierIdOrderBySubmittedAtDesc(
                                caller.userId(), PageRequest.of(0, RECENT_HANDOVERS)).stream()
                        .map(CashHandoverResponse::of).toList());
    }

    /** Everything the courier holds, in one handover to the dispatcher of their district. */
    @Transactional
    public CashHandoverResponse submit(CallerContext caller) {
        caller.requireRole(UserRole.COURIER);
        if (caller.districtId() == null) {
            throw new ForbiddenException("Your account has no district");
        }
        List<CashEntry> entries = entryRepository.findInHand(caller.userId());
        if (entries.isEmpty()) {
            throw new CashConflictException("You have no cash to hand over.");
        }
        BigDecimal total = entries.stream().map(CashEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        CashHandover handover = handoverRepository.saveAndFlush(new CashHandover(
                caller.userId(), entries.getFirst().getCourierName(), caller.districtId(), total));
        int attached = entryRepository.attachToHandover(handover.getHandoverId(),
                entries.stream().map(CashEntry::getEntryId).toList());
        if (attached != entries.size()) {
            throw new CashConflictException("Your cash changed while you were handing it over. Check it and try again.");
        }
        return CashHandoverResponse.of(handover);
    }

    @Transactional(readOnly = true)
    public BigDecimal inHand(UUID courierId) {
        return entryRepository.sumInHand(courierId);
    }

    private static BigDecimal sum(List<CashEntry> entries, CashKind kind) {
        return entries.stream().filter(entry -> entry.getKind() == kind)
                .map(CashEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
