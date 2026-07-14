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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private static final long CACHE_TTL_MS = 10_000L;
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final Map<Integer, CachedDashboard> cache = new ConcurrentHashMap<Integer, CachedDashboard>();
    private final ExecutorService dashboardExecutor = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "dashboard-query");
        thread.setDaemon(true);
        return thread;
    });

    public AdminDashboardController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(required = false) Integer year,
                                                     HttpServletRequest req) {
        auth.current(req);
        int y = year == null ? LocalDate.now().getYear() : year;
        return ApiResponse.ok(cachedOverview(y));
    }

    private synchronized Map<String, Object> cachedOverview(int year) {
        CachedDashboard cached = cache.get(year);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAt < CACHE_TTL_MS) return cached.data;
        CompletableFuture<Map<String, Object>> cards = CompletableFuture.supplyAsync(() -> cards(year), dashboardExecutor);
        CompletableFuture<Map<String, Object>> monthly = CompletableFuture.supplyAsync(() -> monthly(year), dashboardExecutor);
        CompletableFuture<List<Map<String, Object>>> popularActivities = CompletableFuture.supplyAsync(() -> popularActivities(year), dashboardExecutor);
        CompletableFuture<List<Map<String, Object>>> popularVenues = CompletableFuture.supplyAsync(() -> popularVenues(year), dashboardExecutor);
        CompletableFuture<List<Map<String, Object>>> popularGameModes = CompletableFuture.supplyAsync(() -> popularGameModes(year), dashboardExecutor);
        CompletableFuture<List<Map<String, Object>>> activeMembers = CompletableFuture.supplyAsync(() -> activeMembers(year), dashboardExecutor);
        CompletableFuture<List<Map<String, Object>>> recentActivities = CompletableFuture.supplyAsync(() -> recentActivities(year), dashboardExecutor);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("year", year);
        out.put("cards", cards.join());
        out.put("monthly", monthly.join());
        Map<String, Object> rankingData = new LinkedHashMap<String, Object>();
        rankingData.put("popular_activities", popularActivities.join());
        rankingData.put("popular_venues", popularVenues.join());
        rankingData.put("popular_game_modes", popularGameModes.join());
        rankingData.put("active_members", activeMembers.join());
        out.put("rankings", rankingData);
        out.put("recent_activities", recentActivities.join());
        cache.put(year, new CachedDashboard(now, out));
        return out;
    }

    @PreDestroy
    public void shutdownDashboardExecutor() {
        dashboardExecutor.shutdown();
    }

    private Map<String, Object> cards(int year) {
        String start = year + "-01-01";
        String end = (year + 1) + "-01-01";
        Map<String, Object> totals = Rows.one(jdbc,
                "select " +
                        "(select count(*) from users) user_total," +
                        "(select count(*) from users where disabled=0 and is_regular_member=1) formal_member_total," +
                        "(select count(*) from activities where record_type='activity' and deleted_at is null and start_at>=? and start_at<?) activity_total," +
                        "(select count(*) from activities where record_type='activity' and deleted_at is null and start_at>=greatest(now(),?) and start_at<?) upcoming_activity_total," +
                        "(select coalesce(sum(1+coalesce(e.extra_count,0)),0) from enrollments e join activities a on a.id=e.activity_id where a.record_type='activity' and a.deleted_at is null and a.start_at>=? and a.start_at<?) enroll_total," +
                        "(select count(*) from attendance_records ar join attendance_events ev on ev.id=ar.event_id left join activities aa on aa.id=ev.source_activity_id where ar.present=1 and ev.event_date>=? and ev.event_date<? and (ev.source_activity_id is null or coalesce(aa.attendance_enabled,1)=1)) checkin_total",
                start, end, start, end, start, end, start, end);
        long enrollTotal = number(totals.get("enroll_total"));
        long checkinTotal = number(totals.get("checkin_total"));
        Map<String, Object> cards = new LinkedHashMap<String, Object>();
        cards.put("user_total", totals.get("user_total"));
        cards.put("formal_member_total", totals.get("formal_member_total"));
        cards.put("activity_total", totals.get("activity_total"));
        cards.put("upcoming_activity_total", totals.get("upcoming_activity_total"));
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

        String start = year + "-01-01";
        String end = (year + 1) + "-01-01";
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select 'activities' series,month(start_at) month,count(*) count from activities " +
                        "where record_type='activity' and deleted_at is null and start_at>=? and start_at<? group by month(start_at) " +
                        "union all select 'enrollments' series,month(a.start_at) month,coalesce(sum(1+coalesce(e.extra_count,0)),0) count from enrollments e join activities a on a.id=e.activity_id " +
                        "where a.record_type='activity' and a.deleted_at is null and a.start_at>=? and a.start_at<? group by month(a.start_at) " +
                        "union all select 'checkins' series,month(ev.event_date) month,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id left join activities aa on aa.id=ev.source_activity_id " +
                        "where ar.present=1 and ev.event_date>=? and ev.event_date<? and (ev.source_activity_id is null or coalesce(aa.attendance_enabled,1)=1) group by month(ev.event_date)",
                start, end, start, end, start, end);
        for (Map<String, Object> row : rows) {
            String series = String.valueOf(row.get("series"));
            fillMonthly("activities".equals(series) ? activities : "enrollments".equals(series) ? enrollments : checkins,
                    java.util.Collections.singletonList(row));
        }

        Map<String, Object> monthly = new LinkedHashMap<String, Object>();
        monthly.put("months", months);
        monthly.put("activities", activities);
        monthly.put("enrollments", enrollments);
        monthly.put("checkins", checkins);
        return monthly;
    }

    private List<Map<String, Object>> popularActivities(int year) {
        return Rows.list(jdbc,
                "select a.id,a.name,coalesce(sum(case when e.id is null then 0 else 1+coalesce(e.extra_count,0) end),0) enroll_count," +
                        "(select count(*) from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=a.id and ar.present=1 and coalesce(a.attendance_enabled,1)=1) checkin_count " +
                        "from activities a left join enrollments e on e.activity_id=a.id " +
                        "where a.record_type='activity' and a.deleted_at is null and year(a.start_at)=? " +
                        "group by a.id,a.name order by enroll_count desc,checkin_count desc,a.start_at desc limit 5",
                year);
    }

    private List<Map<String, Object>> popularVenues(int year) {
        return Rows.list(jdbc,
                "select ev.location name,count(ar.id) count from attendance_events ev left join activities a on a.id=ev.source_activity_id left join attendance_records ar on ar.event_id=ev.id and ar.present=1 " +
                        "where year(ev.event_date)=? and coalesce(ev.location,'')<>'' and (ev.source_activity_id is null or coalesce(a.attendance_enabled,1)=1) group by ev.location order by count desc limit 5",
                year);
    }

    private List<Map<String, Object>> activeMembers(int year) {
        return Rows.list(jdbc,
                "select u.id,u.username,u.callsign,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id left join activities a on a.id=ev.source_activity_id join users u on u.id=ar.user_id " +
                        "where ar.present=1 and year(ev.event_date)=? and (ev.source_activity_id is null or coalesce(a.attendance_enabled,1)=1) group by u.id,u.username,u.callsign order by count desc limit 5",
                year);
    }

    private List<Map<String, Object>> recentActivities(int year) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select a.id,a.name,a.start_at,a.location,a.deleted_at," +
                        "(select coalesce(sum(1+coalesce(e.extra_count,0)),0) from enrollments e where e.activity_id=a.id) enroll_count," +
                        "(select count(*) from attendance_events ev join attendance_records ar on ar.event_id=ev.id where ev.source_activity_id=a.id and ar.present=1 and coalesce(a.attendance_enabled,1)=1) checkin_count " +
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

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
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

    private static class CachedDashboard {
        private final long createdAt;
        private final Map<String, Object> data;

        private CachedDashboard(long createdAt, Map<String, Object> data) {
            this.createdAt = createdAt;
            this.data = data;
        }
    }
}
