package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminActivityController {
    private static final String DEFAULT_PLAN_BANNER = "/uploads/activity-plan-default.jpg";

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminActivityController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String recordType,
                                                       @RequestParam(required = false) String startAt,
                                                       @RequestParam(required = false) String endAt) {
        String q = name == null ? "" : name.trim();
        String rt = recordType == null ? "" : recordType.trim();
        List<Map<String, Object>> activities = Rows.list(jdbc,
                "select a.*, " +
                        "(select count(*) from enrollments e where e.activity_id=a.id) enroll_count, " +
                        "(select count(*) from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=a.id and ar.present=1) checkin_count " +
                        "from activities a where a.record_type='activity' " +
                        "and (?='' or a.name like concat('%',?,'%')) and (?='' or a.record_type=?) " +
                        "and (? is null or a.start_at>=?) and (? is null or a.end_at<=?) order by a.id desc",
                q, q, rt, rt, startAt, startAt, endAt, endAt);
        for (Map<String, Object> row : activities) {
            applyDefaultVenueBanner(row);
            row.put("display_status", displayStatus(row));
            row.put("display_record_type", "正式活动");
            row.put("signup_limit", signupLimit(row));
        }
        List<Map<String, Object>> plans = Rows.list(jdbc,
                "select p.*, 'plan' record_type, null start_at, p.vote_deadline end_at, null location, 0 enroll_count, 0 checkin_count " +
                        "from activity_plans p where (?='' or p.name like concat('%',?,'%')) and (?='' or ?='plan') order by p.id desc",
                q, q, rt, rt);
        for (Map<String, Object> row : plans) {
            row.put("display_status", row.get("converted_activity_id") == null ? "投票中" : "已生成活动");
            row.put("display_record_type", "活动策划");
        }
        if ("activity".equals(rt)) return ApiResponse.ok(activities);
        if ("plan".equals(rt)) return ApiResponse.ok(plans);
        activities.addAll(plans);
        return ApiResponse.ok(activities);
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable int id) {
        Map<String, Object> row = Rows.one(jdbc, "select * from activities where id=?", id);
        if (row == null) throw new IllegalArgumentException("活动不存在");
        applyDefaultVenueBanner(row);
        row.put("display_status", displayStatus(row));
        row.put("signup_limit", signupLimit(row));
        row.put("enrollments", Rows.list(jdbc,
                "select e.*, u.username, u.callsign from enrollments e join users u on u.id=e.user_id where e.activity_id=? order by e.id", id));
        row.put("squads", Rows.list(jdbc, "select * from squad_settings where activity_id=? order by camp_no,squad_no", id));
        return ApiResponse.ok(row);
    }

    @PostMapping("/activities")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "activity:create");
        Integer id = insertActivity(body, auth.currentUserId(req));
        createSquads(id, num(body.get("camp_count"), 2), num(body.get("squad_count"), 1));
        return ApiResponse.ok(Collections.<String, Object>singletonMap("id", id));
    }

    @PutMapping("/activities/{id}")
    public ApiResponse<Void> update(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "activity:update");
        Integer venueId = numOrNull(body.get("venue_id"));
        String location = activityLocation(body, venueId);
        String bannerUrl = activityBanner(body, venueId);
        String bannerSource = activityBannerSource(body);
        jdbc.update("update activities set name=?, banner_url=?, banner_source=?, activity_type=?, start_at=?, end_at=?, location=?, venue_id=?, open_min=?, camp_count=?, camp_limit=?, squad_count=?, squad_limit=?, allowed_jobs=?, game_modes=?, activity_region=?, visibility_type=?, invitee_ids=? where id=?",
                body.get("name"), bannerUrl, bannerSource, body.get("activity_type"), body.get("start_at"), body.get("end_at"), location, venueId,
                num(body.get("open_min"), 0), num(body.get("camp_count"), 2), num(body.get("camp_limit"), 0), num(body.get("squad_count"), 1), num(body.get("squad_limit"), 0),
                Rows.joinValue(body.get("allowed_jobs")), Rows.joinValue(body.get("game_modes")), body.get("activity_region"), body.get("visibility_type"), Rows.joinValue(body.get("invitee_ids")), id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/activities/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "activity:cancel");
        jdbc.update("update activities set deleted_at=now(), deleted_by_id=? where id=?", auth.currentUserId(req), id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/activities/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "activity:restore");
        jdbc.update("update activities set deleted_at=null, deleted_by_id=null where id=?", id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/activities/{id}")
    public ApiResponse<Void> delete(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "activity:delete");
        jdbc.update("delete from enrollments where activity_id=?", id);
        jdbc.update("delete from squad_settings where activity_id=?", id);
        jdbc.update("delete from camp_settings where activity_id=?", id);
        jdbc.update("delete from activities where id=?", id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/activity-plans")
    public ApiResponse<Map<String, Object>> createPlan(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "plan:create");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("insert into activity_plans(name,banner_url,vote_deadline,hidden,visibility_type,invitee_ids,created_by_id,created_at) values(?,?,?,0,?,?,?,now())", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, body.get("name"));
            ps.setObject(2, planBanner(body.get("banner_url")));
            ps.setObject(3, body.get("vote_deadline"));
            ps.setObject(4, body.get("visibility_type"));
            ps.setObject(5, Rows.joinValue(body.get("invitee_ids")));
            ps.setObject(6, auth.currentUserId(req));
            return ps;
        }, kh);
        int id = kh.getKey().intValue();
        replacePlanOptions(id, body);
        return ApiResponse.ok(Collections.<String, Object>singletonMap("id", id));
    }

    @GetMapping("/activity-plans/{id}")
    public ApiResponse<Map<String, Object>> planDetail(@PathVariable int id) {
        Map<String, Object> row = Rows.one(jdbc, "select * from activity_plans where id=?", id);
        if (row == null) throw new IllegalArgumentException("策划不存在");
        row.put("dates", Rows.list(jdbc, "select * from plan_date_options where plan_id=? order by date", id));
        row.put("venues", Rows.list(jdbc, "select v.* from plan_venue_options o join venues v on v.id=o.venue_id where o.plan_id=?", id));
        row.put("game_modes", Rows.list(jdbc, "select g.* from plan_game_mode_options o join game_modes g on g.id=o.game_mode_id where o.plan_id=?", id));
        return ApiResponse.ok(row);
    }

    @PutMapping("/activity-plans/{id}")
    public ApiResponse<Void> updatePlan(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "plan:update");
        jdbc.update("update activity_plans set name=?, banner_url=?, vote_deadline=?, visibility_type=?, invitee_ids=? where id=?",
                body.get("name"), planBanner(body.get("banner_url")), body.get("vote_deadline"), body.get("visibility_type"), Rows.joinValue(body.get("invitee_ids")), id);
        replacePlanOptions(id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/activity-plans/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "plan:delete");
        jdbc.update("delete from plan_votes where plan_id=?", id);
        jdbc.update("delete from plan_date_options where plan_id=?", id);
        jdbc.update("delete from plan_venue_options where plan_id=?", id);
        jdbc.update("delete from plan_game_mode_options where plan_id=?", id);
        jdbc.update("delete from activity_plans where id=?", id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/activity-plans/{id}/convert")
    public ApiResponse<Map<String, Object>> convertPlan(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "plan:update");
        auth.require(req, "activity:create");
        Map<String, Object> plan = Rows.one(jdbc, "select * from activity_plans where id=?", id);
        if (plan == null) throw new IllegalArgumentException("策划不存在");
        if (plan.get("converted_activity_id") != null) throw new IllegalArgumentException("该策划已生成活动");
        Map<String, Object> date = topPlanDate(id);
        Map<String, Object> venue = topPlanVenue(id);
        List<Map<String, Object>> modes = topPlanModes(id);
        Map<String, Object> activity = new HashMap<String, Object>();
        activity.put("name", plan.get("name"));
        activity.put("activity_type", "周常");
        activity.put("start_at", date == null ? plan.get("vote_deadline") : text(date.get("date")) + " 12:00:00");
        activity.put("end_at", date == null ? plan.get("vote_deadline") : text(date.get("date")) + " 18:00:00");
        activity.put("venue_id", venue == null ? null : venue.get("id"));
        activity.put("location", venue == null ? "" : venueLocation(venue));
        activity.put("open_min", 0);
        activity.put("camp_count", 2);
        activity.put("camp_limit", 0);
        activity.put("squad_count", 1);
        activity.put("squad_limit", 0);
        activity.put("allowed_jobs", Arrays.asList("突击兵", "支援兵", "医疗兵", "狙击手", "弹药兵", "填线兵"));
        activity.put("game_modes", modeNames(modes));
        activity.put("activity_region", "宁波");
        activity.put("visibility_type", plan.get("visibility_type"));
        activity.put("invitee_ids", plan.get("invitee_ids"));
        String planBanner = text(plan.get("banner_url"));
        if (!planBanner.isEmpty() && !DEFAULT_PLAN_BANNER.equals(planBanner)) {
            activity.put("banner_url", planBanner);
            activity.put("banner_source", "custom");
        } else {
            activity.put("banner_url", "");
            activity.put("banner_source", "venue");
        }
        Integer activityId = insertActivity(activity, auth.currentUserId(req));
        createSquads(activityId, 2, 1);
        jdbc.update("update activity_plans set converted_activity_id=?, hidden=1 where id=?", activityId, id);
        return ApiResponse.ok(Collections.<String, Object>singletonMap("id", activityId));
    }

    private Integer insertActivity(Map<String, Object> body, Integer userId) {
        KeyHolder kh = new GeneratedKeyHolder();
        Integer venueId = numOrNull(body.get("venue_id"));
        String location = activityLocation(body, venueId);
        String bannerUrl = activityBanner(body, venueId);
        String bannerSource = activityBannerSource(body);
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("insert into activities(record_type,name,banner_url,banner_source,activity_type,start_at,end_at,location,venue_id,open_min,camp_count,camp_limit,squad_count,squad_limit,allowed_jobs,game_modes,attendance_enabled,activity_region,visibility_type,invitee_ids,list_locked,created_by_id,created_at) values('activity',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,0,?,now())", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, body.get("name"));
            ps.setObject(2, bannerUrl);
            ps.setObject(3, bannerSource);
            ps.setObject(4, body.get("activity_type"));
            ps.setObject(5, body.get("start_at"));
            ps.setObject(6, body.get("end_at"));
            ps.setObject(7, location);
            ps.setObject(8, venueId);
            ps.setObject(9, num(body.get("open_min"), 0));
            ps.setObject(10, num(body.get("camp_count"), 2));
            ps.setObject(11, num(body.get("camp_limit"), 0));
            ps.setObject(12, num(body.get("squad_count"), 1));
            ps.setObject(13, num(body.get("squad_limit"), 0));
            ps.setObject(14, Rows.joinValue(body.get("allowed_jobs")));
            ps.setObject(15, Rows.joinValue(body.get("game_modes")));
            ps.setObject(16, body.get("activity_region"));
            ps.setObject(17, body.get("visibility_type"));
            ps.setObject(18, Rows.joinValue(body.get("invitee_ids")));
            ps.setObject(19, userId);
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    private String activityLocation(Map<String, Object> body, Integer venueId) {
        String location = text(body.get("location"));
        if (venueId != null) {
            Map<String, Object> venue = Rows.one(jdbc, "select name,address from venues where id=?", venueId);
            if (venue != null) {
                String address = text(venue.get("address"));
                String name = text(venue.get("name"));
                if (!address.isEmpty()) return address;
                if (!name.isEmpty()) return name;
            }
        }
        return location;
    }

    private String activityBanner(Map<String, Object> body, Integer venueId) {
        String banner = text(body.get("banner_url"));
        if ("custom".equals(activityBannerSource(body)) && !banner.isEmpty()) return banner;
        if (venueId != null) {
            Map<String, Object> venue = Rows.one(jdbc, "select image_url from venues where id=?", venueId);
            if (venue != null) {
                String image = text(venue.get("image_url"));
                if (!image.isEmpty()) return image;
            }
        }
        return null;
    }

    private String activityBannerSource(Map<String, Object> body) {
        String banner = text(body.get("banner_url"));
        String source = text(body.get("banner_source"));
        return "custom".equals(source) && !banner.isEmpty() ? "custom" : "venue";
    }

    private Map<String, Object> topPlanDate(int planId) {
        return Rows.one(jdbc,
                "select d.* from plan_date_options d " +
                        "left join (select option_id,count(*) votes from plan_votes where plan_id=? and option_type='date' group by option_id) v on v.option_id=d.id " +
                        "where d.plan_id=? order by coalesce(v.votes,0) desc,d.id limit 1",
                planId, planId);
    }

    private Map<String, Object> topPlanVenue(int planId) {
        return Rows.one(jdbc,
                "select v.* from plan_venue_options o join venues v on v.id=o.venue_id " +
                        "left join (select option_id,count(*) votes from plan_votes where plan_id=? and option_type='venue' group by option_id) pv on pv.option_id=v.id " +
                        "where o.plan_id=? order by coalesce(pv.votes,0) desc,o.id limit 1",
                planId, planId);
    }

    private List<Map<String, Object>> topPlanModes(int planId) {
        return Rows.list(jdbc,
                "select g.* from plan_game_mode_options o join game_modes g on g.id=o.game_mode_id " +
                        "left join (select option_id,count(*) votes from plan_votes where plan_id=? and option_type='game_mode' group by option_id) pv on pv.option_id=g.id " +
                        "where o.plan_id=? order by coalesce(pv.votes,0) desc,o.id",
                planId, planId);
    }

    private String modeNames(List<Map<String, Object>> modes) {
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> mode : modes) {
            String name = text(mode.get("name"));
            if (!name.isEmpty()) names.add(name);
        }
        return String.join(",", names);
    }

    private String venueLocation(Map<String, Object> venue) {
        String address = text(venue.get("address"));
        return address.isEmpty() ? text(venue.get("name")) : address;
    }

    private void replacePlanOptions(int id, Map<String, Object> body) {
        jdbc.update("delete from plan_date_options where plan_id=?", id);
        jdbc.update("delete from plan_venue_options where plan_id=?", id);
        jdbc.update("delete from plan_game_mode_options where plan_id=?", id);
        for (Object date : list(body.get("dates"))) jdbc.update("insert into plan_date_options(plan_id,date) values(?,?)", id, date);
        for (Object venueId : list(body.get("venue_ids"))) jdbc.update("insert into plan_venue_options(plan_id,venue_id) values(?,?)", id, venueId);
        for (Object modeId : list(body.get("game_mode_ids"))) jdbc.update("insert into plan_game_mode_options(plan_id,game_mode_id) values(?,?)", id, modeId);
    }

    private void createSquads(int activityId, int camps, int squads) {
        for (int c = 1; c <= camps; c++) {
            jdbc.update("insert into camp_settings(activity_id,camp_no,updated_at) values(?,?,now())", activityId, c);
            for (int s = 1; s <= squads; s++) {
                jdbc.update("insert into squad_settings(activity_id,camp_no,squad_no,name,radio_channel,locked,updated_at) values(?,?,?,?,?,0,now())",
                        activityId, c, s, (char) ('A' + s - 1) + "队", c == 1 ? "435." + String.format("%03d", (s - 1) * 100) : "436." + String.format("%03d", (s - 1) * 100));
            }
        }
    }

    private String displayStatus(Map<String, Object> row) {
        if (row.get("deleted_at") != null) return "活动取消";
        return String.valueOf(row.get("start_at")).compareTo(new java.sql.Timestamp(System.currentTimeMillis()).toString()) > 0 ? "报名中"
                : String.valueOf(row.get("end_at")).compareTo(new java.sql.Timestamp(System.currentTimeMillis()).toString()) < 0 ? "活动结束" : "活动开始";
    }

    private int signupLimit(Map<String, Object> row) {
        int modeMax = 0;
        for (String mode : Rows.csv(String.valueOf(row.get("game_modes")))) {
            Map<String, Object> m = Rows.one(jdbc, "select suitable_people from game_modes where name=?", mode);
            if (m != null) modeMax = Math.max(modeMax, num(m.get("suitable_people"), 0));
        }
        int campLimit = num(row.get("camp_count"), 0) * num(row.get("camp_limit"), 0);
        int squadLimit = num(row.get("camp_count"), 0) * num(row.get("squad_count"), 0) * num(row.get("squad_limit"), 0);
        int limit = modeMax == 0 ? Integer.MAX_VALUE : modeMax;
        if (campLimit > 0) limit = Math.min(limit, campLimit);
        if (squadLimit > 0) limit = Math.min(limit, squadLimit);
        return limit == Integer.MAX_VALUE ? 0 : limit;
    }

    private void applyDefaultVenueBanner(Map<String, Object> row) {
        if ("custom".equals(text(row.get("banner_source"))) && !text(row.get("banner_url")).isEmpty()) return;
        Map<String, Object> venue = null;
        if (row.get("venue_id") != null) {
            venue = Rows.one(jdbc, "select image_url from venues where id=?", row.get("venue_id"));
        }
        if (venue == null) {
            String location = text(row.get("location"));
            if (!location.isEmpty()) venue = Rows.one(jdbc, "select image_url from venues where name=? or address=? limit 1", location, location);
        }
        if (venue != null && !text(venue.get("image_url")).isEmpty()) row.put("banner_url", venue.get("image_url"));
    }

    private String planBanner(Object banner) {
        String value = text(banner);
        return value.isEmpty() ? DEFAULT_PLAN_BANNER : value;
    }

    private List<?> list(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof List) return (List<?>) value;
        if (value instanceof Collection) return new ArrayList<Object>((Collection<?>) value);
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> values = new ArrayList<Object>();
            for (int i = 0; i < length; i++) values.add(java.lang.reflect.Array.get(value, i));
            return values;
        }
        return Rows.csv(String.valueOf(value));
    }

    private int num(Object v, int fallback) {
        if (v == null) return fallback;
        try {
            String s = String.valueOf(v).replaceAll("[^0-9]", "");
            return s.isEmpty() ? fallback : Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer numOrNull(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) return null;
        try {
            return Integer.parseInt(String.valueOf(v).replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
