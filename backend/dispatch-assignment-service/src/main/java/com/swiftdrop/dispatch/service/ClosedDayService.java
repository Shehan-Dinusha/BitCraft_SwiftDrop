package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import com.swiftdrop.dispatch.domain.repository.ClosedDayRepository;
import com.swiftdrop.dispatch.dto.ClosedDayRequest;
import com.swiftdrop.dispatch.dto.WorkdayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** The holidays a SYSTEM_ADMIN closes. Only today and later days can be added or removed. */
@Service
public class ClosedDayService {

    private final ClosedDayRepository closedDays;
    private final WorkingDay workingDay;

    public ClosedDayService(ClosedDayRepository closedDays, WorkingDay workingDay) {
        this.closedDays = closedDays;
        this.workingDay = workingDay;
    }

    @Transactional
    public List<WorkdayResponse.ClosedDayResponse> add(ClosedDayRequest request) {
        LocalDate day = request.day();
        if (day.isBefore(workingDay.today())) {
            throw new IllegalArgumentException("Choose today or a later day.");
        }
        if (!workingDay.hours().isWorkingDay(day.getDayOfWeek())) {
            throw new IllegalArgumentException(WorkingDay.weekdayName(day.getDayOfWeek()) + "s are always closed.");
        }
        if (closedDays.existsById(day)) {
            throw new IllegalArgumentException("That day is already closed.");
        }
        closedDays.save(new ClosedDay(day, request.name().strip()));
        return workingDay.upcomingClosedDays();
    }

    @Transactional
    public List<WorkdayResponse.ClosedDayResponse> remove(LocalDate day) {
        if (day.isBefore(workingDay.today())) {
            throw new IllegalArgumentException("A past day cannot be changed.");
        }
        closedDays.deleteById(day);
        return workingDay.upcomingClosedDays();
    }
}
