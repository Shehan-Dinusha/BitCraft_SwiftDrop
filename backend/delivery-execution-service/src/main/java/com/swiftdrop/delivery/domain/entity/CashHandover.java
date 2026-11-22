package com.swiftdrop.delivery.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.enums.CashHandoverStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** All the cash a courier hands to their dispatcher at once. The expected amount never changes. */
@Entity
@Table(name = "cash_handovers")
public class CashHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false, updatable = false)
    private UUID handoverId;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "courier_name", updatable = false, length = 128)
    private String courierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "district", nullable = false, updatable = false, length = 64)
    private SriLankanDistrict district;

    @Column(name = "expected_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "counted_amount", precision = 12, scale = 2)
    private BigDecimal countedAmount;

    @Column(name = "currency", nullable = false, updatable = false, length = 8)
    private String currency = "LKR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CashHandoverStatus status;

    @Column(name = "note", length = 256)
    private String note;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "counted_by")
    private UUID countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(name = "deposit_id")
    private UUID depositId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CashHandover() {
    }

    public CashHandover(UUID courierId, String courierName, SriLankanDistrict district, BigDecimal expectedAmount) {
        this.handoverId = UUID.randomUUID();
        this.courierId = courierId;
        this.courierName = courierName;
        this.district = district;
        this.expectedAmount = expectedAmount;
        this.status = CashHandoverStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void count(BigDecimal counted, String note, UUID countedBy) {
        this.countedAmount = counted;
        this.note = note;
        this.countedBy = countedBy;
        this.countedAt = Instant.now();
        this.status = CashHandoverStatus.COUNTED;
    }

    public UUID getHandoverId() { return handoverId; }
    public UUID getCourierId() { return courierId; }
    public String getCourierName() { return courierName; }
    public SriLankanDistrict getDistrict() { return district; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getCountedAmount() { return countedAmount; }
    public String getCurrency() { return currency; }
    public CashHandoverStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCountedAt() { return countedAt; }
    public UUID getDepositId() { return depositId; }
}
