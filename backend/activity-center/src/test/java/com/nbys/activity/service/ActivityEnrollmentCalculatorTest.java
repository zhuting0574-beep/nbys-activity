package com.nbys.activity.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActivityEnrollmentCalculatorTest {
    @Test
    void extraCountIsNeverNegative() {
        assertEquals(0, ActivityEnrollmentCalculator.extraCount(null));
        assertEquals(0, ActivityEnrollmentCalculator.extraCount(-2));
        assertEquals(0, ActivityEnrollmentCalculator.extraCount("abc"));
        assertEquals(3, ActivityEnrollmentCalculator.extraCount("3"));
    }

    @Test
    void participantCountIncludesSelfAndExtraPeople() {
        Map<String, Object> enrollment = new HashMap<String, Object>();
        enrollment.put("extra_count", 2);

        assertEquals(3, ActivityEnrollmentCalculator.participantCount(enrollment));
    }

    @Test
    void updateChecksLimitAfterRemovingOldEnrollmentCount() {
        assertFalse(ActivityEnrollmentCalculator.exceedsLimit(8, 3, 5, 10));
        assertTrue(ActivityEnrollmentCalculator.exceedsLimit(8, 3, 6, 10));
    }

    @Test
    void zeroLimitMeansUnlimited() {
        assertFalse(ActivityEnrollmentCalculator.exceedsLimit(100, 0, 50, 0));
    }
}
