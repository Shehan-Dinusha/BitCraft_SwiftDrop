package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantOrderReceipt;
import com.swiftdrop.merchant.domain.repository.MerchantOrderReceiptRepository;
import com.swiftdrop.merchant.dto.MerchantPickupResponse;
import com.swiftdrop.merchant.dto.MerchantPickupState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The shop says its parcels are packed; the dispatcher then sends one courier for them. */
@Service
@RequiredArgsConstructor
public class MerchantPickupService {

    private final MerchantOrderReceiptRepository receipts;
    private final MerchantApprovalGuard approvalGuard;
    private final DispatchPickupClient dispatch;

    public MerchantPickupResponse current(UUID merchantId) {
        approvalGuard.requireApproved(merchantId);
        return response(merchantId, dispatch.atShop(merchantId));
    }

    public MerchantPickupResponse ready(UUID merchantId) {
        MerchantAccount merchant = approvalGuard.requireApproved(merchantId);
        MerchantPickupResponse pickup = response(merchantId, dispatch.markReady(merchantId, merchant.getBusinessName()));
        if (pickup.parcels().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There are no parcels to pick up yet. Book an order first.");
        }
        return pickup;
    }

    private MerchantPickupResponse response(UUID merchantId, List<DispatchPickupClient.Parcel> atShop) {
        Map<UUID, MerchantOrderReceipt> byOrder = atShop.isEmpty() ? Map.of()
                : receipts.findByMerchantIdAndOrderIdIn(merchantId,
                                atShop.stream().map(DispatchPickupClient.Parcel::orderId).toList()).stream()
                        .collect(Collectors.toMap(MerchantOrderReceipt::getOrderId, Function.identity(), (a, b) -> a));
        List<MerchantPickupResponse.Parcel> parcels = atShop.stream().map(parcel -> {
            MerchantOrderReceipt receipt = byOrder.get(parcel.orderId());
            return new MerchantPickupResponse.Parcel(parcel.orderId(), parcel.trackingNumber(),
                    receipt == null ? null : receipt.getExternalRef(), parcel.state(), parcel.courierName(), parcel.readyAt(),
                    receipt == null ? null : receipt.getFee());
        }).toList();
        return new MerchantPickupResponse(group(parcels, MerchantPickupState.WAITING), group(parcels, MerchantPickupState.READY),
                group(parcels, MerchantPickupState.ASSIGNED), "LKR", parcels);
    }

    private static MerchantPickupResponse.Group group(List<MerchantPickupResponse.Parcel> parcels, MerchantPickupState state) {
        List<BigDecimal> fees = parcels.stream().filter(parcel -> parcel.state() == state)
                .map(MerchantPickupResponse.Parcel::fee).toList();
        return new MerchantPickupResponse.Group(fees.size(),
                fees.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                (int) fees.stream().filter(Objects::isNull).count());
    }
}
