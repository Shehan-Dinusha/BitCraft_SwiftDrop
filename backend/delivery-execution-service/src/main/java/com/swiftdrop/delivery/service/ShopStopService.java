package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.entity.MerchantPayment;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.domain.repository.MerchantPaymentRepository;
import com.swiftdrop.delivery.dto.CollectShopRequest;
import com.swiftdrop.delivery.dto.CollectShopResponse;
import com.swiftdrop.delivery.dto.CourierJobStop;
import com.swiftdrop.delivery.dto.MerchantPaymentResponse;
import com.swiftdrop.delivery.dto.ShopParcel;
import com.swiftdrop.delivery.dto.ShopStopResponse;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.CashConflictException;
import com.swiftdrop.delivery.statemachine.HubRoute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One visit to a shop: the courier sees all its parcels, takes the ones that are there and paid for, and
 * confirms one amount. The pickups, the ledger entries and the payment are saved together. Parcels left out
 * stay with the courier for later.
 *
 * <p>The order service is asked before the transaction starts, so no connection is held while it answers.</p>
 */
@Service
public class ShopStopService {

    private final DeliverySessionRepository sessions;
    private final MerchantPaymentRepository payments;
    private final OrderJobClient orders;
    private final DeliveryExecutionService deliveries;
    private final TransactionTemplate transaction;

    public ShopStopService(DeliverySessionRepository sessions, MerchantPaymentRepository payments, OrderJobClient orders,
                           DeliveryExecutionService deliveries, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.payments = payments;
        this.orders = orders;
        this.deliveries = deliveries;
        this.transaction = transaction;
    }

    public ShopStopResponse stop(CallerContext caller, UUID merchantId) {
        List<DeliverySession> waiting = waiting(caller, merchantId);
        Map<UUID, OrderJobClient.OrderJob> jobs = jobs(waiting);
        Map<String, List<DeliverySession>> byAddress = new LinkedHashMap<>();
        for (DeliverySession session : waiting) {
            OrderJobClient.OrderJob job = jobs.get(session.getOrderId());
            if (job != null && !job.cancelled()) {
                byAddress.computeIfAbsent(addressKey(job.pickup()), key -> new ArrayList<>()).add(session);
            }
        }
        return new ShopStopResponse(
                merchantId,
                waiting.stream().map(DeliverySession::getMerchantName).filter(name -> name != null).findFirst().orElse(null),
                "LKR",
                byAddress.values().stream().map(group -> new ShopStopResponse.Address(
                        jobs.get(group.getFirst().getOrderId()).pickup(),
                        group.stream().map(session -> parcel(session, jobs.get(session.getOrderId()))).toList(),
                        total(group, jobs))).toList());
    }

    public CollectShopResponse collect(CallerContext caller, UUID merchantId, CollectShopRequest request) {
        List<UUID> chosen = request.deliveryIds().stream().distinct().toList();
        List<DeliverySession> taken = waiting(caller, merchantId).stream()
                .filter(session -> chosen.contains(session.getDeliveryId())).toList();
        if (taken.size() != chosen.size()) {
            throw new CashConflictException("One of these parcels is no longer yours to collect. Refresh and try again.");
        }
        Map<UUID, OrderJobClient.OrderJob> jobs = jobs(taken);
        for (DeliverySession session : taken) {
            OrderJobClient.OrderJob job = jobs.get(session.getOrderId());
            if (job == null || job.cancelled()) {
                throw new CashConflictException("%s was cancelled. Leave it at the shop and refresh."
                        .formatted(session.getTrackingNumber()));
            }
        }
        BigDecimal total = total(taken, jobs);
        if (request.cashCollected().compareTo(total) != 0) {
            throw new CashConflictException("Collect LKR %,.2f for these parcels, then confirm it.".formatted(total));
        }
        return transaction.execute(status -> {
            MerchantPayment payment = total.signum() > 0
                    ? payments.saveAndFlush(new MerchantPayment(taken.getFirst(), total, "LKR", taken.size())) : null;
            for (DeliverySession session : taken) {
                BigDecimal fee = fee(jobs.get(session.getOrderId()));
                deliveries.transition(session.getDeliveryId(),
                        new TransitionDeliveryRequest(DeliveryState.PICKED_UP, "Collected at the shop", null, "COURIER"),
                        caller, fee.signum() > 0 ? new CashDue(fee, "LKR", payment.getPaymentId()) : null);
            }
            return new CollectShopResponse(taken.size(), payment == null ? null : MerchantPaymentResponse.of(payment));
        });
    }

    private List<DeliverySession> waiting(CallerContext caller, UUID merchantId) {
        caller.requireRole(UserRole.COURIER);
        return sessions.findByCourierIdAndMerchantIdAndCurrentStateOrderByCreatedAtAsc(
                caller.userId(), merchantId, DeliveryState.ASSIGNED);
    }

    private Map<UUID, OrderJobClient.OrderJob> jobs(List<DeliverySession> list) {
        return orders.getAll(list.stream().map(DeliverySession::getOrderId).toList());
    }

    private static ShopParcel parcel(DeliverySession session, OrderJobClient.OrderJob job) {
        return new ShopParcel(session.getDeliveryId(), session.getTrackingNumber(), job.itemDescription(), job.weightKg(),
                session.getDeliveryDistrict(), HubRoute.goesViaHub(session), fee(job));
    }

    private static BigDecimal total(List<DeliverySession> list, Map<UUID, OrderJobClient.OrderJob> jobs) {
        return list.stream().map(session -> fee(jobs.get(session.getOrderId()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal fee(OrderJobClient.OrderJob job) {
        return job.fee() != null && job.fee().signum() > 0 ? job.fee() : BigDecimal.ZERO;
    }

    private static String addressKey(CourierJobStop stop) {
        return stop == null ? "" : (stop.addressLine1() + "|" + stop.city() + "|" + stop.postalCode()).toLowerCase();
    }
}
