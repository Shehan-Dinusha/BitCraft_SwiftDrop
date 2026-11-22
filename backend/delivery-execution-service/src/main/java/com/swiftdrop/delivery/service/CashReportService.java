package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.delivery.dto.CashReportResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Revenue and cash, read from the ledger itself so a report can never disagree with the cash. Revenue is
 * the delivery fees collected minus refunds, whole, under the pickup district.
 */
@Service
public class CashReportService {

    static final int MAX_DAYS = 90;
    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final String IN_DISTRICT = " AND (:district IS NULL OR district = :district) ";
    private static final String IN_PERIOD = " recorded_at >= :from AND recorded_at < :to ";
    private static final String REVENUE =
            "COALESCE(SUM(CASE entry_type WHEN 'REFUND' THEN -amount ELSE amount END), 0)";
    private static final String PARCELS = "COUNT(*) FILTER (WHERE entry_type = 'COLLECTION')";

    private final NamedParameterJdbcTemplate jdbc;

    public CashReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CashReportResponse report(CallerContext caller, LocalDate from, LocalDate to, SriLankanDistrict requested) {
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        SriLankanDistrict district = caller.role() == UserRole.SYSTEM_ADMIN ? requested : caller.districtId();
        if (district == null && caller.role() == UserRole.DISPATCHER) {
            throw new ForbiddenException("Your account has no district");
        }
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("Provide a date range of 1 to " + MAX_DAYS + " days");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", startOf(from))
                .addValue("to", startOf(to.plusDays(1)))
                .addValue("zone", ZONE.getId())
                .addValue("district", district == null ? null : district.name(), Types.VARCHAR);

        Map<String, Object> period = jdbc.queryForMap("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE entry_type = 'COLLECTION'), 0) AS fees,
                       COALESCE(SUM(amount) FILTER (WHERE entry_type = 'REFUND'), 0) AS refunds,
                       COUNT(*) FILTER (WHERE entry_type = 'COLLECTION') AS parcels
                FROM cash_entries WHERE""" + IN_PERIOD + IN_DISTRICT, params);
        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT COALESCE(SUM(GREATEST(expected_amount - counted_amount, 0)), 0) AS shortage,
                       COALESCE(SUM(GREATEST(counted_amount - expected_amount, 0)), 0) AS excess
                FROM cash_handovers WHERE counted_at >= :from AND counted_at < :to""" + IN_DISTRICT, params);
        BigDecimal refunds = money(period, "refunds");

        return new CashReportResponse(from, to, "LKR",
                money(period, "fees").subtract(refunds), refunds, ((Number) period.get("parcels")).longValue(),
                money(counts, "shortage"), money(counts, "excess"),
                amount("SELECT COALESCE(SUM(amount), 0) FROM cash_entries "
                        + "WHERE entry_type = 'COLLECTION' AND handover_id IS NULL" + IN_DISTRICT, params),
                amount("SELECT COALESCE(SUM(expected_amount), 0) FROM cash_handovers "
                        + "WHERE status = 'SUBMITTED'" + IN_DISTRICT, params),
                amount("SELECT COALESCE(SUM(counted_amount), 0) FROM cash_handovers "
                        + "WHERE status = 'COUNTED' AND deposit_id IS NULL" + IN_DISTRICT, params)
                        .subtract(amount("SELECT COALESCE(SUM(amount), 0) FROM cash_entries "
                                + "WHERE entry_type = 'REFUND' AND deposit_id IS NULL" + IN_DISTRICT, params)),
                amount("SELECT COALESCE(SUM(amount), 0) FROM cash_deposits WHERE status = 'SUBMITTED'" + IN_DISTRICT, params),
                days(from, to, params),
                districts(params));
    }

    private List<CashReportResponse.Day> days(LocalDate from, LocalDate to, MapSqlParameterSource params) {
        Map<LocalDate, CashReportResponse.Day> recorded = jdbc.query(
                "SELECT CAST(recorded_at AT TIME ZONE :zone AS date) AS day, " + REVENUE + " AS revenue, " + PARCELS
                        + " AS parcels FROM cash_entries WHERE" + IN_PERIOD + IN_DISTRICT
                        + "GROUP BY 1", params,
                (rs, row) -> new CashReportResponse.Day(rs.getObject("day", LocalDate.class),
                        rs.getBigDecimal("revenue"), rs.getLong("parcels"))).stream()
                .collect(Collectors.toMap(CashReportResponse.Day::date, Function.identity()));
        return from.datesUntil(to.plusDays(1))
                .map(date -> recorded.getOrDefault(date, new CashReportResponse.Day(date, BigDecimal.ZERO, 0)))
                .toList();
    }

    private List<CashReportResponse.District> districts(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT district, SUM(revenue) AS revenue, SUM(parcels) AS parcels, SUM(last_mile) AS last_mile
                FROM (SELECT district, %s AS revenue, %s AS parcels, 0 AS last_mile
                      FROM cash_entries WHERE %s GROUP BY district
                      UNION ALL
                      SELECT delivery_district, 0, 0, COUNT(*)
                      FROM delivery_sessions
                      WHERE current_state = 'DELIVERED' AND delivered_at >= :from AND delivered_at < :to
                        AND pickup_district <> delivery_district
                      GROUP BY delivery_district) AS work
                WHERE district IS NOT NULL %s
                GROUP BY district ORDER BY 2 DESC, district
                """.formatted(REVENUE, PARCELS, IN_PERIOD, IN_DISTRICT), params,
                (rs, row) -> new CashReportResponse.District(SriLankanDistrict.valueOf(rs.getString("district")),
                        rs.getBigDecimal("revenue"), rs.getLong("parcels"), rs.getLong("last_mile")));
    }

    private BigDecimal amount(String sql, MapSqlParameterSource params) {
        return jdbc.queryForObject(sql, params, BigDecimal.class);
    }

    private static BigDecimal money(Map<String, Object> row, String column) {
        return (BigDecimal) row.get(column);
    }

    private static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant().atOffset(ZoneOffset.UTC);
    }
}
