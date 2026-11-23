package com.swiftdrop.dispatch.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A holiday on which nobody works. Sundays are closed without being listed. */
@Entity
@Table(name = "closed_days")
public class ClosedDay {

    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClosedDay() {
    }

    public ClosedDay(LocalDate day, String name) {
        this.day = day;
        this.name = name;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public LocalDate getDay() { return day; }

    public String getName() { return name; }
}
