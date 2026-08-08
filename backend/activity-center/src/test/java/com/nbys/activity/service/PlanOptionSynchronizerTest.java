package com.nbys.activity.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanOptionSynchronizerTest {
    @Test
    void editingExistingDateKeepsOptionIdAndVotes() {
        JdbcTemplate jdbc = jdbcWithOptions(
                Collections.singletonList(row("id", 31, "date", "2026-07-25", "remark", "")),
                Collections.singletonList(row("id", 8, "option_id", 4)),
                Collections.singletonList(row("id", 9, "option_id", 2)));
        PlanOptionSynchronizer synchronizer = new PlanOptionSynchronizer(jdbc);

        Map<String, Object> changedDate = row("id", 31, "date", "2026-07-26", "remark", "时间调整");
        Map<String, Object> body = row(
                "dates", Collections.singletonList(changedDate),
                "venue_ids", Collections.singletonList(4),
                "game_mode_ids", Collections.singletonList(2));

        synchronizer.sync(11, body);

        verify(jdbc).update(
                "update plan_date_options set date=?,remark=? where id=? and plan_id=?",
                "2026-07-26", "时间调整", 31, 11);
        verify(jdbc, never()).update(
                eq("delete from plan_votes where plan_id=? and option_type='date' and option_id=?"),
                any(), any());
        verify(jdbc, never()).update(
                eq("delete from plan_votes where plan_id=? and option_type=? and option_id=?"),
                any(), any(), any());
    }

    @Test
    void unchangedLegacyDateWithoutIdIsMatchedByValue() {
        JdbcTemplate jdbc = jdbcWithOptions(
                Collections.singletonList(row("id", 31, "date", "2026-07-25", "remark", "")),
                Collections.emptyList(),
                Collections.emptyList());
        PlanOptionSynchronizer synchronizer = new PlanOptionSynchronizer(jdbc);
        Map<String, Object> body = row(
                "dates", Collections.singletonList(row("date", "2026-07-25", "remark", "")),
                "venue_ids", Collections.emptyList(),
                "game_mode_ids", Collections.emptyList());

        synchronizer.sync(11, body);

        verify(jdbc).update(
                "update plan_date_options set date=?,remark=? where id=? and plan_id=?",
                "2026-07-25", "", 31, 11);
        verify(jdbc, never()).update(
                eq("insert into plan_date_options(plan_id,date,remark) values(?,?,?)"),
                any(), any(), any());
        verify(jdbc, never()).update(
                eq("delete from plan_votes where plan_id=? and option_type='date' and option_id=?"),
                any(), any());
    }

    @Test
    void removingAnOptionDeletesOnlyVotesForThatOption() {
        JdbcTemplate jdbc = jdbcWithOptions(
                Arrays.asList(
                        row("id", 31, "date", "2026-07-25", "remark", ""),
                        row("id", 32, "date", "2026-07-26", "remark", "")),
                Collections.emptyList(),
                Collections.emptyList());
        PlanOptionSynchronizer synchronizer = new PlanOptionSynchronizer(jdbc);
        Map<String, Object> body = row(
                "dates", Collections.singletonList(row("id", 31, "date", "2026-07-25", "remark", "")),
                "venue_ids", Collections.emptyList(),
                "game_mode_ids", Collections.emptyList());

        synchronizer.sync(11, body);

        verify(jdbc).update(
                "delete from plan_votes where plan_id=? and option_type='date' and option_id=?",
                11, 32);
        verify(jdbc).update("delete from plan_date_options where plan_id=? and id=?", 11, 32);
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbcWithOptions(List<Map<String, Object>> dates,
                                         List<Map<String, Object>> venues,
                                         List<Map<String, Object>> modes) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(Object[].class), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("plan_date_options")) return dates;
            if (sql.contains("plan_venue_options")) return venues;
            if (sql.contains("plan_game_mode_options")) return modes;
            return Collections.emptyList();
        });
        return jdbc;
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) row.put(String.valueOf(values[i]), values[i + 1]);
        return row;
    }
}
