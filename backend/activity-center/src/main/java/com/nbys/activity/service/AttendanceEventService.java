package com.nbys.activity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceEventService {
    private final JdbcTemplate jdbc;

    public AttendanceEventService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Integer ensureForActivity(int activityId, Map<String, Object> activity) {
        String organizerIds = text(activity.get("organizer_ids"));
        if (organizerIds.isEmpty() && activity.get("created_by_id") != null) {
            organizerIds = String.valueOf(activity.get("created_by_id"));
        }
        String organizer = organizerNames(organizerIds);

        Map<String, Object> existing = Rows.one(jdbc,
                "select id,organizer,organizer_ids from attendance_events where source_activity_id=? order by id limit 1",
                activityId);
        if (existing != null) {
            if (text(existing.get("organizer_ids")).isEmpty() && !organizerIds.isEmpty()) {
                String displayName = text(existing.get("organizer")).isEmpty() ? organizer : text(existing.get("organizer"));
                jdbc.update("update attendance_events set organizer=?,organizer_ids=? where id=?",
                        displayName, organizerIds, existing.get("id"));
            }
            return ((Number) existing.get("id")).intValue();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String selectedOrganizerIds = organizerIds;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into attendance_events(source_activity_id,name,event_date,location,organizer,organizer_ids,activity_region,is_manual,created_by_id,created_at) " +
                            "values(?,?,date(?),?,?,?,?,0,?,now())",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, activityId);
            statement.setObject(2, activity.get("name"));
            statement.setObject(3, activity.get("start_at"));
            statement.setObject(4, activity.get("location"));
            statement.setObject(5, organizer);
            statement.setObject(6, selectedOrganizerIds);
            statement.setObject(7, activity.get("activity_region"));
            statement.setObject(8, activity.get("created_by_id"));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private String organizerNames(String ids) {
        if (ids.isEmpty()) return "";
        List<Map<String, Object>> users = Rows.list(jdbc,
                "select coalesce(nullif(callsign,''),username) name from users " +
                        "where find_in_set(cast(id as char),?)>0 order by find_in_set(cast(id as char),?)",
                ids, ids);
        StringBuilder names = new StringBuilder();
        for (Map<String, Object> user : users) {
            if (names.length() > 0) names.append('、');
            names.append(user.get("name"));
        }
        return names.toString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
