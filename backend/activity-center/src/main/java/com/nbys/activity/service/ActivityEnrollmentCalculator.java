package com.nbys.activity.service;

import java.util.Map;

public final class ActivityEnrollmentCalculator {
    private ActivityEnrollmentCalculator() {
    }

    public static int extraCount(Object value) {
        int count = num(value, 0);
        return Math.max(0, count);
    }

    public static int participantCount(Map<String, Object> enrollment) {
        return enrollment == null ? 0 : 1 + extraCount(enrollment.get("extra_count"));
    }

    public static boolean exceedsLimit(int currentTotal, int oldParticipants, int newParticipants, int signupLimit) {
        return signupLimit > 0 && currentTotal - oldParticipants + newParticipants > signupLimit;
    }

    private static int num(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) return fallback;
            return Integer.parseInt(text);
        } catch (Exception e) {
            return fallback;
        }
    }
}
