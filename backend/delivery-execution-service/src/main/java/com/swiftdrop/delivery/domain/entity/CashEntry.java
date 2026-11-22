package com.swiftdrop.delivery.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.enums.CashEntryType;
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

/**
 * A delivery fee collected or refunded for one parcel. The facts are written once and never change; only the handover and
 * deposit that later carry it are filled in, by bulk updates in the repository.
 */
@Entity
@Table(name = "cash_entries")
public class CashEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 16)
    private CashEntryType entryType;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "tracking_number", nullable = false, updatable = false, length = 32)
    private String trackingNumber;

    /** Who collected it; for a refund, who had collected the fee. */
    @Column(name = "courier_id", updatable = false)
    private UUID courierId;

    @Column(name = "courier_name", updatable = false, length = 128)
    private String courierName;

    /** The pickup district: the fee is its revenue. */
    @Enumerated(EnumType.STRING)
    @Column(name = "district", updatable = false, length = 64)
    private SriLankanDistrict district;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 8)
    private String currency;

    @Column(name = "note", updatable = false, length = 256)
    private String note;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "handover_id")
    private UUID handoverId;

    @Column(name = "deposit_id")
    private UUID depositId;

    protected CashEntry() {
    }

    public CashEntry(CashEntryType entryType, DeliverySession session, UUID courierId, String courierName,
                     SriLankanDistrict district, BigDecimal amount, String currency, String note, UUID recordedBy) {
        this.entryId = UUID.randomUUID();
        this.entryType = entryType;
        this.deliveryId = session.getDeliveryId();
        this.orderId = session.getOrderId();
        this.trackingNumber = session.getTrackingNumber();
        this.courierId = courierId;
        this.courierName = courierName;
        this.district = district;
        this.amount = amount;
        this.currency = currency != null ? currency : "LKR";
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    public UUID getEntryId() { return entryId; }
    public CashEntryType getEntryType() { return entryType; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getOrderId() { return orderId; }
    public String getTrackingNumber() { return trackingNumber; }
    public UUID getCourierId() { return courierId; }
    public String getCourierName() { return courierName; }
    public SriLankanDistrict getDistrict() { return district; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getNote() { return note; }
    public Instant getRecordedAt() { return recordedAt; }
    public UUID getHandoverId() { return handoverId; }
    public UUID getDepositId() { return depositId; }
}
