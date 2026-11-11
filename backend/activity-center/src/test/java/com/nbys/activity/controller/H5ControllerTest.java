package com.nbys.activity.controller;

import com.nbys.activity.service.AttendanceEventService;
import com.nbys.activity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @Test
    void leaderChangingSquadsTransfersLeadershipWithoutAutoAssigningTargetLeader() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(auth.current(request)).thenReturn(row("id", 10));
        when(jdbc.query(anyString(), any(Object[].class), any(ColumnMapRowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("select * from activities where id=? for update")) {
                return rows(row("id", 7, "activity_type", "本地活动", "squad_limit", 10, "allowed_jobs", "步枪手"));
            }
            if (sql.contains("select * from enrollments where activity_id=? and user_id=? for update")) {
                return rows(row("id", 100, "user_id", 10, "camp_no", 1, "squad_no", 1));
            }
            if (sql.contains("select squad_limit,allowed_jobs from activities")) {
                return rows(row("squad_limit", 10, "allowed_jobs", "步枪手"));
            }
            if (sql.contains("select * from squad_settings") && sql.contains("camp_no=?") && sql.contains("squad_no=?")) {
                Object[] args = invocation.getArgument(1);
                int squadNo = ((Number) args[2]).intValue();
                return squadNo == 1
                        ? rows(row("id", 21, "camp_no", 1, "squad_no", 1, "leader_user_id", 10, "locked", 0))
                        : rows(row("id", 22, "camp_no", 1, "squad_no", 2, "leader_user_id", null, "locked", 0));
            }
            if (sql.contains("select user_id from enrollments") && sql.contains("order by id asc")) {
                return rows(row("user_id", 11));
            }
            return Collections.emptyList();
        });

        H5Controller controller = new H5Controller(jdbc, auth, mock(AttendanceEventService.class), "test-secret");
        controller.joinSquad(7, row("camp_no", 1, "squad_no", 2, "job", "步枪手"), request);

        verify(jdbc).update("update squad_settings set leader_user_id=?,updated_at=now() where id=?", 11, 21);
        verify(jdbc).update("update enrollments set camp_no=?, squad_no=?, job=?, updated_at=now() where activity_id=? and user_id=?",
                1, 2, "步枪手", 7, 10);
        verify(jdbc, never()).update(eq("update squad_settings set leader_user_id=? where id=?"), any(), any());
    }

    private static List<Map<String, Object>> rows(Map<String, Object> row) {
        return Collections.singletonList(row);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) row.put(String.valueOf(values[i]), values[i + 1]);
        return row;
    }
}
