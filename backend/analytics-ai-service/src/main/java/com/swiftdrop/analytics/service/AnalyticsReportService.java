package com.swiftdrop.analytics.service;

import com.swiftdrop.analytics.dto.CourierPerformanceResponse;
import com.swiftdrop.analytics.dto.DistrictKpiResponse;
import com.swiftdrop.analytics.domain.projection.OrderProjection;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnalyticsReportService {
    private final JdbcTemplate jdbc;
    public AnalyticsReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Internal database read models mapped to public record DTOs by the report controller.
    public record DailyStats(LocalDate date, long created, long delivered, long failed) {}
    public record OutcomeTotals(long delivered, long failed) {
        public long completed() { return delivered + failed; }
        public BigDecimal successRatePercent() { return rate(delivered); }
        public BigDecimal failureRatePercent() { return rate(failed); }
        private BigDecimal rate(long count) {
            return completed() == 0 ? null : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(completed()), 2, RoundingMode.HALF_UP);
        }
    }
    public record CourierWorkload(UUID courierId, long activeOrders) {}

    public List<DailyStats> daily(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 90) {
            throw new IllegalArgumentException("Provide an inclusive date range of 1 to 90 days");
        }
        var found = new HashMap<LocalDate, DailyStats>();
        jdbc.query("SELECT * FROM daily_delivery_stats WHERE report_date BETWEEN ? AND ?", rs -> {
            var date = rs.getObject("report_date", LocalDate.class);
            found.put(date, new DailyStats(date, rs.getLong("created"), rs.getLong("delivered"), rs.getLong("failed")));
        }, from, to);
        return from.datesUntil(to.plusDays(1)).map(date -> found.getOrDefault(date, new DailyStats(date, 0, 0, 0))).toList();
    }

    public OutcomeTotals outcomes(LocalDate from, LocalDate to) {
        var days = daily(from, to);
        return new OutcomeTotals(days.stream().mapToLong(DailyStats::delivered).sum(), days.stream().mapToLong(DailyStats::failed).sum());
    }

    public List<CourierWorkload> workload() {
        return jdbc.query("""
                SELECT c.courier_id, coalesce(sum(d.active),0) active
                FROM analytics_couriers c LEFT JOIN courier_daily_stats d USING(courier_id)
                GROUP BY c.courier_id ORDER BY c.courier_id
                """, (rs, row) -> new CourierWorkload(rs.getObject(1, UUID.class), rs.getLong(2)));
    }

    public List<DistrictKpiResponse> districts(LocalDate date) {
        Timestamp start = Timestamp.from(date.atStartOfDay(OrderProjection.REPORTING_ZONE).toInstant());
        Timestamp end = Timestamp.from(date.plusDays(1).atStartOfDay(OrderProjection.REPORTING_ZONE).toInstant());
        return jdbc.query("""
                SELECT district, sum(created) created, sum(failed) failed FROM (
                    SELECT pickup_district district, count(*) created, 0 failed FROM analytics_order_projection
                    WHERE created_at >= ? AND created_at < ? AND pickup_district IS NOT NULL GROUP BY pickup_district
                    UNION ALL
                    SELECT outcome_district district, 0 created, count(*) FILTER(WHERE outcome='FAILED') failed
                    FROM analytics_order_projection WHERE outcome_at >= ? AND outcome_at < ?
                        AND outcome_district IS NOT NULL AND conflict_reason IS NULL GROUP BY outcome_district
                ) facts GROUP BY district ORDER BY district
                """, (rs, row) -> new DistrictKpiResponse(SriLankanDistrict.valueOf(rs.getString(1)),
                Math.toIntExact(rs.getLong(2)), null, null, Math.toIntExact(rs.getLong(3)), null, null), start, end, start, end);
    }

    public List<CourierPerformanceResponse> couriers(SriLankanDistrict district, UUID courierId) {
        String sql = """
                SELECT * FROM (
                    SELECT c.courier_id, c.courier_name, c.last_assignment_at,
                        (SELECT count(*) FROM analytics_order_couriers a WHERE a.courier_id=c.courier_id) assigned,
                        coalesce(d.delivered,0) delivered, coalesce(d.failed,0) failed, coalesce(d.active,0) active,
                        (SELECT p.district FROM analytics_order_projection p WHERE p.courier_id=c.courier_id
                         ORDER BY p.status_at DESC NULLS LAST, p.order_id LIMIT 1) district
                    FROM analytics_couriers c LEFT JOIN (
                        SELECT courier_id, sum(delivered) delivered, sum(failed) failed, sum(active) active
                        FROM courier_daily_stats GROUP BY courier_id
                    ) d USING(courier_id)
                ) report WHERE 1=1
                """;
        var parameters = new ArrayList<Object>();
        if (district != null) { sql += " AND district=?"; parameters.add(district.name()); }
        if (courierId != null) { sql += " AND courier_id=?"; parameters.add(courierId); }
        return jdbc.query(sql + " ORDER BY courier_id", (rs, row) -> new CourierPerformanceResponse(
                rs.getObject("courier_id", UUID.class), rs.getString("courier_name"),
                rs.getString("district") == null ? null : SriLankanDistrict.valueOf(rs.getString("district")),
                Math.toIntExact(rs.getLong("assigned")), Math.toIntExact(rs.getLong("delivered")), Math.toIntExact(rs.getLong("failed")),
                null, null, null, rs.getLong("active") > 0 ? "BUSY" : "NO_ACTIVE_ORDERS",
                rs.getTimestamp("last_assignment_at").toInstant()), parameters.toArray());
    }
}
