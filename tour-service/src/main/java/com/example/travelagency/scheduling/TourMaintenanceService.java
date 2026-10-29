package com.example.travelagency.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class TourMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(TourMaintenanceService.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TourMaintenanceService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public int clearExpiredFeatured(int graceDays) {
        LocalDate cutoff = LocalDate.now(clock).minusDays(graceDays);
        int changed = jdbc.update("UPDATE tours SET featured = FALSE WHERE featured = TRUE AND end_date < ?", cutoff);
        log.info("Removed featured flag from {} expired tours (cutoff {})", changed, cutoff);
        return changed;
    }

    @Transactional
    public int createDailyReport(String country, int horizonDays) {
        LocalDate today = LocalDate.now(clock);
        String selectedCountry = country.trim();
        int inserted = jdbc.update("""
                INSERT INTO daily_tour_reports
                    (report_date, country, horizon_days, tour_count, available_seats)
                SELECT ?, ?, ?, COUNT(*), COALESCE(SUM(available_seats), 0)
                FROM tours
                WHERE start_date >= ? AND start_date <= ? AND (? = 'ALL' OR country = ?)
                HAVING NOT EXISTS (
                    SELECT 1 FROM daily_tour_reports WHERE report_date = ? AND country = ? AND horizon_days = ?
                )
                """, today, selectedCountry, horizonDays, today, today.plusDays(horizonDays),
                selectedCountry, selectedCountry, today, selectedCountry, horizonDays);
        log.info("Created {} daily report snapshots for {} on {}", inserted, selectedCountry, today);
        return inserted;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reports() {
        return jdbc.queryForList("SELECT * FROM daily_tour_reports ORDER BY report_date DESC, country, horizon_days");
    }
}
