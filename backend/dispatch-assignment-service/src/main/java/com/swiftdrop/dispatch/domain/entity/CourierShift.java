package com.swiftdrop.dispatch.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
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

@Entity
@Table(name = "courier_shifts")
public class CourierShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false, unique = true)
    private UUID courierId;

    @Column(name = "courier_name", nullable = false, length = 128)
    private String courierName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 32)
    private VehicleType vehicleType = VehicleType.MOTORCYCLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_district", nullable = false, length = 64)
    private SriLankanDistrict currentDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_status", nullable = false, length = 32)
    private ShiftStatus shiftStatus = ShiftStatus.OFFLINE;

    @Column(name = "active_parcels_count", nullable = false)
    private Integer activeParcelsCount = 0;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity = 5;

    @Column(name = "shift_started_at")
    private Instant shiftStartedAt;

    @Column(name = "shift_ended_at")
    private Instant shiftEndedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    /** The lunch break of the current shift: one per shift. */
    @Column(name = "break_started_at")
    private Instant breakStartedAt;

    @Column(name = "break_ended_at")
    private Instant breakEndedAt;

    /** When the courier left the hub on their run; null while they are at the hub. */
    @Column(name = "run_started_at")
    private Instant runStartedAt;

    /** When the courier last came back to the hub from a run this shift. */
    @Column(name = "last_run_ended_at")
    private Instant lastRunEndedAt;

    /** The courier left the shift open and it was closed at night. */
    @Column(name = "closed_automatically", nullable = false)
    private boolean closedAutomatically;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CourierShift() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (activeParcelsCount == null) {
            activeParcelsCount = 0;
        }
        if (maxCapacity == null) {
            maxCapacity = 5;
        }
        if (shiftStatus == null) {
            shiftStatus = ShiftStatus.OFFLINE;
        }
        if (vehicleType == null) {
            vehicleType = VehicleType.MOTORCYCLE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    /** On shift (a courier on break keeps their parcels and can be given more) with a parcel place free. */
    public boolean canAcceptParcel() {
        return shiftStatus != ShiftStatus.OFFLINE && activeParcelsCount < maxCapacity;
    }

    /** The status a working courier has: BUSY when every parcel place is taken, AVAILABLE otherwise. */
    public ShiftStatus workingStatus() {
        return activeParcelsCount >= maxCapacity ? ShiftStatus.BUSY : ShiftStatus.AVAILABLE;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }

    public SriLankanDistrict getCurrentDistrict() { return currentDistrict; }
    public void setCurrentDistrict(SriLankanDistrict currentDistrict) { this.currentDistrict = currentDistrict; }

    public ShiftStatus getShiftStatus() { return shiftStatus; }
    public void setShiftStatus(ShiftStatus shiftStatus) { this.shiftStatus = shiftStatus; }

    public Integer getActiveParcelsCount() { return activeParcelsCount; }
    public void setActiveParcelsCount(Integer activeParcelsCount) { this.activeParcelsCount = activeParcelsCount; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public Instant getShiftStartedAt() { return shiftStartedAt; }
    public void setShiftStartedAt(Instant shiftStartedAt) { this.shiftStartedAt = shiftStartedAt; }

    public Instant getShiftEndedAt() { return shiftEndedAt; }
    public void setShiftEndedAt(Instant shiftEndedAt) { this.shiftEndedAt = shiftEndedAt; }

    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public Instant getBreakStartedAt() { return breakStartedAt; }
    public void setBreakStartedAt(Instant breakStartedAt) { this.breakStartedAt = breakStartedAt; }

    public Instant getBreakEndedAt() { return breakEndedAt; }
    public void setBreakEndedAt(Instant breakEndedAt) { this.breakEndedAt = breakEndedAt; }

    public Instant getRunStartedAt() { return runStartedAt; }
    public void setRunStartedAt(Instant runStartedAt) { this.runStartedAt = runStartedAt; }

    /** Left the hub: hub parcels can no longer be loaded, only pickups added. */
    public boolean isOnRun() { return runStartedAt != null; }

    public Instant getLastRunEndedAt() { return lastRunEndedAt; }
    public void setLastRunEndedAt(Instant lastRunEndedAt) { this.lastRunEndedAt = lastRunEndedAt; }

    public boolean isClosedAutomatically() { return closedAutomatically; }
    public void setClosedAutomatically(boolean closedAutomatically) { this.closedAutomatically = closedAutomatically; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
