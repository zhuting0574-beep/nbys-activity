package com.nbys.trainingcenter;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/training/admin")
public class TrainingAdminController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public TrainingAdminController(JdbcTemplate jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }

    @GetMapping("/overview")
    public ApiResponse<Map<String,Object>> overview(HttpServletRequest request) {
        auth.require(request, "training:view");
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("open_rooms", scalar("select count(*) from training_rooms where status='open'"));
        out.put("running_sessions", scalar("select count(*) from training_sessions where status='running'"));
        out.put("online_devices", scalar("select count(*) from training_devices where status='online'"));
        out.put("today_completed", scalar("select count(*) from training_sessions where status='completed' and date(finished_at)=curdate()"));
        return ApiResponse.ok(out);
    }

    @GetMapping("/rooms")
    public ApiResponse<List<Map<String,Object>>> rooms(HttpServletRequest request) {
        auth.require(request, "training:view");
        return ApiResponse.ok(Rows.list(jdbc, "select r.*,u.username owner_username,u.callsign owner_callsign,(select count(*) from training_room_members m where m.room_id=r.id) member_count,(select count(*) from training_sessions s where s.room_id=r.id and s.status='running') running_count from training_rooms r join users u on u.id=r.owner_user_id order by r.created_at desc"));
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String,Object>>> devices(HttpServletRequest request) {
        auth.require(request, "training:view");
        return ApiResponse.ok(Rows.list(jdbc, "select d.*,r.room_code,r.name room_name from training_devices d left join training_rooms r on r.id=d.room_id order by d.updated_at desc"));
    }

    @PostMapping("/devices")
    @Transactional
    public ApiResponse<Map<String,Object>> createDevice(@RequestBody Map<String,Object> body, HttpServletRequest request) {
        int actor = actor(request, "training:update");
        String code = value(body.get("device_code"), "NODE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        String name = value(body.get("name"), code);
        jdbc.update("insert into training_devices(device_code,name,target_name,status,calibration_status) values(?,?,?,?,?)", code, name, value(body.get("target_name"), ""), value(body.get("status"), "offline"), value(body.get("calibration_status"), "pending"));
        Map<String,Object> device = Rows.one(jdbc, "select * from training_devices where device_code=?", code);
        audit(actor, "device", ((Number) device.get("id")).longValue(), "create", null, null, device);
        return ApiResponse.ok(device);
    }

    @PutMapping("/devices/{id}")
    @Transactional
    public ApiResponse<Map<String,Object>> updateDevice(@PathVariable long id, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        int actor = actor(request, "training:update");
        Map<String,Object> before = required("training_devices", id);
        jdbc.update("update training_devices set name=?,target_name=?,status=?,calibration_status=?,room_id=?,target_no=?,frame_rate=? where id=?", value(body.get("name"), String.valueOf(before.get("name"))), value(body.get("target_name"), String.valueOf(before.get("target_name"))), value(body.get("status"), String.valueOf(before.get("status"))), value(body.get("calibration_status"), String.valueOf(before.get("calibration_status"))), body.get("room_id"), body.get("target_no"), body.get("frame_rate"), id);
        Map<String,Object> after = required("training_devices", id);
        audit(actor, "device", id, "update", value(body.get("reason"), null), before, after);
        return ApiResponse.ok(after);
    }

    @PutMapping("/rooms/{id}/close")
    @Transactional
    public ApiResponse<Void> closeRoom(@PathVariable long id, @RequestBody(required=false) Map<String,Object> body, HttpServletRequest request) {
        int actor = actor(request, "training:update"); Map<String,Object> before = required("training_rooms", id);
        jdbc.update("update training_rooms set status='closed' where id=?", id);
        jdbc.update("update training_sessions set status='cancelled',finished_at=now() where room_id=? and status='running'", id);
        audit(actor, "room", id, "force_close", body == null ? null : value(body.get("reason"), null), before, required("training_rooms", id));
        return ApiResponse.ok(null);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String,Object>>> sessions(HttpServletRequest request) {
        auth.require(request, "training:view");
        return ApiResponse.ok(Rows.list(jdbc, "select s.*,r.room_code,r.name room_name,u.username,u.callsign from training_sessions s join training_rooms r on r.id=s.room_id join users u on u.id=s.user_id where s.deleted_at is null order by s.created_at desc limit 300"));
    }

    @PutMapping("/sessions/{id}")
    @Transactional
    public ApiResponse<Map<String,Object>> updateSession(@PathVariable long id, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        int actor = actor(request, "training:update"); Map<String,Object> before = required("training_sessions", id);
        jdbc.update("update training_sessions set total_hits=?,average_accuracy=?,duration_ms=? where id=?", body.get("total_hits"), body.get("average_accuracy"), body.get("duration_ms"), id);
        Map<String,Object> after = required("training_sessions", id); audit(actor, "session", id, "update", value(body.get("reason"), null), before, after); return ApiResponse.ok(after);
    }

    @DeleteMapping("/sessions/{id}")
    @Transactional
    public ApiResponse<Void> deleteSession(@PathVariable long id, @RequestBody(required=false) Map<String,Object> body, HttpServletRequest request) {
        int actor = actor(request, "training:delete"); Map<String,Object> before = required("training_sessions", id);
        jdbc.update("update training_sessions set deleted_at=now() where id=?", id); audit(actor, "session", id, "delete", body == null ? null : value(body.get("reason"), null), before, null); return ApiResponse.ok(null);
    }

    @GetMapping("/audits")
    public ApiResponse<List<Map<String,Object>>> audits(HttpServletRequest request) { auth.require(request, "training:view"); return ApiResponse.ok(Rows.list(jdbc, "select a.*,u.username,u.callsign from training_audits a left join users u on u.id=a.actor_user_id order by a.id desc limit 300")); }
    private int actor(HttpServletRequest request, String permission) { auth.require(request, permission); return ((Number) auth.current(request).get("id")).intValue(); }
    private Number scalar(String sql) { return jdbc.queryForObject(sql, Number.class); }
    private Map<String,Object> required(String table, long id) { Map<String,Object> row = Rows.one(jdbc, "select * from " + table + " where id=?", id); if (row == null) throw new IllegalArgumentException("记录不存在"); return row; }
    private void audit(int actor, String type, long id, String action, String reason, Map<String,Object> before, Map<String,Object> after) { jdbc.update("insert into training_audits(actor_user_id,entity_type,entity_id,action,reason,before_json,after_json) values(?,?,?,?,?,?,?)", actor,type,id,action,reason,String.valueOf(before),String.valueOf(after)); }
    private String value(Object value, String fallback) { String result = value == null ? "" : String.valueOf(value).trim(); return result.isEmpty() ? fallback : result; }
}
