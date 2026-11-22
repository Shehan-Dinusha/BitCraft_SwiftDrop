package com.swiftdrop.delivery.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What a shop paid at one pickup visit: the fees of the parcels the courier took. Never changes. */
@Entity
@Table(name = "merchant_payments")
public class MerchantPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "merchant_name", updatable = false, length = 150)
    private String merchantName;

    @Column(name = "courier_id", updatable = false)
    private UUID courierId;

    @Column(name = "courier_name", updatable = false, length = 128)
    private String courierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "district", updatable = false, length = 64)
    private SriLankanDistrict district;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 8)
    private String currency;

    @Column(name = "parcels", nullable = false, updatable = false)
    private int parcels;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    protected MerchantPayment() {
    }

    public MerchantPayment(DeliverySession session, BigDecimal amount, String currency, int parcels) {
        this.paymentId = UUID.randomUUID();
        this.merchantId = session.getMerchantId();
        this.merchantName = session.getMerchantName();
        this.courierId = session.getCourierId();
        this.courierName = session.getCourierName();
        this.district = session.getPickupDistrict();
        this.amount = amount;
        this.currency = currency != null ? currency : "LKR";
        this.parcels = parcels;
        this.paidAt = Instant.now();
    }

    public UUID getPaymentId() { return paymentId; }
    public UUID getMerchantId() { return merchantId; }
    public String getMerchantName() { return merchantName; }
    public UUID getCourierId() { return courierId; }
    public String getCourierName() { return courierName; }
    public SriLankanDistrict getDistrict() { return district; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public int getParcels() { return parcels; }
    public Instant getPaidAt() { return paidAt; }
}
