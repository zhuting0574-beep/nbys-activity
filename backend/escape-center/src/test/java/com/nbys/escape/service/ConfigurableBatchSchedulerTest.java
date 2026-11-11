package com.nbys.escape.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurableBatchSchedulerTest {
    @Test
    void weeklyUsesStartDateWeekdayAndSkipsBeforeStart() {
        LocalDateTime base = LocalDateTime.parse("2026-11-09T05:00:00");
        assertNull(ConfigurableBatchScheduler.occurrence("weekly", base, LocalDateTime.parse("2026-11-08T05:00:00")));
        assertEquals(LocalDateTime.parse("2026-11-16T05:00:00"),
                ConfigurableBatchScheduler.occurrence("weekly", base, LocalDateTime.parse("2026-11-16T05:00:00")));
    }

    @Test
    void monthlyAndYearlyClampShortMonthsAndLeapDays() {
        LocalDateTime monthly = LocalDateTime.parse("2026-01-31T07:30:00");
        assertEquals(LocalDateTime.parse("2026-02-28T07:30:00"),
                ConfigurableBatchScheduler.occurrence("monthly", monthly, LocalDateTime.parse("2026-02-28T07:30:00")));
        LocalDateTime yearly = LocalDateTime.parse("2024-02-29T08:00:00");
        assertEquals(LocalDateTime.parse("2025-02-28T08:00:00"),
                ConfigurableBatchScheduler.occurrence("yearly", yearly, LocalDateTime.parse("2025-02-28T08:00:00")));
    }

    @Test
    void dailyUsesConfiguredLocalTimeAndRejectsInvalidCycle() {
        LocalDateTime base = LocalDateTime.parse("2026-11-09T05:00:15");
        assertEquals(base, ConfigurableBatchScheduler.occurrence("daily", base, base));
        assertNull(ConfigurableBatchScheduler.occurrence("unknown", base, base));
    }

}
