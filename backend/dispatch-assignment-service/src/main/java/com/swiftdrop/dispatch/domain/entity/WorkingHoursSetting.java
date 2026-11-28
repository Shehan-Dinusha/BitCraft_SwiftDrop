package com.swiftdrop.dispatch.domain.entity;

import com.swiftdrop.dispatch.service.WorkHours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** The one row of courier working hours a SYSTEM_ADMIN sets (id always 1). */
@Entity
@Table(name = "working_hours")
public class WorkingHoursSetting {

    public static final short ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id = ID;

    /** Comma-separated weekdays, e.g. "MONDAY,TUESDAY". */
    @Column(name = "working_days", nullable = false, length = 80)
    private String workingDays;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "shift_from", nullable = false)
    private LocalTime shiftFrom;

    @Column(name = "late_after", nullable = false)
    private LocalTime lateAfter;

    @Column(name = "break_from", nullable = false)
    private LocalTime breakFrom;

    @Column(name = "break_until", nullable = false)
    private LocalTime breakUntil;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "pickup_cutoff", nullable = false)
    private LocalTime pickupCutoff;

    @Column(name = "same_day_cutoff", nullable = false)
    private LocalTime sameDayCutoff;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected WorkingHoursSetting() {
    }

    public static WorkingHoursSetting of(WorkHours hours) {
        WorkingHoursSetting setting = new WorkingHoursSetting();
        setting.apply(hours, null, Instant.now());
        return setting;
    }

    public void apply(WorkHours hours, UUID by, Instant at) {
        workingDays = hours.workingDays().stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
        opensAt = hours.opensAt();
        closesAt = hours.closesAt();
        shiftFrom = hours.shiftFrom();
        lateAfter = hours.lateAfter();
        breakFrom = hours.breakFrom();
        breakUntil = hours.breakUntil();
        breakMinutes = hours.breakMinutes();
        pickupCutoff = hours.pickupCutoff();
        sameDayCutoff = hours.sameDayCutoff();
        updatedBy = by;
        updatedAt = at;
    }

    public WorkHours toHours() {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(workingDays.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf).forEach(days::add);
        return new WorkHours(days, opensAt, closesAt, shiftFrom, lateAfter, breakFrom, breakUntil, breakMinutes,
                pickupCutoff, sameDayCutoff);
    }

    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getUpdatedBy() { return updatedBy; }
}
