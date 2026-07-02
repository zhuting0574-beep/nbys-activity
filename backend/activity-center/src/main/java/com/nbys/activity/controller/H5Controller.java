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
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/h5")
public class H5Controller {
    private static final String DEFAULT_PLAN_BANNER = "/uploads/activity-plan-default.jpg";

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public H5Controller(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> activities(HttpServletRequest req) {
        Map<String, Object> me = auth.current(req);
        return ApiResponse.ok(activityRows(me));
    }

    private List<Map<String, Object>> activityRows(Map<String, Object> me) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select a.*, coalesce(nullif(u.callsign,''),u.username) creator_name, " +
                        "(select count(distinct e.user_id) from enrollments e where e.activity_id=a.id) enroll_count " +
                        "from activities a left join users u on u.id=a.created_by_id " +
                        "where a.record_type='activity' and a.deleted_at is null and a.end_at>=now() " +
                        "order by a.created_at desc, a.id desc");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            enrichVenue(row);
            String status = displayStatus(row);
            if (visible(row, me) && ("报名中".equals(status) || "活动开始".equals(status))) {
                row.put("display_status", status);
                row.put("signup_limit", signupLimit(row));
                result.add(row);
            }
        }
        return result;
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<Map<String, Object>> activityDetail(@PathVariable int id, HttpServletRequest req) {
        Map<String, Object> me = auth.current(req);
        Map<String, Object> row = Rows.one(jdbc,
                "select a.*, coalesce(nullif(u.callsign,''),u.username) creator_name " +
                        "from activities a left join users u on u.id=a.created_by_id where a.id=?", id);
        if (row == null || !visible(row, me)) throw new IllegalArgumentException("活动不存在或不可见");
        enrichVenue(row);
        int userId = ((Number) me.get("id")).intValue();
        row.put("display_status", displayStatus(row));
        row.put("signup_limit", signupLimit(row));
        row.put("enroll_count", enrolledUserCount(id));
        row.put("is_activity_creator", row.get("created_by_id") != null && String.valueOf(row.get("created_by_id")).equals(String.valueOf(userId)));
        row.put("my_enrollment", Rows.one(jdbc, "select * from enrollments where activity_id=? and user_id=?", id, userId));
        List<Map<String, Object>> squads = Rows.list(jdbc,
                "select s.*, (select count(*) from enrollments e where e.activity_id=s.activity_id and e.camp_no=s.camp_no and e.squad_no=s.squad_no) member_count " +
                        "from squad_settings s where s.activity_id=? order by s.camp_no,s.squad_no", id);
        for (Map<String, Object> squad : squads) {
            int campNo = num(squad.get("camp_no"), 1);
            int squadNo = num(squad.get("squad_no"), 1);
            String channel = text(squad.get("radio_channel"));
            if (channel.isEmpty() || channel.equals(legacyRadioChannel(campNo, squadNo)) || channel.equals(oldRadioChannel(campNo, squadNo))) {
                squad.put("radio_channel", radioChannel(campNo, squadNo));
            }
        }
        row.put("squads", squads);
        row.put("members", Rows.list(jdbc,
                "select e.*, u.username, u.callsign, " +
                        "case when exists(select 1 from attendance_events ev join attendance_records ar on ar.event_id=ev.id " +
                        "where ev.source_activity_id=e.activity_id and ar.user_id=e.user_id and ar.present=1) then 1 else 0 end checked_in " +
                        "from enrollments e join users u on u.id=e.user_id where e.activity_id=? order by e.camp_no,e.squad_no,e.id", id));
        row.put("checkin", Rows.one(jdbc, "select ar.* from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=? and ar.user_id=?", id, userId));
        return ApiResponse.ok(row);
    }

    @PutMapping("/activities/{id}/members/{targetUserId}/squad")
    public ApiResponse<Void> assignMemberSquad(@PathVariable int id, @PathVariable int targetUserId, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        Map<String, Object> activity = Rows.one(jdbc, "select created_by_id,squad_limit from activities where id=?", id);
        if (activity == null) throw new IllegalArgumentException("活动不存在");
        if (activity.get("created_by_id") == null || !String.valueOf(activity.get("created_by_id")).equals(String.valueOf(userId))) {
            throw new SecurityException("只有活动发起人可以分配人员");
        }
        if (Rows.one(jdbc, "select id from enrollments where activity_id=? and user_id=?", id, targetUserId) == null) {
            throw new IllegalArgumentException("该用户未报名");
        }
        int campNo = num(body.get("camp_no"), 1);
        int squadNo = num(body.get("squad_no"), 1);
        String job = String.valueOf(body.get("job"));
        validateJob(id, campNo, squadNo, targetUserId, job);
        Map<String, Object> squad = Rows.one(jdbc, "select * from squad_settings where activity_id=? and camp_no=? and squad_no=?", id, campNo, squadNo);
        if (squad == null) throw new IllegalArgumentException("小队不存在");
        ensureSquadJoinAllowed(id, squad, targetUserId);
        int squadLimit = num(activity.get("squad_limit"), 0);
        if (squadLimit > 0) {
            int members = Rows.list(jdbc, "select id from enrollments where activity_id=? and camp_no=? and squad_no=? and user_id<>?", id, campNo, squadNo, targetUserId).size();
            if (members >= squadLimit) throw new IllegalArgumentException("小队已满");
        }
        jdbc.update("update enrollments set camp_no=?, squad_no=?, job=?, updated_at=now() where activity_id=? and user_id=?", campNo, squadNo, job, id, targetUserId);
        if (squad.get("leader_user_id") == null) jdbc.update("update squad_settings set leader_user_id=? where id=?", targetUserId, squad.get("id"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/attendance/my-summary")
    public ApiResponse<Map<String, Object>> myAttendanceSummary(HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        return ApiResponse.ok(attendanceSummary(userId));
    }

    private Map<String, Object> attendanceSummary(int userId) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("present_count", Rows.one(jdbc,
                "select count(distinct ev.id) total from attendance_events ev join attendance_records ar on ar.event_id=ev.id " +
                        "where ar.user_id=? and ar.present=1",
                userId).get("total"));
        out.put("activity_total", Rows.one(jdbc,
                "select count(*) total from activities where record_type='activity' and deleted_at is null and end_at<=now()").get("total"));
        return out;
    }

    @PostMapping("/activities/{id}/enroll")
    public ApiResponse<Void> enroll(@PathVariable int id, HttpServletRequest req) {
        Map<String, Object> me = auth.current(req);
        int userId = ((Number) me.get("id")).intValue();
        Map<String, Object> activity = Rows.one(jdbc, "select * from activities where id=?", id);
        if (activity == null || !"报名中".equals(displayStatus(activity))) throw new IllegalArgumentException("当前活动不可报名");
        if (Rows.one(jdbc, "select id from enrollments where activity_id=? and user_id=?", id, userId) != null) return ApiResponse.ok(null);
        int count = enrolledUserCount(id);
        if (count >= signupLimit(activity)) throw new IllegalArgumentException("活动名额已满");
        jdbc.update("insert into enrollments(activity_id,user_id,rent_launcher,created_at,updated_at) values(?,?,0,now(),now())", id, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/activities/{id}/enroll")
    public ApiResponse<Void> cancelEnroll(@PathVariable int id, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        if (Rows.one(jdbc, "select ar.id from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=? and ar.user_id=? and ar.present=1", id, userId) != null) {
            throw new IllegalArgumentException("已签到，不能取消报名");
        }
        jdbc.update("delete from enrollments where activity_id=? and user_id=?", id, userId);
        jdbc.update("update squad_settings set leader_user_id=null where activity_id=? and leader_user_id=?", id, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/activities/{id}/checkin")
    public ApiResponse<Void> checkin(@PathVariable int id, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        if (Rows.one(jdbc, "select id from enrollments where activity_id=? and user_id=?", id, userId) == null) throw new IllegalArgumentException("请先报名");
        Map<String, Object> a = Rows.one(jdbc, "select * from activities where id=?", id);
        LocalDateTime start = LocalDateTime.parse(String.valueOf(a.get("start_at")).replace(" ", "T"));
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(start.toLocalDate()) || now.isBefore(start.minusHours(3))) throw new IllegalArgumentException("活动当天开始前3小时才允许签到");
        Integer eventId = ensureAttendanceEvent(id, a);
        if (Rows.one(jdbc, "select id from attendance_records where event_id=? and user_id=?", eventId, userId) == null) {
            jdbc.update("insert into attendance_records(event_id,user_id,present,updated_by_id,updated_at) values(?,?,1,?,now())", eventId, userId, userId);
        } else {
            jdbc.update("update attendance_records set present=1, updated_by_id=?, updated_at=now() where event_id=? and user_id=?", userId, eventId, userId);
        }
        return ApiResponse.ok(null);
    }

    @PutMapping("/activities/{id}/squad")
    public ApiResponse<Void> joinSquad(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        int campNo = num(body.get("camp_no"), 1);
        int squadNo = num(body.get("squad_no"), 1);
        String job = String.valueOf(body.get("job"));
        validateJob(id, campNo, squadNo, userId, job);
        Map<String, Object> squad = Rows.one(jdbc, "select * from squad_settings where activity_id=? and camp_no=? and squad_no=?", id, campNo, squadNo);
        if (squad == null) throw new IllegalArgumentException("小队不存在");
        ensureSquadJoinAllowed(id, squad, userId);
        Map<String, Object> activity = Rows.one(jdbc, "select squad_limit from activities where id=?", id);
        int squadLimit = num(activity.get("squad_limit"), 0);
        if (squadLimit > 0) {
            int members = Rows.list(jdbc, "select id from enrollments where activity_id=? and camp_no=? and squad_no=? and user_id<>?", id, campNo, squadNo, userId).size();
            if (members >= squadLimit) throw new IllegalArgumentException("小队已满");
        }
        jdbc.update("update enrollments set camp_no=?, squad_no=?, job=?, updated_at=now() where activity_id=? and user_id=?", campNo, squadNo, job, id, userId);
        jdbc.update("update squad_settings set leader_user_id=null where activity_id=? and leader_user_id=? and id<>?", id, userId, squad.get("id"));
        if (squad.get("leader_user_id") == null) jdbc.update("update squad_settings set leader_user_id=? where id=?", userId, squad.get("id"));
        return ApiResponse.ok(null);
    }

    @PutMapping("/activities/{id}/squads/{squadId}/lock")
    public ApiResponse<Void> lockSquad(@PathVariable int id, @PathVariable int squadId, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        Map<String, Object> squad = Rows.one(jdbc, "select * from squad_settings where id=? and activity_id=?", squadId, id);
        if (squad == null) throw new IllegalArgumentException("小队不存在");
        if (!String.valueOf(squad.get("leader_user_id")).equals(String.valueOf(userId))) throw new SecurityException("只有队长可以锁定小队");
        int locked = bool(body.get("locked")) ? 1 : 0;
        jdbc.update("update squad_settings set locked=?, updated_at=now() where id=?", locked, squadId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/activities/{id}/squad/leader")
    public ApiResponse<Void> transferLeader(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        int targetId = num(body.get("user_id"), 0);
        Map<String, Object> mine = Rows.one(jdbc, "select * from enrollments where activity_id=? and user_id=?", id, userId);
        Map<String, Object> target = Rows.one(jdbc, "select * from enrollments where activity_id=? and user_id=?", id, targetId);
        if (mine == null || target == null || !String.valueOf(mine.get("camp_no")).equals(String.valueOf(target.get("camp_no"))) || !String.valueOf(mine.get("squad_no")).equals(String.valueOf(target.get("squad_no")))) throw new IllegalArgumentException("只能转让给本小队成员");
        Map<String, Object> squad = Rows.one(jdbc, "select * from squad_settings where activity_id=? and camp_no=? and squad_no=?", id, mine.get("camp_no"), mine.get("squad_no"));
        if (squad == null || !String.valueOf(squad.get("leader_user_id")).equals(String.valueOf(userId))) throw new SecurityException("只有队长可以转让");
        jdbc.update("update squad_settings set leader_user_id=? where id=?", targetId, squad.get("id"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/activity-plans")
    public ApiResponse<List<Map<String, Object>>> plans(HttpServletRequest req) {
        Map<String, Object> me = auth.current(req);
        int userId = ((Number) me.get("id")).intValue();
        return ApiResponse.ok(planRows(me, userId));
    }

    private List<Map<String, Object>> planRows(Map<String, Object> me, int userId) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select p.*, coalesce(nullif(u.callsign,''),u.username) creator_name " +
                        "from activity_plans p left join users u on u.id=p.created_by_id " +
                        "where p.hidden=0 and p.converted_activity_id is null and p.vote_deadline>=now() " +
                        "order by p.created_at desc, p.id desc");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (rows.isEmpty()) return result;

        String activePlans = "select id from activity_plans where hidden=0 and converted_activity_id is null and vote_deadline>=now()";
        Map<Integer, List<Map<String, Object>>> datesByPlan = groupByPlan(Rows.list(jdbc,
                "select d.*,count(v.id) vote_count from plan_date_options d " +
                        "left join plan_votes v on v.plan_id=d.plan_id and v.option_type='date' and v.option_id=d.id " +
                        "where d.plan_id in (" + activePlans + ") group by d.id order by d.plan_id,d.date"));
        Map<Integer, List<Map<String, Object>>> venuesByPlan = groupByPlan(Rows.list(jdbc,
                "select o.plan_id,v.*,count(pv.id) vote_count from plan_venue_options o join venues v on v.id=o.venue_id " +
                        "left join plan_votes pv on pv.plan_id=o.plan_id and pv.option_type='venue' and pv.option_id=v.id " +
                        "where o.plan_id in (" + activePlans + ") group by o.plan_id,v.id order by o.plan_id,v.id"));
        Map<Integer, List<Map<String, Object>>> modesByPlan = groupByPlan(Rows.list(jdbc,
                "select o.plan_id,g.*,count(pv.id) vote_count from plan_game_mode_options o join game_modes g on g.id=o.game_mode_id " +
                        "left join plan_votes pv on pv.plan_id=o.plan_id and pv.option_type='game_mode' and pv.option_id=g.id " +
                        "where o.plan_id in (" + activePlans + ") group by o.plan_id,g.id order by o.plan_id,g.id"));
        Map<Integer, Map<String, List<Integer>>> votesByPlan = new HashMap<Integer, Map<String, List<Integer>>>();
        for (Map<String, Object> vote : Rows.list(jdbc,
                "select plan_id,option_type,option_id from plan_votes where user_id=? and plan_id in (" + activePlans + ") order by id", userId)) {
            int planId = num(vote.get("plan_id"), 0);
            String type = text(vote.get("option_type"));
            Map<String, List<Integer>> byType = votesByPlan.computeIfAbsent(planId, key -> new HashMap<String, List<Integer>>());
            byType.computeIfAbsent(type, key -> new ArrayList<Integer>()).add(num(vote.get("option_id"), 0));
        }
        for (Map<String, Object> row : rows) {
            if (visible(row, me)) {
                applyDefaultPlanBanner(row);
                int id = ((Number) row.get("id")).intValue();
                row.put("dates", datesByPlan.getOrDefault(id, Collections.emptyList()));
                row.put("venues", venuesByPlan.getOrDefault(id, Collections.emptyList()));
                row.put("game_modes", modesByPlan.getOrDefault(id, Collections.emptyList()));
                row.put("display_status", "策划中");
                Map<String, List<Integer>> myVotes = votesByPlan.getOrDefault(id, Collections.emptyMap());
                row.put("voted", !myVotes.isEmpty());
                row.put("my_date_option_ids", myVotes.getOrDefault("date", Collections.emptyList()));
                row.put("my_venue_ids", myVotes.getOrDefault("venue", Collections.emptyList()));
                row.put("my_game_mode_ids", myVotes.getOrDefault("game_mode", Collections.emptyList()));
                result.add(row);
            }
        }
        return result;
    }

    @GetMapping("/activities/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap(HttpServletRequest req) {
        Map<String, Object> me = auth.current(req);
        int userId = ((Number) me.get("id")).intValue();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("activities", activityRows(me));
        out.put("plans", planRows(me, userId));
        out.put("attendance_summary", attendanceSummary(userId));
        out.put("notifications", notificationRows(userId));
        return ApiResponse.ok(out);
    }

    private List<Map<String, Object>> notificationRows(int userId) {
        return Rows.list(jdbc,
                "select n.*, r.id rental_action_id, r.status rental_status, r.user_id rental_user_id, r.launcher_id rental_launcher_id " +
                        "from user_notifications n " +
                        "left join activity_launcher_rentals r on r.id=(select r2.id from activity_launcher_rentals r2 " +
                        "join launcher_rental_items l2 on l2.id=r2.launcher_id " +
                        "where n.type='launcher_rental' and (r2.id=n.related_id or (r2.launcher_id=n.related_id and l2.created_by_id=n.user_id)) " +
                        "order by case when r2.id=n.related_id then 0 else 1 end, r2.id desc limit 1) " +
                        "where n.user_id=? order by n.created_at desc,n.id desc limit 50", userId);
    }

    @PostMapping("/activity-plans/{id}/vote")
    public ApiResponse<Void> vote(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        if (Rows.one(jdbc, "select id from plan_votes where plan_id=? and user_id=?", id, userId) != null) throw new IllegalArgumentException("已投票");
        for (Object dateId : list(body.get("date_option_ids"))) jdbc.update("insert into plan_votes(plan_id,user_id,option_type,option_id,created_at) values(?,?,?, ?,now())", id, userId, "date", dateId);
        for (Object venueId : list(body.get("venue_ids"))) jdbc.update("insert into plan_votes(plan_id,user_id,option_type,option_id,created_at) values(?,?,?, ?,now())", id, userId, "venue", venueId);
        for (Object modeId : list(body.get("game_mode_ids"))) jdbc.update("insert into plan_votes(plan_id,user_id,option_type,option_id,created_at) values(?,?,?, ?,now())", id, userId, "game_mode", modeId);
        return ApiResponse.ok(null);
    }

    private boolean visible(Map<String, Object> row, Map<String, Object> user) {
        String visibility = String.valueOf(row.get("visibility_type"));
        if ("all".equals(visibility)) return true;
        boolean regular = "1".equals(String.valueOf(user.get("is_regular_member"))) || Boolean.TRUE.equals(user.get("is_regular_member"));
        if ("official".equals(visibility)) return regular;
        if ("official_plus_invite".equals(visibility)) return regular || Rows.csv(String.valueOf(row.get("invitee_ids"))).contains(String.valueOf(user.get("id")));
        return true;
    }

    private void validateJob(int activityId, int campNo, int squadNo, int userId, String job) {
        Map<String, Object> activity = Rows.one(jdbc, "select squad_limit,allowed_jobs from activities where id=?", activityId);
        if (job == null || job.trim().isEmpty() || "请选择职业".equals(job)) throw new IllegalArgumentException("请选择职业");
        if (!Rows.csv(String.valueOf(activity.get("allowed_jobs"))).contains(job)) throw new IllegalArgumentException("该活动未开放" + job);
        if (!Arrays.asList("狙击手", "医疗兵", "弹药兵").contains(job)) return;
        int count = Rows.list(jdbc, "select id from enrollments where activity_id=? and camp_no=? and squad_no=? and job=? and user_id<>?", activityId, campNo, squadNo, job, userId).size();
        int quota = Math.max(1, num(activity.get("squad_limit"), 5) / 5);
        if (count >= quota) throw new IllegalArgumentException("该小队每5人只允许1个" + job);
    }

    private void ensureSquadJoinAllowed(int activityId, Map<String, Object> squad, int userId) {
        if (!bool(squad.get("locked"))) return;
        Map<String, Object> mine = Rows.one(jdbc, "select camp_no,squad_no from enrollments where activity_id=? and user_id=?", activityId, userId);
        boolean alreadyInSquad = mine != null
                && String.valueOf(mine.get("camp_no")).equals(String.valueOf(squad.get("camp_no")))
                && String.valueOf(mine.get("squad_no")).equals(String.valueOf(squad.get("squad_no")));
        if (!alreadyInSquad) throw new IllegalArgumentException("小队已锁定，无法加入");
    }

    private Integer ensureAttendanceEvent(int activityId, Map<String, Object> a) {
        Map<String, Object> existing = Rows.one(jdbc, "select id from attendance_events where source_activity_id=?", activityId);
        if (existing != null) return ((Number) existing.get("id")).intValue();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("insert into attendance_events(source_activity_id,name,event_date,location,organizer,activity_region,is_manual,created_at) values(?,?,date(?),?,?,?,0,now())", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, activityId);
            ps.setObject(2, a.get("name"));
            ps.setObject(3, a.get("start_at"));
            ps.setObject(4, a.get("location"));
            ps.setObject(5, "");
            ps.setObject(6, a.get("activity_region"));
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    private String displayStatus(Map<String, Object> row) {
        if (row.get("deleted_at") != null) return "活动取消";
        String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
        return String.valueOf(row.get("start_at")).compareTo(now) > 0 ? "报名中" : String.valueOf(row.get("end_at")).compareTo(now) < 0 ? "活动结束" : "活动开始";
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

    private void enrichVenue(Map<String, Object> row) {
        boolean customBanner = "custom".equals(text(row.get("banner_source"))) && !text(row.get("banner_url")).isEmpty();
        Map<String, Object> venue = null;
        if (row.get("venue_id") != null) {
            venue = Rows.one(jdbc, "select name,address,image_url from venues where id=?", row.get("venue_id"));
        }
        if (venue == null) {
            String location = text(row.get("location"));
            if (!location.isEmpty()) venue = Rows.one(jdbc, "select name,address,image_url from venues where name=? or address=? limit 1", location, location);
        }
        if (venue != null) {
            if (!customBanner && !text(venue.get("image_url")).isEmpty()) row.put("banner_url", venue.get("image_url"));
            row.put("venue_name", venue.get("name"));
            row.put("venue_address", venue.get("address"));
        } else {
            row.put("venue_name", row.get("location"));
            row.put("venue_address", row.get("location"));
        }
    }

    private void applyDefaultPlanBanner(Map<String, Object> row) {
        if (text(row.get("banner_url")).isEmpty()) row.put("banner_url", DEFAULT_PLAN_BANNER);
    }

    private Map<Integer, List<Map<String, Object>>> groupByPlan(List<Map<String, Object>> rows) {
        Map<Integer, List<Map<String, Object>>> grouped = new HashMap<Integer, List<Map<String, Object>>>();
        for (Map<String, Object> row : rows) {
            int planId = num(row.get("plan_id"), 0);
            grouped.computeIfAbsent(planId, key -> new ArrayList<Map<String, Object>>()).add(row);
        }
        return grouped;
    }

    private List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    private List<Integer> voteOptionIds(int planId, int userId, String optionType) {
        List<Integer> ids = new ArrayList<Integer>();
        for (Map<String, Object> row : Rows.list(jdbc,
                "select option_id from plan_votes where plan_id=? and user_id=? and option_type=? order by id",
                planId, userId, optionType)) {
            ids.add(((Number) row.get("option_id")).intValue());
        }
        return ids;
    }

    private int enrolledUserCount(int activityId) {
        Integer count = jdbc.queryForObject(
                "select count(distinct user_id) from enrollments where activity_id=?",
                Integer.class,
                activityId);
        return count == null ? 0 : count;
    }

    private boolean bool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    private String radioChannel(int campNo, int squadNo) {
        return (438 + campNo) + "." + String.format("%03d", squadNo * 100);
    }

    private String legacyRadioChannel(int campNo, int squadNo) {
        return (434 + campNo) + "." + String.format("%03d", (squadNo - 1) * 100);
    }

    private String oldRadioChannel(int campNo, int squadNo) {
        return (434 + campNo) + "." + String.format("%03d", squadNo * 100);
    }

    private int num(Object v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(String.valueOf(v).replaceAll("[^0-9]", "")); } catch (Exception e) { return fallback; }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
