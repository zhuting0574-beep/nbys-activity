package com.nbys.activity.controller;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class H5ControllerTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 7, 6, 12, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 7, 6, 18, 0);

    @Test
    void checkinWindowIncludesBothBoundaries() {
        assertFalse(H5Controller.isWithinCheckinWindow(start.minusHours(3).minusNanos(1), start, end, 3, "hour"));
        assertTrue(H5Controller.isWithinCheckinWindow(start.minusHours(3), start, end, 3, "hour"));
        assertTrue(H5Controller.isWithinCheckinWindow(start, start, end, 3, "hour"));
        assertTrue(H5Controller.isWithinCheckinWindow(end, start, end, 3, "hour"));
        assertFalse(H5Controller.isWithinCheckinWindow(end.plusNanos(1), start, end, 3, "hour"));
    }

    @Test
    void checkinWindowSupportsDayAndZeroConfig() {
        assertTrue(H5Controller.isWithinCheckinWindow(start.minusDays(1), start, end, 1, "day"));
        assertFalse(H5Controller.isWithinCheckinWindow(start.minusDays(1).minusNanos(1), start, end, 1, "day"));
        assertFalse(H5Controller.isWithinCheckinWindow(start.minusNanos(1), start, end, 0, "hour"));
        assertTrue(H5Controller.isWithinCheckinWindow(start, start, end, 0, "hour"));
    }

    @Test
    void distanceUsesOneKilometerBoundaryPredictably() {
        assertEquals(0d, H5Controller.distanceMeters(29.87386, 121.55027, 29.87386, 121.55027), 0.01d);
        double near = H5Controller.distanceMeters(29.87386, 121.55027, 29.88284, 121.55027);
        double far = H5Controller.distanceMeters(29.87386, 121.55027, 29.88290, 121.55027);
        assertTrue(near < 1000d, "测试点应在1公里内");
        assertTrue(far > 1000d, "测试点应在1公里外");
    }
}
