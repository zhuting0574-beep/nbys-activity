package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminAttendanceController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam int year,
                                                    @RequestParam(required = false) String region,
                                                    @RequestParam(required = false, defaultValue = "false") boolean formalOnly) {
        String r = region == null ? "" : region;
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("activity_total", Rows.one(jdbc, "select count(*) total from attendance_events where year(event_date)=? and (?='' or activity_region=?)", year, r, r).get("total"));
        out.put("top_formal_members", Rows.list(jdbc,
                "select u.id,u.username,u.callsign,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id join users u on u.id=ar.user_id " +
                        "where ar.present=1 and u.is_regular_member=1 and year(ev.event_date)=? and (?='' or ev.activity_region=?) group by u.id order by count desc limit 3", year, r, r));
        out.put("top_organizer", Rows.one(jdbc,
                "select organizer,count(*) count from attendance_events where year(event_date)=? and (?='' or activity_region=?) group by organizer order by count desc limit 1", year, r, r));
        out.put("popular_venue", Rows.one(jdbc,
                "select ev.location,count(ar.id) count from attendance_events ev left join attendance_records ar on ar.event_id=ev.id and ar.present=1 where year(ev.event_date)=? and (?='' or ev.activity_region=?) group by ev.location order by count desc limit 1", year, r, r));
        return ApiResponse.ok(out);
    }

    @GetMapping("/matrix")
    public ApiResponse<Map<String, Object>> matrix(@RequestParam int year,
                                                   @RequestParam(required = false) String region,
                                                   @RequestParam(required = false, defaultValue = "false") boolean formalOnly) {
        String r = region == null ? "" : region;
        List<Map<String, Object>> events = Rows.list(jdbc, "select * from attendance_events where year(event_date)=? and (?='' or activity_region=?) order by event_date", year, r, r);
        int formal = formalOnly ? 1 : 0;
        List<Map<String, Object>> users = Rows.list(jdbc, "select id,username,callsign,is_regular_member from users where disabled=0 and (?=0 or is_regular_member=1) order by callsign", formal);
        List<Map<String, Object>> records = Rows.list(jdbc,
                "select ar.* from attendance_records ar join attendance_events ev on ev.id=ar.event_id where year(ev.event_date)=? and (?='' or ev.activity_region=?)", year, r, r);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("events", events);
        out.put("users", users);
        out.put("records", records);
        return ApiResponse.ok(out);
    }

    @PostMapping("/history-activities")
    public ApiResponse<Void> createEvent(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "attendance:create");
        jdbc.update("insert into attendance_events(name,event_date,location,organizer,activity_region,is_manual,created_by_id,created_at) values(?,?,?,?,?,1,?,now())",
                body.get("name"), body.get("event_date"), body.get("location"), body.get("organizer"), body.get("activity_region"), auth.currentUserId(req));
        return ApiResponse.ok(null);
    }

    @PutMapping("/history-activities/{id}")
    public ApiResponse<Void> updateEvent(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "attendance:update");
        jdbc.update("update attendance_events set name=?, event_date=?, location=?, organizer=?, activity_region=? where id=?",
                body.get("name"), body.get("event_date"), body.get("location"), body.get("organizer"), body.get("activity_region"), id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/history-activities/{id}")
    public ApiResponse<Void> deleteEvent(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "attendance:delete");
        jdbc.update("delete from attendance_records where event_id=?", id);
        jdbc.update("delete from attendance_events where id=? and is_manual=1", id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/records")
    public ApiResponse<Void> updateRecord(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "attendance:update");
        int eventId = ((Number) body.get("event_id")).intValue();
        int userId = ((Number) body.get("user_id")).intValue();
        int present = Boolean.TRUE.equals(body.get("present")) || "1".equals(String.valueOf(body.get("present"))) ? 1 : 0;
        if (Rows.one(jdbc, "select id from attendance_records where event_id=? and user_id=?", eventId, userId) == null) {
            jdbc.update("insert into attendance_records(event_id,user_id,present,updated_by_id,updated_at) values(?,?,?,?,now())", eventId, userId, present, auth.currentUserId(req));
        } else {
            jdbc.update("update attendance_records set present=?, updated_by_id=?, updated_at=now() where event_id=? and user_id=?", present, auth.currentUserId(req), eventId, userId);
        }
        return ApiResponse.ok(null);
    }
}
