package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

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
                                                    @RequestParam(required = false, defaultValue = "false") boolean formalOnly,
                                                    HttpServletRequest req) {
        auth.require(req, "attendance:view");
        String r = region == null ? "" : region;
        String start = year == null ? null : year + "-01-01";
        String end = year == null ? null : (year + 1) + "-01-01";
        String enabledAttendance = enabledAttendanceSql();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("activity_total", Rows.one(jdbc, "select count(*) total from attendance_events ev where (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?)" + enabledAttendance, year, start, end, r, r).get("total"));
        out.put("top_formal_members", Rows.list(jdbc,
                "select u.id,u.username,u.callsign,count(*) count from attendance_records ar join attendance_events ev on ev.id=ar.event_id join users u on u.id=ar.user_id " +
                        "where ar.present=1 and u.is_regular_member=1 and (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?)" + enabledAttendance + " group by u.id order by count desc limit 3", year, start, end, r, r));
        List<Map<String, Object>> topOrganizers = Rows.list(jdbc,
                "select u.id,coalesce(nullif(u.callsign,''),u.username) organizer,count(*) count " +
                        "from attendance_events ev join users u on find_in_set(cast(u.id as char),coalesce(ev.organizer_ids,''))>0 " +
                        "where (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?)" + enabledAttendance + " " +
                        "group by u.id,u.username,u.callsign order by count desc,organizer limit 3", year, start, end, r, r);
        List<Map<String, Object>> popularVenues = Rows.list(jdbc,
                "select ev.location,count(ar.id) count from attendance_events ev left join attendance_records ar on ar.event_id=ev.id and ar.present=1 where (? is null or (ev.event_date>=? and ev.event_date<?)) and (?='' or ev.activity_region=?) and coalesce(ev.location,'')<>''" + enabledAttendance + " group by ev.location order by count desc limit 3", year, start, end, r, r);
        out.put("top_organizers", topOrganizers);
        out.put("popular_venues", popularVenues);
        out.put("top_organizer", topOrganizers.isEmpty() ? null : topOrganizers.get(0));
        out.put("popular_venue", popularVenues.isEmpty() ? null : popularVenues.get(0));
        return ApiResponse.ok(out);
    }

    @GetMapping("/matrix")
    public ApiResponse<Map<String, Object>> matrix(@RequestParam(required = false) Integer year,
                                                   @RequestParam(required = false) String region,
                                                   @RequestParam(required = false, defaultValue = "false") boolean formalOnly,
                                                   @RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer userId,
                                                   HttpServletRequest req) {
        auth.require(req, "attendance:view");
        String r = region == null ? "" : region;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate startDay = startDate == null || startDate.trim().isEmpty()
                ? (year == null ? today.minusMonths(3) : LocalDate.of(year, 1, 1)) : LocalDate.parse(startDate);
        LocalDate endDay = endDate == null || endDate.trim().isEmpty()
                ? (year == null ? today : LocalDate.of(year, 12, 31)) : LocalDate.parse(endDate);
        validateRange(startDay, endDay);
        String start = startDay.toString();
        String end = endDay.plusDays(1).toString();
        String q = keyword == null ? "" : keyword.trim();
        String enabledAttendance = enabledAttendanceSql();
        List<Map<String, Object>> events = Rows.list(jdbc, "select * from attendance_events ev where ev.event_date>=? and ev.event_date<? and (?='' or ev.activity_region=?)" + enabledAttendance + " order by ev.event_date", start, end, r, r);
        int formal = formalOnly ? 1 : 0;
        List<Map<String, Object>> users = Rows.list(jdbc, "select id,username,callsign,is_regular_member from users where disabled=0 and (?=0 or is_regular_member=1) and (? is null or id=?) and (?='' or username like concat('%',?,'%') or callsign like concat('%',?,'%')) order by is_regular_member desc,callsign,username", formal, userId, userId, q, q, q);
        if (userId != null && users.isEmpty()) throw new IllegalArgumentException("用户不存在或已禁用");
        Set<Integer> userIds = new HashSet<Integer>();
        for (Map<String, Object> user : users) userIds.add(((Number) user.get("id")).intValue());
        List<Map<String, Object>> records = Rows.list(jdbc,
                "select ar.* from attendance_records ar join attendance_events ev on ev.id=ar.event_id where ev.event_date>=? and ev.event_date<? and (?='' or ev.activity_region=?)" + enabledAttendance, start, end, r, r);
        records.removeIf(row -> !userIds.contains(((Number) row.get("user_id")).intValue()));
        List<Map<String, Object>> enrollments = Rows.list(jdbc,
                "select ev.id event_id,e.user_id from attendance_events ev join enrollments e on e.activity_id=ev.source_activity_id " +
                        "where ev.source_activity_id is not null and ev.event_date>=? and ev.event_date<? and (?='' or ev.activity_region=?)" + enabledAttendance,
                start, end, r, r);
        enrollments.removeIf(row -> !userIds.contains(((Number) row.get("user_id")).intValue()));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("events", events);
        out.put("users", users);
        out.put("records", records);
        out.put("enrollments", enrollments);
        return ApiResponse.ok(out);
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> userAttendance(@PathVariable int userId, HttpServletRequest req) {
        auth.require(req, "attendance:view");
        Map<String, Object> user = Rows.one(jdbc, "select id,username,callsign from users where id=?", userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        List<Map<String, Object>> events = Rows.list(jdbc,
                "select ev.*,ar.present from attendance_events ev left join attendance_records ar on ar.event_id=ev.id and ar.user_id=? " +
                        "where 1=1" + enabledAttendanceSql() + " order by ev.event_date desc,ev.id desc", userId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("user", user);
        result.put("events", events);
        return ApiResponse.ok(result);
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
    @Transactional
    public ApiResponse<Void> updateRecord(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "attendance:update");
        int eventId = ((Number) body.get("event_id")).intValue();
        int userId = ((Number) body.get("user_id")).intValue();
        Map<String, Object> event = Rows.one(jdbc, "select id,source_activity_id from attendance_events where id=?", eventId);
        if (event == null) throw new IllegalArgumentException("出勤活动不存在");
        if (event.get("source_activity_id") != null) {
            int activityId = ((Number) event.get("source_activity_id")).intValue();
            int present = body.containsKey("present") ? (truthy(body.get("present")) ? 1 : 0) : 1;
            if (present == 1) {
                jdbc.update("insert into enrollments(activity_id,user_id,rent_launcher,created_at,updated_at) values(?,?,0,now(),now()) " +
                        "on duplicate key update updated_at=updated_at", activityId, userId);
            }
            jdbc.update("insert into attendance_records(event_id,user_id,present,updated_by_id,updated_at) values(?,?,?,?,now()) " +
                    "on duplicate key update present=values(present),updated_by_id=values(updated_by_id),updated_at=now()",
                    eventId, userId, present, auth.currentUserId(req));
            return ApiResponse.ok(null);
        }
        int present = Boolean.TRUE.equals(body.get("present")) || "1".equals(String.valueOf(body.get("present"))) ? 1 : 0;
        if (Rows.one(jdbc, "select id from attendance_records where event_id=? and user_id=?", eventId, userId) == null) {
            jdbc.update("insert into attendance_records(event_id,user_id,present,updated_by_id,updated_at) values(?,?,?,?,now())", eventId, userId, present, auth.currentUserId(req));
        } else {
            jdbc.update("update attendance_records set present=?, updated_by_id=?, updated_at=now() where event_id=? and user_id=?", present, auth.currentUserId(req), eventId, userId);
        }
        return ApiResponse.ok(null);
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String enabledAttendanceSql() {
        return " and (ev.source_activity_id is null or exists (select 1 from activities a where a.id=ev.source_activity_id and coalesce(a.attendance_enabled,1)=1))";
    }

    @GetMapping("/export")
    public void export(@RequestParam String objectType,
                       @RequestParam(required = false) String periodType,
                       @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(required = false) List<Integer> userIds,
                       HttpServletResponse response, HttpServletRequest req) throws IOException {
        auth.require(req, "attendance:view");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate from;
        LocalDate through;
        if ("year".equals(periodType)) {
            if (year == null) throw new IllegalArgumentException("请选择统计年度");
            from = LocalDate.of(year, 1, 1);
            through = LocalDate.of(year, 12, 31);
        } else {
            from = LocalDate.parse(startDate);
            through = LocalDate.parse(endDate);
        }
        validateRange(from, through);
        String type = objectType == null ? "" : objectType;
        String where = "disabled=0";
        if ("formal".equals(type)) where += " and is_regular_member=1";
        else if ("non_formal".equals(type)) where += " and is_regular_member=0";
        else if ("selected".equals(type)) {
            if (userIds == null || userIds.isEmpty()) throw new IllegalArgumentException("请选择统计人员");
            where += " and id in (" + String.join(",", Collections.nCopies(userIds.size(), "?")) + ")";
        } else if (!"all".equals(type)) throw new IllegalArgumentException("统计对象类型无效");
        List<Object> args = new ArrayList<Object>();
        args.addAll(userIds == null || !"selected".equals(type) ? Collections.emptyList() : userIds);
        List<Map<String, Object>> users = Rows.list(jdbc,
                "select id,username,coalesce(nullif(trim(callsign),''),username) callsign,is_regular_member from users where " + where + " order by is_regular_member desc,callsign,username", args.toArray());
        Map<String, Integer> totals = new HashMap<String, Integer>();
        for (Map<String, Object> row : Rows.list(jdbc,
                "select coalesce(activity_region,'宁波') region,count(*) total from attendance_events ev where ev.event_date>=? and ev.event_date<?" + enabledAttendanceSql() + " group by coalesce(activity_region,'宁波')",
                from.toString(), through.plusDays(1).toString())) totals.put(String.valueOf(row.get("region")), ((Number) row.get("total")).intValue());
        Map<Integer, Map<String, int[]>> stats = new HashMap<Integer, Map<String, int[]>>();
        for (Map<String, Object> row : Rows.list(jdbc,
                "select u.id user_id,coalesce(ev.activity_region,'宁波') region," +
                        "count(distinct case when ar.present=1 then ev.id end) checked_in," +
                        "count(distinct case when e.user_id is not null or ar.present=1 then ev.id end) enrolled " +
                        "from users u cross join attendance_events ev left join attendance_records ar on ar.event_id=ev.id and ar.user_id=u.id " +
                        "left join enrollments e on e.activity_id=ev.source_activity_id and e.user_id=u.id " +
                        "where u.disabled=0 and ev.event_date>=? and ev.event_date<?" + enabledAttendanceSql() +
                        " group by u.id,coalesce(ev.activity_region,'宁波')", from.toString(), through.plusDays(1).toString())) {
            int id = ((Number) row.get("user_id")).intValue();
            stats.computeIfAbsent(id, key -> new HashMap<String, int[]>())
                    .put(String.valueOf(row.get("region")), new int[]{((Number) row.get("enrolled")).intValue(), ((Number) row.get("checked_in")).intValue()});
        }
        String typeLabel = "formal".equals(type) ? "正式队员" : "non_formal".equals(type) ? "非正式队员" : "selected".equals(type) ? "指定人员" : "全员";
        HSSFWorkbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("出勤统计");
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("统计对象类型：" + typeLabel);
        title.createCell(1).setCellValue("统计区间：" + from + " 至 " + through);
        title.createCell(2).setCellValue("宁波活动场次：" + totals.getOrDefault("宁波", 0));
        title.createCell(3).setCellValue("外地活动场次：" + totals.getOrDefault("外地", 0));
        CellStyle percentStyle = workbook.createCellStyle();
        percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        Row header = sheet.createRow(1);
        String[] columns = {"序号", "姓名", "呼号", "宁波报名次数", "宁波打卡次数", "宁波下场率", "外地报名次数", "外地打卡次数", "外地下场率"};
        for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            Map<String, int[]> byRegion = stats.getOrDefault(((Number) user.get("id")).intValue(), Collections.emptyMap());
            int[] ningbo = byRegion.getOrDefault("宁波", new int[2]);
            int[] other = byRegion.getOrDefault("外地", new int[2]);
            Row row = sheet.createRow(i + 2);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(String.valueOf(user.get("username")));
            row.createCell(2).setCellValue(String.valueOf(user.get("callsign")));
            row.createCell(3).setCellValue(ningbo[0]); row.createCell(4).setCellValue(ningbo[1]);
            Cell ningboRate = row.createCell(5); ningboRate.setCellValue(rate(ningbo[1], totals.getOrDefault("宁波", 0))); ningboRate.setCellStyle(percentStyle);
            row.createCell(6).setCellValue(other[0]); row.createCell(7).setCellValue(other[1]);
            Cell otherRate = row.createCell(8); otherRate.setCellValue(rate(other[1], totals.getOrDefault("外地", 0))); otherRate.setCellStyle(percentStyle);
        }
        int[] columnWidths = {8, 20, 20, 16, 16, 16, 16, 16, 16};
        for (int i = 0; i < columnWidths.length; i++) sheet.setColumnWidth(i, columnWidths[i] * 256);
        String filename = "宁波甬士出勤统计" + today.toString().replace("-", "") + ".xls";
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    private double rate(int checkins, int total) { return total == 0 ? 0d : (double) checkins / total; }

    private void validateRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不得早于开始日期");
        if (end.isAfter(start.plusYears(1).minusDays(1))) throw new IllegalArgumentException("统计时间跨度最长为一年");
    }
}
