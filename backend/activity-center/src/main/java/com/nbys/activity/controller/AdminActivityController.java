package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.ActivityLimitCalculator;
import com.nbys.activity.service.PlanOptionSynchronizer;
import com.nbys.activity.service.Rows;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminActivityController {
    private static final String DEFAULT_PLAN_BANNER = "/uploads/activity-plan-default.jpg";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final PlanOptionSynchronizer planOptionSynchronizer;

    public AdminActivityController(JdbcTemplate jdbc, AuthService auth, PlanOptionSynchronizer planOptionSynchronizer) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.planOptionSynchronizer = planOptionSynchronizer;
    }

    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String recordType,
                                                       @RequestParam(required = false) String startAt,
                                                       @RequestParam(required = false) String endAt) {
        String q = name == null ? "" : name.trim();
        String rt = recordType == null ? "" : recordType.trim();
        String st = status == null ? "" : status.trim();
        List<Map<String, Object>> activities = Rows.list(jdbc,
                "select a.*, " +
                        "coalesce(e.enroll_count,0) enroll_count,coalesce(c.checkin_count,0) checkin_count " +
                        "from activities a " +
                        "left join (select activity_id,coalesce(sum(1+coalesce(extra_count,0)),0) enroll_count from enrollments group by activity_id) e on e.activity_id=a.id " +
                        "left join (select ev.source_activity_id,count(*) checkin_count from attendance_events ev " +
                        "join attendance_records ar on ar.event_id=ev.id and ar.present=1 " +
                        "where ev.source_activity_id is not null group by ev.source_activity_id) c on c.source_activity_id=a.id " +
                        "where a.record_type='activity' " +
                        "and (?='' or a.name like concat('%',?,'%')) and (?='' or a.record_type=?) " +
                        "and (? is null or a.start_at>=?) and (? is null or a.end_at<=?) order by a.created_at desc,a.id desc",
                q, q, rt, rt, startAt, startAt, endAt, endAt);
        Map<String, Map<String, Object>> venues = venueLookup();
        Map<String, Integer> gameModeLimits = gameModeLimits();
        List<Map<String, Object>> filteredActivities = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : activities) {
            Map<String, Object> venue = resolveVenue(row, venues);
            applyDefaultVenueBanner(row, venue);
            enrichVenueFields(row, venue);
            row.put("display_status", displayStatus(row));
            row.put("display_record_type", "正式活动");
            row.put("signup_limit", signupLimit(row, gameModeLimits));
            if (st.isEmpty() || st.equals(String.valueOf(row.get("display_status")))) filteredActivities.add(row);
        }
        List<Map<String, Object>> plans = Rows.list(jdbc,
                "select p.*, 'plan' record_type, null start_at, p.vote_deadline end_at, null location, " +
                        "coalesce(v.voter_count,0) voter_count, coalesce(v.voter_count,0) enroll_count, 0 checkin_count " +
                        "from activity_plans p " +
                        "left join (select plan_id,count(distinct user_id) voter_count from plan_votes group by plan_id) v on v.plan_id=p.id " +
                        "where (?='' or p.name like concat('%',?,'%')) and (?='' or ?='plan') order by p.created_at desc,p.id desc",
                q, q, rt, rt);
        List<Map<String, Object>> filteredPlans = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : plans) {
            applyDefaultPlanBanner(row);
            row.put("display_status", row.get("converted_activity_id") == null ? "投票中" : "已生成活动");
            row.put("display_record_type", "活动策划");
            if (st.isEmpty() || st.equals(String.valueOf(row.get("display_status")))) filteredPlans.add(row);
        }
        if ("activity".equals(rt)) return ApiResponse.ok(filteredActivities);
        if ("plan".equals(rt)) return ApiResponse.ok(filteredPlans);
        filteredActivities.addAll(filteredPlans);
        filteredActivities.sort((a, b) -> compareCreatedDesc(a, b));
        return ApiResponse.ok(filteredActivities);
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable int id) {
        Map<String, Object> row = Rows.one(jdbc, "select * from activities where id=?", id);
        if (row == null) throw new IllegalArgumentException("活动不存在");
        applyDefaultVenueBanner(row);
        enrichVenueFields(row);
        row.put("display_status", displayStatus(row));
        row.put("signup_limit", signupLimit(row));
        row.put("enroll_count", enrolledCount(id));
        row.put("enrollments", Rows.list(jdbc,
                "select e.*, u.username, u.callsign, u.is_regular_member, coalesce(e.extra_count,0) extra_count, 1+coalesce(e.extra_count,0) participant_count from enrollments e join users u on u.id=e.user_id where e.activity_id=? order by u.is_regular_member desc,e.id", id));
        row.put("squads", Rows.list(jdbc, "select * from squad_settings where activity_id=? order by camp_no,squad_no", id));
        row.put("launcher_ids", launcherIds(id));
        return ApiResponse.ok(row);
    }

    @PostMapping("/activities")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "activity:create");
        validateActivityTime(body);
        Integer id = insertActivity(body, auth.currentUserId(req));
        createSquads(id, num(body.get("camp_count"), 2), num(body.get("squad_count"), 1));
        replaceLauncherOptions(id, body.get("launcher_ids"));
        markPlanConverted(body.get("source_plan_id"), id);
        return ApiResponse.ok(Collections.<String, Object>singletonMap("id", id));
    }

    @PutMapping("/activities/{id}")
    @Transactional
    public ApiResponse<Void> update(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "activity:update");
        validateActivityTime(body);
        Map<String, String> organizers = organizers(body.get("organizer_ids"), auth.currentUserId(req));
        Integer venueId = numOrNull(body.get("venue_id"));
        String location = activityLocation(body, venueId);
        String bannerUrl = activityBanner(body, venueId);
        String bannerSource = activityBannerSource(body);
        jdbc.update("update activities set name=?, banner_url=?, banner_source=?, activity_type=?, start_at=?, end_at=?, location=?, venue_id=?, checkin_methods=?, checkin_open_value=?, checkin_open_unit=?, open_min=?, camp_count=?, camp_limit=?, squad_count=?, squad_limit=?, allowed_jobs=?, game_modes=?, attendance_enabled=?, activity_region=?, visibility_type=?, invitee_ids=?,organizer_ids=? where id=?",
                body.get("name"), bannerUrl, bannerSource, body.get("activity_type"), body.get("start_at"), body.get("end_at"), location, venueId,
                checkinMethods(body.get("checkin_methods")), checkinOpenValue(body.get("checkin_open_value")), checkinOpenUnit(body.get("checkin_open_unit")),
                num(body.get("open_min"), 0), num(body.get("camp_count"), 2), num(body.get("camp_limit"), 0), num(body.get("squad_count"), 1), num(body.get("squad_limit"), 0),
                Rows.joinValue(body.get("allowed_jobs")), Rows.joinValue(body.get("game_modes")), attendanceEnabled(body), body.get("activity_region"), body.get("visibility_type"), Rows.joinValue(body.get("invitee_ids")), organizers.get("ids"), id);
        jdbc.update("update attendance_events set organizer=?,organizer_ids=? where source_activity_id=?", organizers.get("names"), organizers.get("ids"), id);
        replaceLauncherOptions(id, body.get("launcher_ids"));
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
    @Transactional
    public ApiResponse<Void> delete(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "activity:delete");
        jdbc.update("update activity_plans set converted_activity_id=null,hidden=0 where converted_activity_id=?", id);
        jdbc.update("delete from attendance_records where event_id in (select id from attendance_events where source_activity_id=?)", id);
        jdbc.update("delete from attendance_events where source_activity_id=?", id);
        jdbc.update("delete from extraction_run_records where activity_id=?", id);
        jdbc.update("delete from activity_launcher_options where activity_id=?", id);
        jdbc.update("delete n from user_notifications n join activity_launcher_rentals r on r.id=n.related_id where n.type='launcher_rental' and r.activity_id=?", id);
        jdbc.update("delete from activity_launcher_rentals where activity_id=?", id);
        jdbc.update("delete from enrollments where activity_id=?", id);
        jdbc.update("delete from squad_settings where activity_id=?", id);
        jdbc.update("delete from camp_settings where activity_id=?", id);
        jdbc.update("delete from activities where id=?", id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/launcher-rentals/options")
    public ApiResponse<List<Map<String, Object>>> launcherOptions(@RequestParam(required = false) String name,
                                                                  @RequestParam(required = false) String callsign,
                                                                  HttpServletRequest req) {
        auth.require(req, "activity:view");
        String n = text(name);
        String c = text(callsign);
        return ApiResponse.ok(Rows.list(jdbc,
                "select l.id,l.name,l.photo_filename,l.rent_fee,l.created_by_id,u.username owner_name,u.callsign owner_callsign " +
                        "from launcher_rental_items l join users u on u.id=l.created_by_id " +
                        "where l.active=1 and (?='' or l.name like concat('%',?,'%')) and (?='' or u.callsign like concat('%',?,'%')) " +
                        "order by l.id desc",
                n, n, c, c));
    }

    @GetMapping("/activities/{id}/enrollments/export")
    public void exportEnrollments(@PathVariable int id, HttpServletRequest req, HttpServletResponse response) throws IOException {
        auth.require(req, "activity:view");
        Map<String, Object> activity = Rows.one(jdbc, "select name from activities where id=?", id);
        if (activity == null) throw new IllegalArgumentException("活动不存在");
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select u.id user_id,u.username,coalesce(u.callsign,'') callsign," +
                        "coalesce(nullif(trim(u.callsign),''),u.username) display_name," +
                        "1+coalesce(e.extra_count,0) participant_count " +
                        "from enrollments e join users u on u.id=e.user_id where e.activity_id=? " +
                        "order by u.is_regular_member desc,e.id",
                id);
        String activityName = text(activity.get("name"));
        writeWorkbook(response, buildEnrollmentWorkbook(activityName, rows), enrollmentExportFilename(activityName));
    }

    Workbook buildEnrollmentWorkbook(String activityName, List<Map<String, Object>> rows) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("报名表");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 6);

        CellStyle titleStyle = solidStyle(workbook, IndexedColors.DARK_BLUE, IndexedColors.WHITE, true, 16);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle labelStyle = solidStyle(workbook, IndexedColors.PALE_BLUE, IndexedColors.DARK_BLUE, true, 11);
        labelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle headerStyle = solidStyle(workbook, IndexedColors.ROYAL_BLUE, IndexedColors.WHITE, true, 11);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(headerStyle, BorderStyle.THIN, IndexedColors.DARK_BLUE.getIndex());
        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        bodyStyle.setBorderBottom(BorderStyle.THIN);
        bodyStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        CellStyle bandedStyle = workbook.createCellStyle();
        bandedStyle.cloneStyleFrom(bodyStyle);
        bandedStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        bandedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle centeredBodyStyle = workbook.createCellStyle();
        centeredBodyStyle.cloneStyleFrom(bodyStyle);
        centeredBodyStyle.setAlignment(HorizontalAlignment.CENTER);
        CellStyle centeredBandedStyle = workbook.createCellStyle();
        centeredBandedStyle.cloneStyleFrom(bandedStyle);
        centeredBandedStyle.setAlignment(HorizontalAlignment.CENTER);
        CellStyle numericBodyStyle = workbook.createCellStyle();
        numericBodyStyle.cloneStyleFrom(bodyStyle);
        numericBodyStyle.setAlignment(HorizontalAlignment.RIGHT);
        CellStyle numericBandedStyle = workbook.createCellStyle();
        numericBandedStyle.cloneStyleFrom(bandedStyle);
        numericBandedStyle.setAlignment(HorizontalAlignment.RIGHT);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(34);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue(activityName + "｜报名表");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        writeMergedMetadata(sheet, 1, "活动名称", activityName, labelStyle);
        writeMergedMetadata(sheet, 2, "显示规则", "呼号不为空时使用呼号；呼号为空时使用用户名", labelStyle);

        int participantTotal = 0;
        for (Map<String, Object> item : rows) participantTotal += num(item.get("participant_count"), 1);
        Row summary = sheet.createRow(3);
        summary.setHeightInPoints(24);
        writeSummaryCell(summary, 0, "报名账号", labelStyle);
        writeSummaryCell(summary, 1, rows.size(), null);
        writeSummaryCell(summary, 2, "报名人数", labelStyle);
        writeSummaryCell(summary, 3, participantTotal, null);
        writeSummaryCell(summary, 4, "导出时间", labelStyle);
        writeSummaryCell(summary, 5, LocalDateTime.now(BUSINESS_ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), null);

        Row header = sheet.createRow(5);
        header.setHeightInPoints(26);
        String[] headers = {"序号", "用户ID", "用户名", "呼号", "显示名称", "报名人数"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> item = rows.get(index);
            Row row = sheet.createRow(index + 6);
            row.setHeightInPoints(22);
            boolean banded = index % 2 == 0;
            writeNumericCell(row, 0, index + 1, banded ? centeredBandedStyle : centeredBodyStyle);
            writeNumericCell(row, 1, num(item.get("user_id"), 0), banded ? centeredBandedStyle : centeredBodyStyle);
            writeTextCell(row, 2, text(item.get("username")), banded ? bandedStyle : bodyStyle);
            writeTextCell(row, 3, text(item.get("callsign")), banded ? bandedStyle : bodyStyle);
            writeTextCell(row, 4, text(item.get("display_name")), banded ? bandedStyle : bodyStyle);
            writeNumericCell(row, 5, num(item.get("participant_count"), 1), banded ? numericBandedStyle : numericBodyStyle);
        }

        int[] widths = {9, 11, 20, 20, 20, 12};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheet.setAutoFilter(new CellRangeAddress(5, Math.max(5, rows.size() + 5), 0, 5));
        return workbook;
    }

    String enrollmentExportFilename(String activityName) {
        String safe = text(activityName).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        safe = safe.replaceAll("[. ]+$", "").trim();
        if (safe.isEmpty()) safe = "活动报名表";
        if (safe.length() > 120) safe = safe.substring(0, 120).trim();
        return safe + ".xlsx";
    }

    private CellStyle solidStyle(Workbook workbook, IndexedColors fill, IndexedColors fontColor, boolean bold, int fontSize) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(fill.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(bold);
        font.setColor(fontColor.getIndex());
        font.setFontHeightInPoints((short) fontSize);
        style.setFont(font);
        return style;
    }

    private void applyBorders(CellStyle style, BorderStyle borderStyle, short color) {
        style.setBorderTop(borderStyle);
        style.setBorderBottom(borderStyle);
        style.setBorderLeft(borderStyle);
        style.setBorderRight(borderStyle);
        style.setTopBorderColor(color);
        style.setBottomBorderColor(color);
        style.setLeftBorderColor(color);
        style.setRightBorderColor(color);
    }

    private void writeMergedMetadata(Sheet sheet, int rowIndex, String label, String value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(24);
        writeTextCell(row, 0, label, labelStyle);
        writeTextCell(row, 1, value, null);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 1, 5));
    }

    private void writeSummaryCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        else cell.setCellValue(text(value));
        if (style != null) cell.setCellStyle(style);
    }

    private void writeTextCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private void writeNumericCell(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    @GetMapping("/activities/{id}/launcher-rentals/export")
    public void exportLauncherRentals(@PathVariable int id, HttpServletRequest req, HttpServletResponse response) throws IOException {
        auth.require(req, "activity:view");
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select renter.username renter_name, owner.username owner_name, l.name launcher_name " +
                        "from activity_launcher_rentals r join users renter on renter.id=r.user_id " +
                        "join launcher_rental_items l on l.id=r.launcher_id join users owner on owner.id=l.created_by_id " +
                        "where r.activity_id=? and r.cancelled_at is null and r.status='confirmed' order by r.id",
                id);
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("租赁表");
        writeHeader(sheet, "借用人的序号", "名字", "被借人名字", "发射器名字");
        int i = 1;
        for (Map<String, Object> item : rows) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(i);
            row.createCell(1).setCellValue(text(item.get("renter_name")));
            row.createCell(2).setCellValue(text(item.get("owner_name")));
            row.createCell(3).setCellValue(text(item.get("launcher_name")));
            i++;
        }
        writeWorkbook(response, workbook, "发射器租赁表.xlsx");
    }

    @PostMapping("/activity-plans")
    public ApiResponse<Map<String, Object>> createPlan(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "plan:create");
        Map<String, String> organizers = organizers(body.get("organizer_ids"), auth.currentUserId(req));
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("insert into activity_plans(name,banner_url,vote_deadline,hidden,visibility_type,invitee_ids,created_by_id,organizer_ids,created_at) values(?,?,?,0,?,?,?,?,now())", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, body.get("name"));
            ps.setObject(2, planBanner(body.get("banner_url")));
            ps.setObject(3, body.get("vote_deadline"));
            ps.setObject(4, body.get("visibility_type"));
            ps.setObject(5, Rows.joinValue(body.get("invitee_ids")));
            ps.setObject(6, auth.currentUserId(req));
            ps.setObject(7, organizers.get("ids"));
            return ps;
        }, kh);
        int id = kh.getKey().intValue();
        planOptionSynchronizer.sync(id, body);
        return ApiResponse.ok(Collections.<String, Object>singletonMap("id", id));
    }

    @GetMapping("/activity-plans/{id}")
    public ApiResponse<Map<String, Object>> planDetail(@PathVariable int id) {
        Map<String, Object> row = Rows.one(jdbc, "select * from activity_plans where id=?", id);
        if (row == null) throw new IllegalArgumentException("策划不存在");
        applyDefaultPlanBanner(row);
        row.put("dates", Rows.list(jdbc, "select * from plan_date_options where plan_id=? order by date", id));
        row.put("venues", Rows.list(jdbc, "select v.* from plan_venue_options o join venues v on v.id=o.venue_id where o.plan_id=?", id));
        row.put("game_modes", Rows.list(jdbc, "select g.* from plan_game_mode_options o join game_modes g on g.id=o.game_mode_id where o.plan_id=?", id));
        return ApiResponse.ok(row);
    }

    @GetMapping("/activity-plans/{id}/votes")
    public ApiResponse<Map<String, Object>> planVotes(@PathVariable int id, HttpServletRequest req) {
        Map<String, Object> me = auth.currentAdmin(req);
        Map<String, Object> plan = Rows.one(jdbc, "select * from activity_plans where id=?", id);
        if (plan == null) throw new IllegalArgumentException("策划不存在");
        if (!canViewPlanVotes(plan, me)) throw new SecurityException("没有查看投票详情权限");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("plan", plan);
        out.put("total_voters", Rows.one(jdbc, "select count(distinct user_id) total from plan_votes where plan_id=?", id).get("total"));
        out.put("sections", Arrays.asList(
                voteSection(id, "date", "日期投票", planDateVoteOptions(id)),
                voteSection(id, "venue", "场地投票", planVenueVoteOptions(id)),
                voteSection(id, "game_mode", "模式投票", planModeVoteOptions(id))
        ));
        out.put("voters", Rows.list(jdbc,
                "select u.id,u.username,u.callsign,u.avatar_url,u.is_regular_member,max(v.created_at) voted_at " +
                        "from plan_votes v join users u on u.id=v.user_id where v.plan_id=? " +
                        "group by u.id,u.username,u.callsign,u.avatar_url,u.is_regular_member order by u.is_regular_member desc,voted_at desc,u.id desc",
                id));
        return ApiResponse.ok(out);
    }

    @GetMapping("/activity-plans/{id}/convert-preview")
    public ApiResponse<Map<String, Object>> convertPreview(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "plan:update");
        auth.require(req, "activity:create");
        Map<String, Object> plan = Rows.one(jdbc, "select * from activity_plans where id=?", id);
        if (plan == null) throw new IllegalArgumentException("策划不存在");
        if (plan.get("converted_activity_id") != null) throw new IllegalArgumentException("该策划已生成活动");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("plan", plan);
        List<Map<String, Object>> dates = planDateVotes(id);
        List<Map<String, Object>> venues = planVenueVotes(id);
        List<Map<String, Object>> modes = topPlanModes(id);
        out.put("dates", dates);
        out.put("venues", venues);
        out.put("game_modes", modes);
        out.put("top_dates", topTiedOptions(dates));
        out.put("top_venues", topTiedOptions(venues));
        out.put("date_tied", topTiedOptions(dates).size() > 1);
        out.put("venue_tied", topTiedOptions(venues).size() > 1);
        return ApiResponse.ok(out);
    }

    @PutMapping("/activity-plans/{id}")
    @Transactional
    public ApiResponse<Void> updatePlan(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "plan:update");
        Map<String, String> organizers = organizers(body.get("organizer_ids"), auth.currentUserId(req));
        jdbc.update("update activity_plans set name=?, banner_url=?, vote_deadline=?, visibility_type=?, invitee_ids=?,organizer_ids=? where id=?",
                body.get("name"), planBanner(body.get("banner_url")), body.get("vote_deadline"), body.get("visibility_type"), Rows.joinValue(body.get("invitee_ids")), organizers.get("ids"), id);
        planOptionSynchronizer.sync(id, body);
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
        throw new IllegalArgumentException("请使用转活动预览并在活动新增页保存");
    }

    private Integer insertActivity(Map<String, Object> body, Integer userId) {
        KeyHolder kh = new GeneratedKeyHolder();
        Integer venueId = numOrNull(body.get("venue_id"));
        String location = activityLocation(body, venueId);
        String bannerUrl = activityBanner(body, venueId);
        String bannerSource = activityBannerSource(body);
        Map<String, String> organizers = organizers(body.get("organizer_ids"), userId);
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("insert into activities(record_type,name,banner_url,banner_source,activity_type,start_at,end_at,location,venue_id,checkin_methods,checkin_open_value,checkin_open_unit,open_min,camp_count,camp_limit,squad_count,squad_limit,allowed_jobs,game_modes,attendance_enabled,activity_region,visibility_type,invitee_ids,list_locked,created_by_id,organizer_ids,created_at) values('activity',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,now())", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, body.get("name"));
            ps.setObject(2, bannerUrl);
            ps.setObject(3, bannerSource);
            ps.setObject(4, body.get("activity_type"));
            ps.setObject(5, body.get("start_at"));
            ps.setObject(6, body.get("end_at"));
            ps.setObject(7, location);
            ps.setObject(8, venueId);
            ps.setObject(9, checkinMethods(body.get("checkin_methods")));
            ps.setObject(10, checkinOpenValue(body.get("checkin_open_value")));
            ps.setObject(11, checkinOpenUnit(body.get("checkin_open_unit")));
            ps.setObject(12, num(body.get("open_min"), 0));
            ps.setObject(13, num(body.get("camp_count"), 2));
            ps.setObject(14, num(body.get("camp_limit"), 0));
            ps.setObject(15, num(body.get("squad_count"), 1));
            ps.setObject(16, num(body.get("squad_limit"), 0));
            ps.setObject(17, Rows.joinValue(body.get("allowed_jobs")));
            ps.setObject(18, Rows.joinValue(body.get("game_modes")));
            ps.setObject(19, attendanceEnabled(body));
            ps.setObject(20, body.get("activity_region"));
            ps.setObject(21, body.get("visibility_type"));
            ps.setObject(22, Rows.joinValue(body.get("invitee_ids")));
            ps.setObject(23, userId);
            ps.setObject(24, organizers.get("ids"));
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    private void validateActivityTime(Map<String, Object> body) {
        String startValue = text(body.get("start_at"));
        String endValue = text(body.get("end_at"));
        if (startValue.isEmpty() || endValue.isEmpty()) {
            throw new IllegalArgumentException("请选择活动开始时间和结束时间");
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startValue.replace(' ', 'T'));
            LocalDateTime end = LocalDateTime.parse(endValue.replace(' ', 'T'));
            if (!end.isAfter(start)) throw new IllegalArgumentException("活动结束时间必须晚于开始时间");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("活动时间格式不正确");
        }
    }

    private int checkinOpenValue(Object value) {
        if (value == null) return 3;
        if (value instanceof Number) return Math.max(0, ((Number) value).intValue());
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
        } catch (Exception e) {
            return 3;
        }
    }

    private String checkinOpenUnit(Object value) {
        String unit = text(value);
        return "day".equals(unit) ? "day" : "hour";
    }

    private int attendanceEnabled(Map<String, Object> activity) {
        return "接龙".equals(text(activity.get("activity_type"))) ? 0 : 1;
    }

    private String checkinMethods(Object value) {
        LinkedHashSet<String> methods = new LinkedHashSet<String>(Rows.csv(Rows.joinValue(value)));
        methods.removeIf(method -> !"location".equals(method) && !"qr".equals(method));
        if (methods.isEmpty()) methods.addAll(Arrays.asList("location", "qr"));
        return Rows.join(methods);
    }

    private Map<String, String> organizers(Object value, Integer defaultUserId) {
        LinkedHashSet<String> requested = new LinkedHashSet<String>(Rows.csv(Rows.joinValue(value)));
        if (requested.isEmpty() && defaultUserId != null) requested.add(String.valueOf(defaultUserId));
        String ids = Rows.join(requested);
        List<Map<String, Object>> users = Rows.list(jdbc,
                "select id,coalesce(nullif(callsign,''),username) name from users " +
                        "where disabled=0 and is_regular_member=1 and find_in_set(cast(id as char),?)>0 order by callsign,id", ids);
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

    private boolean canViewPlanVotes(Map<String, Object> plan, Map<String, Object> user) {
        String role = text(user.get("role"));
        if ("superadmin".equals(role) || "admin".equals(role) || "activity_admin".equals(role)) return true;
        String userId = String.valueOf(user.get("id"));
        if (userId.equals(String.valueOf(plan.get("created_by_id")))) return true;
        return Rows.csv(text(plan.get("organizer_ids"))).contains(userId);
    }

    private Map<String, Object> voteSection(int planId, String type, String title, List<Map<String, Object>> options) {
        Map<Integer, List<Map<String, Object>>> voters = new HashMap<Integer, List<Map<String, Object>>>();
        for (Map<String, Object> voter : Rows.list(jdbc,
                "select v.option_id,u.id,u.username,u.callsign,u.avatar_url,u.is_regular_member,v.created_at voted_at " +
                        "from plan_votes v join users u on u.id=v.user_id where v.plan_id=? and v.option_type=? " +
                        "order by u.is_regular_member desc,v.created_at desc,u.id desc",
                planId, type)) {
            voters.computeIfAbsent(num(voter.get("option_id"), 0), key -> new ArrayList<Map<String, Object>>()).add(voter);
        }
        for (Map<String, Object> option : options) {
            int optionId = num(option.get("id"), 0);
            List<Map<String, Object>> optionVoters = voters.getOrDefault(optionId, Collections.emptyList());
            int formalCount = 0;
            for (Map<String, Object> voter : optionVoters) if (bool(voter.get("is_regular_member"))) formalCount++;
            option.put("vote_count", optionVoters.size());
            option.put("formal_vote_count", formalCount);
            option.put("voters", optionVoters);
        }
        Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("type", type);
        section.put("title", title);
        section.put("options", options);
        return section;
    }

    private List<Map<String, Object>> planDateVoteOptions(int planId) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select id,date label,remark subtitle from plan_date_options where plan_id=? order by date,id", planId);
        for (Map<String, Object> row : rows) row.put("subtitle", text(row.get("subtitle")));
        return rows;
    }

    private List<Map<String, Object>> planVenueVoteOptions(int planId) {
        return Rows.list(jdbc,
                "select v.id,v.name label,v.address subtitle from plan_venue_options o join venues v on v.id=o.venue_id where o.plan_id=? order by o.id",
                planId);
    }

    private List<Map<String, Object>> planModeVoteOptions(int planId) {
        return Rows.list(jdbc,
                "select g.id,g.name label,case when g.suitable_people is null then '' else concat(g.suitable_people,'人') end subtitle " +
                        "from plan_game_mode_options o join game_modes g on g.id=o.game_mode_id where o.plan_id=? order by o.id",
                planId);
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

    private List<Map<String, Object>> planDateVotes(int planId) {
        return Rows.list(jdbc,
                "select d.*, coalesce(v.votes,0) vote_count from plan_date_options d " +
                        "left join (select option_id,count(*) votes from plan_votes where plan_id=? and option_type='date' group by option_id) v on v.option_id=d.id " +
                        "where d.plan_id=? order by coalesce(v.votes,0) desc,d.date,d.id",
                planId, planId);
    }

    private Map<String, Object> topPlanVenue(int planId) {
        return Rows.one(jdbc,
                "select v.* from plan_venue_options o join venues v on v.id=o.venue_id " +
                        "left join (select option_id,count(*) votes from plan_votes where plan_id=? and option_type='venue' group by option_id) pv on pv.option_id=v.id " +
                        "where o.plan_id=? order by coalesce(pv.votes,0) desc,o.id limit 1",
                planId, planId);
    }

    private List<Map<String, Object>> planVenueVotes(int planId) {
        return Rows.list(jdbc,
                "select v.*, coalesce(pv.votes,0) vote_count from plan_venue_options o join venues v on v.id=o.venue_id " +
                        "left join (select option_id,count(*) votes from plan_votes where plan_id=? and option_type='venue' group by option_id) pv on pv.option_id=v.id " +
                        "where o.plan_id=? order by coalesce(pv.votes,0) desc,o.id",
                planId, planId);
    }

    private List<Map<String, Object>> topTiedOptions(List<Map<String, Object>> rows) {
        List<Map<String, Object>> top = new ArrayList<Map<String, Object>>();
        if (rows == null || rows.isEmpty()) return top;
        int max = num(rows.get(0).get("vote_count"), 0);
        for (Map<String, Object> row : rows) {
            if (num(row.get("vote_count"), 0) == max) top.add(row);
            else break;
        }
        return top;
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

    private List<Integer> launcherIds(int activityId) {
        List<Integer> ids = new ArrayList<Integer>();
        for (Map<String, Object> row : Rows.list(jdbc, "select launcher_id from activity_launcher_options where activity_id=? order by id", activityId)) {
            ids.add(((Number) row.get("launcher_id")).intValue());
        }
        return ids;
    }

    private void replaceLauncherOptions(int activityId, Object value) {
        jdbc.update("delete from activity_launcher_options where activity_id=?", activityId);
        Set<Integer> seen = new LinkedHashSet<Integer>();
        for (Object item : list(value)) {
            Integer id = numOrNull(item);
            if (id != null) seen.add(id);
        }
        for (Integer launcherId : seen) {
            jdbc.update("insert ignore into activity_launcher_options(activity_id,launcher_id,created_at) values(?,?,now())", activityId, launcherId);
        }
    }

    private void createSquads(int activityId, int camps, int squads) {
        for (int c = 1; c <= camps; c++) {
            jdbc.update("insert into camp_settings(activity_id,camp_no,updated_at) values(?,?,now())", activityId, c);
            for (int s = 1; s <= squads; s++) {
                jdbc.update("insert into squad_settings(activity_id,camp_no,squad_no,name,radio_channel,locked,updated_at) values(?,?,?,?,?,0,now())",
                        activityId, c, s, (char) ('A' + s - 1) + "队", radioChannel(c, s));
            }
        }
    }

    private String radioChannel(int campNo, int squadNo) {
        return (438 + campNo) + "." + String.format("%03d", squadNo * 100);
    }

    private String displayStatus(Map<String, Object> row) {
        if (row.get("deleted_at") != null) return "活动取消";
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime start = LocalDateTime.parse(String.valueOf(row.get("start_at")).replace(' ', 'T'));
        LocalDateTime end = LocalDateTime.parse(String.valueOf(row.get("end_at")).replace(' ', 'T'));
        if (!now.isBefore(end)) return "活动结束";
        if (!now.isBefore(start)) return "活动进行中";
        return "报名中";
    }

    private int signupLimit(Map<String, Object> row) {
        return signupLimit(row, gameModeLimits());
    }

    private int signupLimit(Map<String, Object> row, Map<String, Integer> modeLimits) {
        return ActivityLimitCalculator.signupLimit(row, modeLimits);
    }

    private int enrolledCount(int activityId) {
        Integer count = jdbc.queryForObject(
                "select coalesce(sum(1+coalesce(extra_count,0)),0) from enrollments where activity_id=?",
                Integer.class,
                activityId);
        return count == null ? 0 : count;
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

    private void applyDefaultVenueBanner(Map<String, Object> row, Map<String, Object> venue) {
        if ("custom".equals(text(row.get("banner_source"))) && !text(row.get("banner_url")).isEmpty()) return;
        if (venue != null && !text(venue.get("image_url")).isEmpty()) row.put("banner_url", venue.get("image_url"));
    }

    private void enrichVenueFields(Map<String, Object> row) {
        Map<String, Object> venue = null;
        if (row.get("venue_id") != null) {
            venue = Rows.one(jdbc, "select name,address from venues where id=?", row.get("venue_id"));
        }
        if (venue == null) {
            String location = text(row.get("location"));
            if (!location.isEmpty()) venue = Rows.one(jdbc, "select name,address from venues where name=? or address=? limit 1", location, location);
        }
        if (venue != null) {
            row.put("venue_name", venue.get("name"));
            row.put("venue_address", venue.get("address"));
        } else {
            row.put("venue_name", row.get("location"));
            row.put("venue_address", row.get("location"));
        }
    }

    private void enrichVenueFields(Map<String, Object> row, Map<String, Object> venue) {
        if (venue != null) {
            row.put("venue_name", venue.get("name"));
            row.put("venue_address", venue.get("address"));
        } else {
            row.put("venue_name", row.get("location"));
            row.put("venue_address", row.get("location"));
        }
    }

    private Map<String, Map<String, Object>> venueLookup() {
        Map<String, Map<String, Object>> lookup = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> venue : Rows.list(jdbc, "select id,name,address,image_url from venues")) {
            lookup.put("id:" + venue.get("id"), venue);
            if (!text(venue.get("name")).isEmpty()) lookup.put("location:" + text(venue.get("name")), venue);
            if (!text(venue.get("address")).isEmpty()) lookup.put("location:" + text(venue.get("address")), venue);
        }
        return lookup;
    }

    private Map<String, Object> resolveVenue(Map<String, Object> row, Map<String, Map<String, Object>> venues) {
        if (row.get("venue_id") != null) {
            Map<String, Object> venue = venues.get("id:" + row.get("venue_id"));
            if (venue != null) return venue;
        }
        return venues.get("location:" + text(row.get("location")));
    }

    private Map<String, Integer> gameModeLimits() {
        Map<String, Integer> limits = new HashMap<String, Integer>();
        for (Map<String, Object> mode : Rows.list(jdbc, "select name,suitable_people from game_modes")) {
            limits.put(text(mode.get("name")), num(mode.get("suitable_people"), 0));
        }
        return limits;
    }

    private void markPlanConverted(Object sourcePlanId, Integer activityId) {
        Integer planId = numOrNull(sourcePlanId);
        if (planId == null) return;
        Map<String, Object> plan = Rows.one(jdbc, "select converted_activity_id from activity_plans where id=?", planId);
        if (plan == null) throw new IllegalArgumentException("策划不存在");
        if (plan.get("converted_activity_id") != null) throw new IllegalArgumentException("该策划已生成活动");
        jdbc.update("update activity_plans set converted_activity_id=?, hidden=1 where id=?", activityId, planId);
    }

    private void writeHeader(Sheet sheet, String... values) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private void writeWorkbook(HttpServletResponse response, Workbook workbook, String filename) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, "UTF-8").replace("+", "%20"));
        try {
            workbook.write(response.getOutputStream());
        } finally {
            workbook.close();
        }
    }

    private String planBanner(Object banner) {
        String value = text(banner);
        return value.isEmpty() ? DEFAULT_PLAN_BANNER : value;
    }

    private void applyDefaultPlanBanner(Map<String, Object> row) {
        if (text(row.get("banner_url")).isEmpty()) row.put("banner_url", DEFAULT_PLAN_BANNER);
    }

    private int compareCreatedDesc(Map<String, Object> a, Map<String, Object> b) {
        String ca = text(a.get("created_at"));
        String cb = text(b.get("created_at"));
        int byCreated = cb.compareTo(ca);
        if (byCreated != 0) return byCreated;
        return num(b.get("id"), 0) - num(a.get("id"), 0);
    }

    private String dateValue(Object value) {
        if (value instanceof Map) return text(((Map<?, ?>) value).get("date"));
        return text(value);
    }

    private String dateRemark(Object value) {
        if (value instanceof Map) return text(((Map<?, ?>) value).get("remark"));
        return "";
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

    private boolean bool(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
