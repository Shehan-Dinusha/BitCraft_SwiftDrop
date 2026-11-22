package com.swiftdrop.delivery.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.enums.CashDepositStatus;
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

/** A hub's counted cash, less refunds, banked by its dispatcher. */
@Entity
@Table(name = "cash_deposits")
public class CashDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deposit_id", nullable = false, updatable = false)
    private UUID depositId;

    @Enumerated(EnumType.STRING)
    @Column(name = "district", nullable = false, updatable = false, length = 64)
    private SriLankanDistrict district;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 8)
    private String currency = "LKR";

    /** The bank slip number. */
    @Column(name = "reference", nullable = false, updatable = false, length = 64)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CashDepositStatus status;

    @Column(name = "deposited_by", nullable = false, updatable = false)
    private UUID depositedBy;

    @Column(name = "deposited_at", nullable = false, updatable = false)
    private Instant depositedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 256)
    private String reviewNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CashDeposit() {
    }

    public CashDeposit(SriLankanDistrict district, BigDecimal amount, String reference, UUID depositedBy) {
        this.depositId = UUID.randomUUID();
        this.district = district;
        this.amount = amount;
        this.reference = reference;
        this.status = CashDepositStatus.SUBMITTED;
        this.depositedBy = depositedBy;
        this.depositedAt = Instant.now();
    }

    public void review(CashDepositStatus outcome, String note, UUID reviewedBy) {
        this.status = outcome;
        this.reviewNote = note;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
    }

    public UUID getDepositId() { return depositId; }
    public SriLankanDistrict getDistrict() { return district; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public CashDepositStatus getStatus() { return status; }
    public Instant getDepositedAt() { return depositedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
}
