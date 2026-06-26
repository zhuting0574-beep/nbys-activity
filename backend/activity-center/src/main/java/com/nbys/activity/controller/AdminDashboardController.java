package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminDashboardController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(required = false) Integer year,
                                                     HttpServletRequest req) {
        auth.current(req);
        int y = year == null ? LocalDate.now().getYear() : year;

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("year", y);
        out.put("cards", cards(y));
        out.put("monthly", monthly(y));
        out.put("rankings", rankings(y));
        out.put("recent_activities", recentActivities(y));
        return ApiResponse.ok(out);
    }

    private Map<String, Object> cards(int year) {
        long enrollTotal = scalar("select count(*) total from enrollments e join activities a on a.id=e.activity_id where a.record_type='activity' and a.deleted_at is null and year(a.start_at)=?", year);
        long checkinTotal = scalar("select count(*) total from attendance_records ar join attendance_events ev on ev.id=ar.event_id where ar.present=1 and year(ev.event_date)=?", year);

        Map<String, Object> cards = new LinkedHashMap<String, Object>();
        cards.put("user_total", scalar("select count(*) total from users"));
        cards.put("formal_member_total", scalar("select count(*) total from users where disabled=0 and is_regular_member=1"));
        cards.put("activity_total", scalar("select count(*) total from activities where record_type='activity' and deleted_at is null and year(start_at)=?", year));
        cards.put("upcoming_activity_total", scalar("select count(*) total from activities where record_type='activity' and deleted_at is null and year(start_at)=? and start_at>=now()", year));
        cards.put("enroll_total", enrollTotal);
        cards.put("checkin_total", checkinTotal);
        cards.put("checkin_rate", rate(checkinTotal, enrollTotal));
        return cards;
    }

    private Map<String, Object> monthly(int year) {
        List<Integer> months = new ArrayList<Integer>();
        List<Long> activities = zeros();
        List<Long> enrollments = zeros();
        List<Long> checkins = zeros();
        for (int i = 1; i <= 12; i++) months.add(i);

        fillMonthly(activities, Rows.list(jdbc,
                "select month(start_at) month,count(*) count from activities where record_type='activity' and deleted_at is null and year(start_at)=? group by month(start_at)",
                year));
        fillMonthly(enrollments, Rows.list(jdbc,
                "select month(a.start_at) month,count(*) count from enrollments e join activities a on a.id=e.activity_id where a.record_type='activity' and a.deleted_at is null and year(a.start_at)=? group by month(a.start_at)",
                year));
        fillMonthly(checkins, Rows.list(jdbc,
                "select month(ev.event_date) month,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id where ar.present=1 and year(ev.event_date)=? group by month(ev.event_date)",
                year));

        Map<String, Object> monthly = new LinkedHashMap<String, Object>();
        monthly.put("months", months);
        monthly.put("activities", activities);
        monthly.put("enrollments", enrollments);
        monthly.put("checkins", checkins);
        return monthly;
    }

    private Map<String, Object> rankings(int year) {
        Map<String, Object> rankings = new LinkedHashMap<String, Object>();
        rankings.put("popular_activities", Rows.list(jdbc,
                "select a.id,a.name,count(e.id) enroll_count," +
                        "(select count(*) from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=a.id and ar.present=1) checkin_count " +
                        "from activities a left join enrollments e on e.activity_id=a.id " +
                        "where a.record_type='activity' and a.deleted_at is null and year(a.start_at)=? " +
                        "group by a.id,a.name order by enroll_count desc,checkin_count desc,a.start_at desc limit 5",
                year));
        rankings.put("popular_venues", Rows.list(jdbc,
                "select ev.location name,count(ar.id) count from attendance_events ev left join attendance_records ar on ar.event_id=ev.id and ar.present=1 " +
                        "where year(ev.event_date)=? and coalesce(ev.location,'')<>'' group by ev.location order by count desc limit 5",
                year));
        rankings.put("popular_game_modes", popularGameModes(year));
        rankings.put("active_members", Rows.list(jdbc,
                "select u.id,u.username,u.callsign,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id join users u on u.id=ar.user_id " +
                        "where ar.present=1 and year(ev.event_date)=? group by u.id,u.username,u.callsign order by count desc limit 5",
                year));
        return rankings;
    }

    private List<Map<String, Object>> recentActivities(int year) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select a.id,a.name,a.start_at,a.location,a.deleted_at," +
                        "(select count(*) from enrollments e where e.activity_id=a.id) enroll_count," +
                        "(select count(*) from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=a.id and ar.present=1) checkin_count " +
                        "from activities a where a.record_type='activity' and year(a.start_at)=? order by a.start_at desc,a.id desc limit 8",
                year);
        for (Map<String, Object> row : rows) row.put("status", displayStatus(row));
        return rows;
    }

    private List<Map<String, Object>> popularGameModes(int year) {
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select game_modes from activities where record_type='activity' and deleted_at is null and year(start_at)=?",
                year);
        for (Map<String, Object> row : rows) {
            for (String mode : Rows.csv(String.valueOf(row.get("game_modes")))) {
                counts.put(mode, counts.containsKey(mode) ? counts.get(mode) + 1 : 1);
            }
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(counts.entrySet());
        entries.sort((a, b) -> {
            int byCount = Long.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        for (Map.Entry<String, Long> entry : entries) {
            if (out.size() >= 5) break;
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", entry.getKey());
            item.put("count", entry.getValue());
            out.add(item);
        }
        return out;
    }

    private long scalar(String sql, Object... args) {
        Map<String, Object> row = Rows.one(jdbc, sql, args);
        if (row == null || row.get("total") == null) return 0;
        return ((Number) row.get("total")).longValue();
    }

    private BigDecimal rate(long value, long total) {
        if (total <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(value * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    private List<Long> zeros() {
        Long[] values = new Long[12];
        Arrays.fill(values, 0L);
        return new ArrayList<Long>(Arrays.asList(values));
    }

    private void fillMonthly(List<Long> target, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            int month = ((Number) row.get("month")).intValue();
            if (month >= 1 && month <= 12) target.set(month - 1, ((Number) row.get("count")).longValue());
        }
    }

    private String displayStatus(Map<String, Object> row) {
        if (row.get("deleted_at") != null) return "已取消";
        Object start = row.get("start_at");
        if (start == null) return "待定";
        String text = String.valueOf(start).replace('T', ' ');
        String now = java.time.LocalDateTime.now().toString().replace('T', ' ');
        return text.compareTo(now) >= 0 ? "待开始" : "已结束";
    }
}
