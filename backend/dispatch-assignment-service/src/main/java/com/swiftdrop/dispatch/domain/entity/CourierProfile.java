package com.swiftdrop.dispatch.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A courier's own vehicle, registered once after they accept their invite. Shifts copy the vehicle type
 * and capacity from here, so a courier cannot switch vehicles from day to day; their dispatcher can
 * correct the profile.
 */
@Entity
@Table(name = "courier_profiles")
public class CourierProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The courier's IAM user id (the JWT subject). */
    @Column(name = "courier_id", nullable = false, unique = true, updatable = false)
    private UUID courierId;

    /** The courier's home district; decides which dispatcher manages this profile. */
    @Enumerated(EnumType.STRING)
    @Column(name = "district", nullable = false, length = 64)
    private SriLankanDistrict district;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 32)
    private VehicleType vehicleType;

    @Column(name = "plate_number", nullable = false, length = 16)
    private String plateNumber;

    @Column(name = "vehicle_model", nullable = false, length = 64)
    private String vehicleModel;

    @Column(name = "vehicle_colour", nullable = false, length = 32)
    private String vehicleColour;

    @Column(name = "licence_number", nullable = false, length = 16)
    private String licenceNumber;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }

    public SriLankanDistrict getDistrict() { return district; }
    public void setDistrict(SriLankanDistrict district) { this.district = district; }

    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }

    public String getVehicleColour() { return vehicleColour; }
    public void setVehicleColour(String vehicleColour) { this.vehicleColour = vehicleColour; }

    public String getLicenceNumber() { return licenceNumber; }
    public void setLicenceNumber(String licenceNumber) { this.licenceNumber = licenceNumber; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Long getVersion() { return version; }
}
