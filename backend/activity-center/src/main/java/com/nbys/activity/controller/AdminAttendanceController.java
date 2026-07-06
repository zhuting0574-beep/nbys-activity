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
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) Integer year,
                                                    @RequestParam(required = false) String region,
                                                    @RequestParam(required = false, defaultValue = "false") boolean formalOnly) {
        String r = region == null ? "" : region;
        String start = year == null ? null : year + "-01-01";
        String end = year == null ? null : (year + 1) + "-01-01";
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("activity_total", Rows.one(jdbc, "select count(*) total from attendance_events where (? is null or (event_date>=? and event_date<?)) and (?='' or activity_region=?)", year, start, end, r, r).get("total"));
        out.put("top_formal_members", Rows.list(jdbc,
                "select u.id,u.username,u.callsign,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id join users u on u.id=ar.user_id " +
                        "where ar.present=1 and u.is_regular_member=1 and (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?) group by u.id order by count desc limit 3", year, start, end, r, r));
        List<Map<String, Object>> topOrganizers = Rows.list(jdbc,
                "select u.id,coalesce(nullif(u.callsign,''),u.username) organizer,count(*) count " +
                        "from attendance_events ev join users u on find_in_set(cast(u.id as char),coalesce(ev.organizer_ids,''))>0 " +
                        "where (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?) " +
                        "group by u.id,u.username,u.callsign order by count desc,organizer limit 3", year, start, end, r, r);
        List<Map<String, Object>> popularVenues = Rows.list(jdbc,
                "select ev.location,count(ar.id) count from attendance_events ev left join attendance_records ar on ar.event_id=ev.id and ar.present=1 where (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?) and coalesce(ev.location,'')<>'' group by ev.location order by count desc limit 3", year, start, end, r, r);
        out.put("top_organizers", topOrganizers);
        out.put("popular_venues", popularVenues);
        out.put("top_organizer", topOrganizers.isEmpty() ? null : topOrganizers.get(0));
        out.put("popular_venue", popularVenues.isEmpty() ? null : popularVenues.get(0));
        return ApiResponse.ok(out);
    }

    @GetMapping("/matrix")
    public ApiResponse<Map<String, Object>> matrix(@RequestParam(required = false) Integer year,
                                                   @RequestParam(required = false) String region,
                                                   @RequestParam(required = false, defaultValue = "false") boolean formalOnly) {
        String r = region == null ? "" : region;
        String start = year == null ? null : year + "-01-01";
        String end = year == null ? null : (year + 1) + "-01-01";
        List<Map<String, Object>> events = Rows.list(jdbc, "select * from attendance_events where (? is null or (event_date>=? and event_date<?)) and (?='' or activity_region=?) order by event_date", year, start, end, r, r);
        int formal = formalOnly ? 1 : 0;
        List<Map<String, Object>> users = Rows.list(jdbc, "select id,username,callsign,is_regular_member from users where disabled=0 and (?=0 or is_regular_member=1) order by is_regular_member desc,callsign,username", formal);
        List<Map<String, Object>> records = Rows.list(jdbc,
                "select ar.* from attendance_records ar join attendance_events ev on ev.id=ar.event_id where (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?)", year, start, end, r, r);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("events", events);
        out.put("users", users);
        out.put("records", records);
        return ApiResponse.ok(out);
    }

    @PostMapping("/history-activities")
    public ApiResponse<Void> createEvent(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "attendance:create");
        Map<String, String> organizers = organizers(body.get("organizer_ids"));
        jdbc.update("insert into attendance_events(name,event_date,location,organizer,organizer_ids,activity_region,is_manual,created_by_id,created_at) values(?,?,?,?,?,?,1,?,now())",
                body.get("name"), body.get("event_date"), body.get("location"), organizers.get("names"), organizers.get("ids"), body.get("activity_region"), auth.currentUserId(req));
        return ApiResponse.ok(null);
    }

    @PutMapping("/history-activities/{id}")
    public ApiResponse<Void> updateEvent(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "attendance:update");
        Map<String, String> organizers = organizers(body.get("organizer_ids"));
        jdbc.update("update attendance_events set name=?, event_date=?, location=?, organizer=?, organizer_ids=?, activity_region=? where id=?",
                body.get("name"), body.get("event_date"), body.get("location"), organizers.get("names"), organizers.get("ids"), body.get("activity_region"), id);
        return ApiResponse.ok(null);
    }

    private Map<String, String> organizers(Object value) {
        LinkedHashSet<String> requested = new LinkedHashSet<String>(Rows.csv(Rows.joinValue(value)));
        if (requested.isEmpty()) {
            Map<String, String> empty = new HashMap<String, String>();
            empty.put("ids", "");
            empty.put("names", "");
            return empty;
        }
        String ids = Rows.join(requested);
        List<Map<String, Object>> users = Rows.list(jdbc,
                "select id,coalesce(nullif(callsign,''),username) name from users " +
                        "where disabled=0 and is_regular_member=1 and find_in_set(cast(id as char),?)>0 order by callsign,id",
                ids);
        if (users.size() != requested.size()) throw new IllegalArgumentException("组织人只能选择启用的正式队员");
        List<Object> validIds = new ArrayList<Object>();
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> user : users) {
            validIds.add(user.get("id"));
            names.add(String.valueOf(user.get("name")));
        }
        Map<String, String> result = new HashMap<String, String>();
        result.put("ids", Rows.join(validIds));
        result.put("names", String.join("、", names));
        return result;
    }

    @DeleteMapping("/history-activities/{id}")
    public ApiResponse<Void> deleteEvent(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "attendance:delete");
        Map<String, Object> event = Rows.one(jdbc, "select id from attendance_events where id=? and is_manual=1", id);
        if (event == null) throw new IllegalArgumentException("只能删除手动增加的历史活动");
        jdbc.update("delete from attendance_records where event_id=?", id);
        jdbc.update("delete from attendance_events where id=?", id);
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
