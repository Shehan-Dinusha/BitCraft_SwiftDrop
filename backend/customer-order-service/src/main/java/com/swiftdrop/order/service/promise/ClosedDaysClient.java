package com.swiftdrop.order.service.promise;

import java.time.LocalDate;
import java.util.Set;

/** The holidays (not Sundays) on which nobody works, from the dispatch service's calendar. */
public interface ClosedDaysClient {

    /** Listed holidays from {@code from} to {@code to}, both included; empty when the calendar cannot answer. */
    Set<LocalDate> between(LocalDate from, LocalDate to);
}
