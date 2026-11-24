package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClosedDayRepository extends JpaRepository<ClosedDay, LocalDate> {

    List<ClosedDay> findByDayGreaterThanEqualOrderByDay(LocalDate from);

    List<ClosedDay> findByDayBetweenOrderByDay(LocalDate from, LocalDate to);
}
