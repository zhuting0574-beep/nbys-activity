package com.nbys.activity.service;

import java.util.Map;

public final class ActivityLimitCalculator {
    private ActivityLimitCalculator() {
    }

    public static int signupLimit(Map<String, Object> row, Map<String, Integer> modeLimits) {
        int modeMax = 0;
        for (String mode : Rows.csv(String.valueOf(row.get("game_modes")))) {
            modeMax = Math.max(modeMax, modeLimits.getOrDefault(mode, 0));
        }
        int campLimit = num(row.get("camp_count"), 0) * num(row.get("camp_limit"), 0);
        int squadLimit = num(row.get("camp_count"), 0) * num(row.get("squad_count"), 0) * num(row.get("squad_limit"), 0);
        int limit = modeMax == 0 ? Integer.MAX_VALUE : modeMax;
        if (campLimit > 0) limit = Math.min(limit, campLimit);
        if (squadLimit > 0) limit = Math.min(limit, squadLimit);
        int openMin = num(row.get("open_min"), 0);
        if (limit == Integer.MAX_VALUE) {
            return openMin > 0 ? openMin : 0;
        }
        // 没有阵营/小队硬上限时，开启人数也代表活动的目标名额下限。
        if (openMin > 0 && campLimit == 0 && squadLimit == 0) {
            limit = Math.max(limit, openMin);
        }
        return limit;
    }

    private static int num(Object v, int fallback) {
        if (v == null) return fallback;
        try {
            String s = String.valueOf(v).replaceAll("[^0-9]", "");
            return s.isEmpty() ? fallback : Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
