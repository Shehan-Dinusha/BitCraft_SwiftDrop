package com.swiftdrop.order.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** One entry in a customer's address book (see V7). Usable for the pickup and the drop-off step. */
@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address_id", nullable = false, unique = true, updatable = false)
    private UUID addressId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    /** The customer's own name for it, such as "Home" or "Amma's place". */
    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    /** +94 followed by 9 digits. */
    @Column(name = "contact_phone", nullable = false, length = 16)
    private String contactPhone;

    @Column(name = "alternate_phone", length = 16)
    private String alternatePhone;

    @Column(name = "house", nullable = false, length = 40)
    private String house;

    @Column(name = "street", nullable = false, length = 150)
    private String street;

    @Column(name = "area", length = 98)
    private String area;

    @Column(name = "landmark", length = 100)
    private String landmark;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "district", nullable = false, length = 32)
    private SriLankanDistrict district;

    @Column(name = "postal_code", nullable = false, length = 5)
    private String postalCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (addressId == null) addressId = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
