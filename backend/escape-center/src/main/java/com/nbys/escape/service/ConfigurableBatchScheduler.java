package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class ConfigurableBatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(ConfigurableBatchScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final BatchRunService batches;

    public ConfigurableBatchScheduler(JdbcTemplate jdbc, BatchRunService batches) {
        this.jdbc = jdbc;
        this.batches = batches;
    }

    @Scheduled(fixedDelay = 15000)
    public void poll() {
        LocalDateTime now = LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
        for (Map<String, Object> task : Rows.list(jdbc, "select task_key,cycle,start_at from batch_task_configs where enabled=1")) {
            String taskKey = String.valueOf(task.get("task_key"));
            LocalDateTime base = dateTime(task.get("start_at"));
            LocalDateTime occurrence = occurrence(String.valueOf(task.get("cycle")), base, now);
            if (occurrence == null || occurrence.isBefore(base) || ChronoUnit.SECONDS.between(occurrence, now) < 0 || ChronoUnit.SECONDS.between(occurrence, now) > 20) continue;
            try {
                batches.runScheduled(taskKey, occurrence);
            } catch (RuntimeException e) {
                log.error("跑批任务执行失败 taskKey={} scheduledAt={}", task.get("task_key"), occurrence, e);
            }
        }
    }

    static LocalDateTime occurrence(String cycle, LocalDateTime base, LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        if ("daily".equals(cycle)) return date.isBefore(base.toLocalDate()) ? null : date.atTime(base.toLocalTime());
        if ("weekly".equals(cycle)) {
            int daysBack = (date.getDayOfWeek().getValue() - base.getDayOfWeek().getValue() + 7) % 7;
            LocalDate scheduled = date.minusDays(daysBack);
            return scheduled.isBefore(base.toLocalDate()) ? null : scheduled.atTime(base.toLocalTime());
        }
        if ("monthly".equals(cycle)) {
            if (date.isBefore(base.toLocalDate().withDayOfMonth(1))) return null;
            YearMonth month = YearMonth.from(date);
            return month.atDay(Math.min(base.getDayOfMonth(), month.lengthOfMonth())).atTime(base.toLocalTime());
        }
        if ("yearly".equals(cycle)) {
            if (date.getMonthValue() < base.getMonthValue() || (date.getMonthValue() == base.getMonthValue() && date.getDayOfMonth() < 1)) {
                if (date.getYear() <= base.getYear()) return null;
            }
            LocalDate yearDay = LocalDate.of(date.getYear(), base.getMonthValue(), 1);
            yearDay = yearDay.withDayOfMonth(Math.min(base.getDayOfMonth(), YearMonth.from(yearDay).lengthOfMonth()));
            if (yearDay.isBefore(base.toLocalDate())) return null;
            return yearDay.atTime(base.toLocalTime());
        }
        return null;
    }

    static LocalDateTime dateTime(Object value) {
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
        if (value instanceof java.time.LocalDateTime) return (LocalDateTime) value;
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }
}
