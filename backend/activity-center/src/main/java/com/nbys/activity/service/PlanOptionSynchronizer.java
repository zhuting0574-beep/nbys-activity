package com.nbys.activity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlanOptionSynchronizer {
    private final JdbcTemplate jdbc;

    public PlanOptionSynchronizer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void sync(int planId, Map<String, Object> body) {
        syncDates(planId, values(body.get("dates")));
        syncReferenceOptions(planId, "venue", "plan_venue_options", "venue_id", values(body.get("venue_ids")));
        syncReferenceOptions(planId, "game_mode", "plan_game_mode_options", "game_mode_id", values(body.get("game_mode_ids")));
    }

    private void syncDates(int planId, List<Object> incoming) {
        List<Map<String, Object>> existing = Rows.list(jdbc,
                "select id,date,remark from plan_date_options where plan_id=? order by id", planId);
        Map<Integer, Map<String, Object>> byId = new HashMap<Integer, Map<String, Object>>();
        Map<String, List<Map<String, Object>>> byValue = new HashMap<String, List<Map<String, Object>>>();
        for (Map<String, Object> row : existing) {
            int id = number(row.get("id"), 0);
            byId.put(id, row);
            byValue.computeIfAbsent(dateKey(row.get("date"), row.get("remark")),
                    key -> new ArrayList<Map<String, Object>>()).add(row);
        }

        Set<Integer> retained = new HashSet<Integer>();
        for (Object item : incoming) {
            String date = dateValue(item);
            if (date.isEmpty()) continue;
            String remark = dateRemark(item);
            Integer requestedId = dateId(item);
            Map<String, Object> matched = requestedId == null ? null : byId.get(requestedId);
            if (matched == null) {
                List<Map<String, Object>> candidates = byValue.getOrDefault(dateKey(date, remark), Collections.emptyList());
                for (Map<String, Object> candidate : candidates) {
                    int candidateId = number(candidate.get("id"), 0);
                    if (!retained.contains(candidateId)) {
                        matched = candidate;
                        break;
                    }
                }
            }

            if (matched == null) {
                jdbc.update("insert into plan_date_options(plan_id,date,remark) values(?,?,?)", planId, date, remark);
                continue;
            }

            int optionId = number(matched.get("id"), 0);
            retained.add(optionId);
            jdbc.update("update plan_date_options set date=?,remark=? where id=? and plan_id=?",
                    date, remark, optionId, planId);
        }

        for (Map<String, Object> row : existing) {
            int optionId = number(row.get("id"), 0);
            if (retained.contains(optionId)) continue;
            jdbc.update("delete from plan_votes where plan_id=? and option_type='date' and option_id=?",
                    planId, optionId);
            jdbc.update("delete from plan_date_options where plan_id=? and id=?", planId, optionId);
        }
    }

    private void syncReferenceOptions(int planId, String optionType, String table, String column,
                                      List<Object> incoming) {
        Set<Integer> desired = new LinkedHashSet<Integer>();
        for (Object value : incoming) {
            int id = number(value, 0);
            if (id > 0) desired.add(id);
        }

        List<Map<String, Object>> existing = Rows.list(jdbc,
                "select id," + column + " option_id from " + table + " where plan_id=? order by id", planId);
        Set<Integer> retained = new HashSet<Integer>();
        for (Map<String, Object> row : existing) {
            int optionId = number(row.get("option_id"), 0);
            if (desired.contains(optionId) && retained.add(optionId)) continue;
            jdbc.update("delete from plan_votes where plan_id=? and option_type=? and option_id=?",
                    planId, optionType, optionId);
            jdbc.update("delete from " + table + " where plan_id=? and id=?",
                    planId, number(row.get("id"), 0));
        }

        for (Integer optionId : desired) {
            if (!retained.contains(optionId)) {
                jdbc.update("insert into " + table + "(plan_id," + column + ") values(?,?)", planId, optionId);
            }
        }
    }

    private List<Object> values(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof Collection) return new ArrayList<Object>((Collection<?>) value);
        return Collections.singletonList(value);
    }

    private Integer dateId(Object value) {
        if (!(value instanceof Map)) return null;
        int id = number(((Map<?, ?>) value).get("id"), 0);
        return id > 0 ? id : null;
    }

    private String dateValue(Object value) {
        if (value instanceof Map) return text(((Map<?, ?>) value).get("date"));
        return text(value);
    }

    private String dateRemark(Object value) {
        if (!(value instanceof Map)) return "";
        return text(((Map<?, ?>) value).get("remark"));
    }

    private String dateKey(Object date, Object remark) {
        return text(date) + "\u0000" + text(remark);
    }

    private int number(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
