package com.swiftdrop.order.service.promise;

import java.time.LocalDate;
import java.util.Set;

/** The courier calendar from the dispatch service: the listed holidays and the working-day rules. */
public interface ClosedDaysClient {

    /** Listed holidays from {@code from} to {@code to}, both included; empty when the calendar cannot answer. */
    Set<LocalDate> between(LocalDate from, LocalDate to);

    /** The working weekdays and booking cut-offs; the standard plan when the calendar cannot answer. */
    default WorkingRules rules() {
        return WorkingRules.STANDARD;
    }
}
