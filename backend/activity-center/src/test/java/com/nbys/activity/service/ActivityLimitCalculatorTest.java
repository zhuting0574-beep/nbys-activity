package com.nbys.activity.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityLimitCalculatorTest {
    @Test
    void usesOpenMinWhenThereIsNoHardCampOrSquadLimit() {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("open_min", 30);
        row.put("camp_count", 1);
        row.put("camp_limit", 0);
        row.put("squad_count", 1);
        row.put("squad_limit", 0);
        row.put("game_modes", "日常训练");

        Map<String, Integer> modeLimits = new HashMap<String, Integer>();
        modeLimits.put("日常训练", 10);

        assertEquals(30, ActivityLimitCalculator.signupLimit(row, modeLimits));
    }

    @Test
    void keepsExplicitHardLimitWhenCampOrSquadLimitExists() {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("open_min", 30);
        row.put("camp_count", 2);
        row.put("camp_limit", 12);
        row.put("squad_count", 1);
        row.put("squad_limit", 0);
        row.put("game_modes", "日常训练");

        Map<String, Integer> modeLimits = new HashMap<String, Integer>();
        modeLimits.put("日常训练", 10);

        assertEquals(10, ActivityLimitCalculator.signupLimit(row, modeLimits));
    }
}
