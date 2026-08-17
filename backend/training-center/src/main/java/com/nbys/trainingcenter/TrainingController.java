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
@RequestMapping("/api/training/h5")
public class TrainingController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    public TrainingController(JdbcTemplate jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String,Object>> bootstrap(HttpServletRequest request) {
        Map<String,Object> me = regularUser(request);
        int userId = ((Number) me.get("id")).intValue();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("history", Rows.list(jdbc, "select s.*,r.room_code,r.name room_name from training_sessions s join training_rooms r on r.id=s.room_id where s.user_id=? and s.deleted_at is null order by s.created_at desc limit 50", userId));
        out.put("rooms", openRooms(userId));
        Map<String,Object> current = Rows.one(jdbc, "select r.id,r.name,r.room_type from training_room_members m join training_rooms r on r.id=m.room_id where m.user_id=? and r.status='open' order by r.id desc limit 1", userId);
        out.put("current_room_id", current == null ? null : current.get("id"));
        out.put("settings", Map.of("target_count", 1, "target_hits", 5, "min_target_count", 1, "max_target_count", 3, "beep_min_delay", 2.0, "beep_max_delay", 4.0));
        return ApiResponse.ok(out);
    }

    @GetMapping("/rooms")
    public ApiResponse<List<Map<String,Object>>> rooms(HttpServletRequest request) { return ApiResponse.ok(openRooms(((Number) regularUser(request).get("id")).intValue())); }

    @GetMapping("/rooms/{id}")
    public ApiResponse<Map<String,Object>> room(@PathVariable long id, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        return ApiResponse.ok(roomView(id, userId));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String,Object>> session(@PathVariable long id, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> session = Rows.one(jdbc, "select s.*,r.room_code,r.name room_name from training_sessions s join training_rooms r on r.id=s.room_id where s.id=? and s.user_id=? and s.deleted_at is null", id, userId);
        if (session == null) throw new IllegalArgumentException("训练成绩不存在");
        session.put("hits", Rows.list(jdbc, "select * from training_hits where session_id=? order by target_no,shot_no,id", id));
        return ApiResponse.ok(session);
    }

    @PostMapping("/sessions/{id}/hits")
    @Transactional
    public ApiResponse<Map<String,Object>> hit(@PathVariable long id, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> session = Rows.one(jdbc, "select * from training_sessions where id=? and user_id=?", id, userId);
        if (session == null) throw new IllegalArgumentException("训练轮次不存在");
        if (!"running".equals(String.valueOf(session.get("status")))) return ApiResponse.ok(session);
        int targetNo = number(body.get("target_no"), 0);
        int targetCount = number(session.get("target_count"), 1);
        if (targetNo < 1 || targetNo > targetCount) throw new IllegalArgumentException("靶机编号超出本轮范围");
        String eventId = text(body.get("event_id"), "");
        if (eventId.isEmpty()) throw new IllegalArgumentException("缺少命中事件ID");
        jdbc.update("insert ignore into training_hits(event_id,session_id,target_no,shot_no,hit_at_ms,split_ms,ring_score,accuracy,x_ratio,y_ratio) values(?,?,?,?,?,?,?,?,?,?)",
                eventId, id, targetNo, Math.max(1, number(body.get("shot_no"), 1)), Math.max(0, longNumber(body.get("hit_at_ms"), 0)),
                nullableLong(body.get("split_ms")), nullableDouble(body.get("ring_score")), nullableDouble(body.get("accuracy")), nullableDouble(body.get("x_ratio")), nullableDouble(body.get("y_ratio")));
        Number total = jdbc.queryForObject("select count(*) from training_hits where session_id=?", Number.class, id);
        int requiredHits = requiredHits(session);
        if (total != null && total.intValue() >= requiredHits) completeFromHits(id, session);
        Map<String,Object> result = Rows.one(jdbc, "select * from training_hits where event_id=?", eventId);
        result.put("session_completed", Rows.one(jdbc, "select status from training_sessions where id=?", id).get("status").equals("completed"));
        return ApiResponse.ok(result);
    }

    @PostMapping("/sessions/{id}/hits/batch")
    @Transactional
    public ApiResponse<Map<String,Object>> hitBatch(@PathVariable long id, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> session = Rows.one(jdbc, "select * from training_sessions where id=? and user_id=?", id, userId);
        if (session == null) throw new IllegalArgumentException("训练轮次不存在");
        int targetCount = number(session.get("target_count"), 1);
        Object rawHits = body.get("hits");
        if (!(rawHits instanceof Collection)) throw new IllegalArgumentException("缺少命中记录");
        int accepted = 0;
        for (Object value : (Collection<?>) rawHits) {
            if (!(value instanceof Map)) continue;
            Map<?,?> hit = (Map<?,?>) value;
            int targetNo = number(hit.get("target_no"), 0);
            String eventId = text(hit.get("event_id"), "");
            if (targetNo < 1 || targetNo > targetCount || eventId.isEmpty()) continue;
            accepted += jdbc.update("insert ignore into training_hits(event_id,session_id,target_no,shot_no,hit_at_ms,split_ms,ring_score,accuracy,x_ratio,y_ratio) values(?,?,?,?,?,?,?,?,?,?)",
                    eventId, id, targetNo, Math.max(1, number(hit.get("shot_no"), 1)), Math.max(0, longNumber(hit.get("hit_at_ms"), 0)),
                    nullableLong(hit.get("split_ms")), nullableDouble(hit.get("ring_score")), nullableDouble(hit.get("accuracy")), nullableDouble(hit.get("x_ratio")), nullableDouble(hit.get("y_ratio")));
        }
        Number total = jdbc.queryForObject("select count(*) from training_hits where session_id=?", Number.class, id);
        if (total != null && total.intValue() >= requiredHits(session)) completeFromHits(id, session);
        Map<String,Object> result = Rows.one(jdbc, "select * from training_sessions where id=?", id);
        result.put("accepted_hits", accepted);
        return ApiResponse.ok(result);
    }

    @PostMapping("/devices/heartbeat")
    @Transactional
    public ApiResponse<Map<String,Object>> heartbeat(@RequestBody Map<String,Object> body, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        String code = text(body.get("device_code"), "");
        if (code.isEmpty()) throw new IllegalArgumentException("缺少靶机设备编号");
        Map<String,Object> device = Rows.one(jdbc, "select * from training_devices where device_code=?", code);
        if (device == null) {
            jdbc.update("insert into training_devices(device_code,name,target_name,status,calibration_status,owner_user_id,frame_rate,last_heartbeat_at,last_seen_at) values(?,?,?, 'online', ?, ?, ?, now(), now())", code, text(body.get("name"), code), text(body.get("target_name"), ""), text(body.get("calibration_status"), "pending"), userId, body.get("frame_rate"));
        } else {
            jdbc.update("update training_devices set target_name=?,status='online',calibration_status=?,owner_user_id=?,frame_rate=?,last_heartbeat_at=now(),last_seen_at=now() where id=?", text(body.get("target_name"), String.valueOf(device.get("target_name"))), text(body.get("calibration_status"), String.valueOf(device.get("calibration_status"))), userId, body.get("frame_rate"), device.get("id"));
        }
        device = Rows.one(jdbc, "select * from training_devices where device_code=?", code);
        Map<String,Object> currentRoom = Rows.one(jdbc, "select r.id,r.target_count from training_room_members m join training_rooms r on r.id=m.room_id where m.user_id=? and r.status='open' order by r.id desc limit 1", userId);
        if (currentRoom != null && (device.get("room_id") == null || ((Number) device.get("room_id")).longValue() != ((Number) currentRoom.get("id")).longValue())) {
            long roomId = ((Number) currentRoom.get("id")).longValue(); int targetCount = ((Number) currentRoom.get("target_count")).intValue();
            Integer targetNo = jdbc.query("select n.target_no from (select 1 target_no union all select 2 union all select 3) n left join training_devices d on d.room_id=? and d.target_no=n.target_no and d.device_code<>? where n.target_no<=? and d.id is null order by n.target_no limit 1", rs -> rs.next() ? rs.getInt(1) : null, roomId, code, targetCount);
            if (targetNo != null) jdbc.update("update training_devices set room_id=?,target_no=? where device_code=?", roomId, targetNo, code);
        }
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_devices where device_code=?", code));
    }

    @PostMapping("/rooms/{id}/devices/refresh")
    @Transactional
    public ApiResponse<Map<String,Object>> refreshDevices(@PathVariable long id, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> room = Rows.one(jdbc, "select * from training_rooms where id=? and owner_user_id=? and status='open'", id, userId);
        if (room == null) throw new IllegalArgumentException("只能刷新并关联自己创建的开放房间");
        int targetCount = number(room.get("target_count"), 1);
        List<Map<String,Object>> devices = Rows.list(jdbc, "select * from training_devices where owner_user_id=? and status='online' and last_heartbeat_at>=date_sub(now(),interval 90 second) order by last_heartbeat_at desc,id desc", userId);
        int rebound = 0;
        for (Map<String,Object> device : devices) {
            if (rebound >= targetCount) break;
            Integer targetNo = jdbc.query("select n.target_no from (select 1 target_no union all select 2 union all select 3) n left join training_devices d on d.room_id=? and d.target_no=n.target_no and d.id<>? where n.target_no<=? and d.id is null order by n.target_no limit 1", rs -> rs.next() ? rs.getInt(1) : null, id, device.get("id"), targetCount);
            if (targetNo == null) break;
            jdbc.update("update training_devices set room_id=?,target_no=? where id=?", id, targetNo, device.get("id"));
            rebound++;
        }
        Map<String,Object> refreshed = roomView(id, userId);
        refreshed.put("rebound_device_count", rebound);
        refreshed.put("owned_online_device_count", devices.size());
        return ApiResponse.ok(refreshed);
    }

    @PutMapping("/devices/{code}/binding")
    @Transactional
    public ApiResponse<Map<String,Object>> bindDevice(@PathVariable String code, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        long roomId = longNumber(body.get("room_id"), 0);
        int targetNo = number(body.get("target_no"), 0);
        Map<String,Object> room = roomView(roomId, userId);
        if (targetNo < 1 || targetNo > number(room.get("target_count"), 1)) throw new IllegalArgumentException("靶机编号超出房间配置");
        Map<String,Object> device = Rows.one(jdbc, "select * from training_devices where device_code=?", code);
        if (device == null) throw new IllegalArgumentException("靶机设备未注册");
        Map<String,Object> occupied = Rows.one(jdbc, "select id from training_devices where room_id=? and target_no=? and id<>?", roomId, targetNo, device.get("id"));
        if (occupied != null) throw new IllegalArgumentException("该靶机号已被其他设备占用");
        jdbc.update("update training_devices set room_id=?,target_no=? where id=?", roomId,targetNo,device.get("id"));
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_devices where device_code=?", code));
    }

    @GetMapping("/devices/{code}/state")
    public ApiResponse<Map<String,Object>> deviceState(@PathVariable String code, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> device = Rows.one(jdbc, "select id,device_code,room_id,target_no,status,calibration_status,last_heartbeat_at from training_devices where device_code=? and owner_user_id=?", code, userId);
        if (device == null) throw new IllegalArgumentException("当前账号未关联该靶机");
        Long roomId = device.get("room_id") instanceof Number ? ((Number) device.get("room_id")).longValue() : null;
        device.put("running_session", roomId == null ? null : Rows.one(jdbc, "select id,session_code,user_id,status,started_at from training_sessions where room_id=? and status='running' order by id desc limit 1", roomId));
        if (roomId != null) {
            Map<String,Object> room = Rows.one(jdbc, "select target_hits_json,target_count from training_rooms where id=?", roomId);
            List<Integer> limits = targetHitsByTarget(room);
            int targetNo = number(device.get("target_no"), 1);
            device.put("target_hit_limit", limits.isEmpty() ? 5 : limits.get(Math.min(Math.max(targetNo - 1, 0), limits.size() - 1)));
        }
        return ApiResponse.ok(device);
    }

    @PostMapping("/devices/{code}/sessions/current/complete")
    @Transactional
    public ApiResponse<Map<String,Object>> completeDeviceSession(@PathVariable String code, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> device = Rows.one(jdbc, "select room_id from training_devices where device_code=? and owner_user_id=?", code, userId);
        if (device == null || !(device.get("room_id") instanceof Number)) throw new IllegalArgumentException("当前账号未关联该靶机");
        long roomId = ((Number) device.get("room_id")).longValue();
        Map<String,Object> session = Rows.one(jdbc, "select * from training_sessions where room_id=? and status='running' order by id desc limit 1", roomId);
        if (session == null) throw new IllegalArgumentException("当前没有进行中的训练");
        long sessionId = ((Number) session.get("id")).longValue();
        Number count = jdbc.queryForObject("select count(*) from training_hits where session_id=?", Number.class, sessionId);
        Double accuracy = jdbc.queryForObject("select avg(accuracy) from training_hits where session_id=? and accuracy is not null", Double.class, sessionId);
        jdbc.update("update training_sessions set status='completed',finished_at=now(),duration_ms=timestampdiff(microsecond,started_at,now()) div 1000,total_hits=?,average_accuracy=? where id=? and status='running'", count == null ? 0 : count.intValue(), accuracy == null ? 0 : accuracy, sessionId);
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_sessions where id=?", sessionId));
    }

    @PostMapping("/rooms")
    @Transactional
    public ApiResponse<Map<String,Object>> createRoom(@RequestBody Map<String,Object> body, HttpServletRequest request) {
        Map<String,Object> me = regularUser(request); int userId = ((Number) me.get("id")).intValue();
        long replaceRoomId = longNumber(body.get("replace_room_id"), 0);
        if (replaceRoomId > 0) {
            Map<String,Object> old = Rows.one(jdbc, "select id from training_rooms where id=? and owner_user_id=? and status='open'", replaceRoomId, userId);
            if (old != null) {
                jdbc.update("update training_sessions set status='completed',finished_at=now(),duration_ms=coalesce(duration_ms,timestampdiff(microsecond,started_at,now()) div 1000) where room_id=? and status='running'", replaceRoomId);
                jdbc.update("update training_rooms set status='closed' where id=?", replaceRoomId);
            }
        }
        String type = "multi".equals(text(body.get("room_type"), "solo")) ? "multi" : "solo";
        if ("solo".equals(type)) {
            jdbc.update("update training_sessions s join training_rooms r on r.id=s.room_id set s.status='completed',finished_at=now(),duration_ms=coalesce(s.duration_ms,timestampdiff(microsecond,s.started_at,now()) div 1000) where r.owner_user_id=? and r.room_type='solo' and r.status='open' and s.status='running'", userId);
            jdbc.update("update training_rooms set status='closed' where owner_user_id=? and room_type='solo' and status='open'", userId);
        }
        String name = text(body.get("name"), type.equals("solo") ? "单人训练" : "多人训练房");
        int targets = TrainingRules.targetCount(number(body.get("target_count"), 1));
        String code = "R-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        int targetHits = targetHits(number(body.get("target_hits"), 5));
        jdbc.update("insert into training_rooms(room_code,name,room_type,owner_user_id,target_count,training_mode,target_hits_json) values(?,?,?,?,?,?,?)", code, name, type, userId, targets, text(body.get("training_mode"), "precision"), "[" + targetHits + "]");
        Long id = jdbc.queryForObject("select id from training_rooms where room_code=?", Long.class, code);
        jdbc.update("insert into training_room_members(room_id,user_id,member_role) values(?,?,?)", id, userId, "owner");
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_rooms where id=?", id));
    }

    @PostMapping("/rooms/{id}/join")
    @Transactional
    public ApiResponse<Map<String,Object>> joinRoom(@PathVariable long id, HttpServletRequest request) {
        Map<String,Object> me = regularUser(request); int userId = ((Number) me.get("id")).intValue();
        Map<String,Object> room = Rows.one(jdbc, "select * from training_rooms where id=? and status='open'", id);
        if (room == null) throw new IllegalArgumentException("房间不存在或已关闭");
        Number count = jdbc.queryForObject("select count(*) from training_room_members where room_id=?", Number.class, id);
        if (count != null && TrainingRules.roomIsFull(count.intValue())) throw new IllegalArgumentException("房间已满");
        jdbc.update("insert ignore into training_room_members(room_id,user_id) values(?,?)", id, userId);
        return ApiResponse.ok(roomView(id, userId));
    }

    @DeleteMapping("/rooms/{id}/members/me")
    @Transactional
    public ApiResponse<Void> leaveRoom(@PathVariable long id, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> room = Rows.one(jdbc, "select * from training_rooms where id=? and status='open'", id);
        if (room == null) throw new IllegalArgumentException("房间不存在或已关闭");
        if (((Number) room.get("owner_user_id")).intValue() == userId) {
            Number members = jdbc.queryForObject("select count(*) from training_room_members where room_id=?", Number.class, id);
            if (members != null && members.intValue() > 1) throw new IllegalArgumentException("房主需先关闭房间，不能直接退出多人房间");
            jdbc.update("update training_rooms set status='closed' where id=?", id);
        }
        jdbc.update("delete from training_room_members where room_id=? and user_id=?", id, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/rooms/{id}/settings")
    @Transactional
    public ApiResponse<Map<String,Object>> settings(@PathVariable long id, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> room = Rows.one(jdbc, "select * from training_rooms where id=? and owner_user_id=? and status='open'", id, userId);
        if (room == null) throw new IllegalArgumentException("只有房主可以修改开放房间设置");
        int targets = TrainingRules.targetCount(number(body.get("target_count"), 1));
        double min = Math.max(0, decimal(body.get("beep_min_delay"), 2)); double max = Math.max(min, decimal(body.get("beep_max_delay"), 4));
        String hitsJson = targetHitsJson(body, targets, room);
        jdbc.update("update training_rooms set target_count=?,training_mode=?,target_hits_json=?,precision_preset=?,beep_min_delay=?,beep_max_delay=? where id=?", targets, text(body.get("training_mode"), "precision"), hitsJson, text(body.get("precision_preset"), "A4"), min, max, id);
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_rooms where id=?", id));
    }

    @PostMapping("/rooms/{id}/sessions")
    @Transactional
    public ApiResponse<Map<String,Object>> start(@PathVariable long id, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> room = Rows.one(jdbc, "select * from training_rooms where id=? and status='open'", id);
        if (room == null) throw new IllegalArgumentException("房间不存在或已关闭");
        if (Rows.one(jdbc, "select id from training_room_members where room_id=? and user_id=?", id, userId) == null) throw new SecurityException("请先加入房间");
        Map<String,Object> readiness = roomReadiness(id, number(room.get("target_count"), 1));
        if (!Boolean.TRUE.equals(readiness.get("ready"))) throw new IllegalArgumentException("靶机未连接，请先在 Android App 中连接并绑定靶机");
        Number running = jdbc.queryForObject("select count(*) from training_sessions where room_id=? and status='running'", Number.class, id);
        if (running != null && running.intValue() > 0) throw new IllegalArgumentException("当前房间已有队员正在训练");
        String code = "S-" + System.currentTimeMillis() + "-" + userId;
        jdbc.update("insert into training_sessions(session_code,room_id,user_id,status,training_mode,target_count,started_at) values(?,?,?,'running',?,?,now())",
                code, id, userId, room.get("training_mode"), room.get("target_count"));
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_sessions where session_code=?", code));
    }

    @PutMapping("/sessions/{id}/complete")
    @Transactional
    public ApiResponse<Map<String,Object>> complete(@PathVariable long id, @RequestBody(required=false) Map<String,Object> body, HttpServletRequest request) {
        int userId = ((Number) regularUser(request).get("id")).intValue();
        Map<String,Object> session = Rows.one(jdbc, "select * from training_sessions where id=? and user_id=? and status='running'", id, userId);
        if (session == null) throw new IllegalArgumentException("训练轮次不存在或已经结束");
        long duration = Math.max(0, longNumber(body == null ? null : body.get("duration_ms"), 0));
        Number hitCount = jdbc.queryForObject("select count(*) from training_hits where session_id=?", Number.class, id);
        int hits = hitCount == null ? 0 : hitCount.intValue();
        Double measuredAccuracy = jdbc.queryForObject("select avg(accuracy) from training_hits where session_id=? and accuracy is not null", Double.class, id);
        double accuracy = measuredAccuracy == null ? Math.max(0, Math.min(100, decimal(body == null ? null : body.get("average_accuracy"), 0))) : measuredAccuracy;
        jdbc.update("update training_sessions set status='completed',finished_at=now(),duration_ms=?,total_hits=?,average_accuracy=? where id=?", duration, hits, accuracy, id);
        return ApiResponse.ok(Rows.one(jdbc, "select * from training_sessions where id=?", id));
    }

    private Map<String,Object> roomView(long id, int userId) {
        Map<String,Object> room = Rows.one(jdbc, "select r.*,coalesce((select count(*) from training_room_members m where m.room_id=r.id),0) member_count,coalesce((select count(*) from training_room_members m where m.room_id=r.id and m.user_id=?),0) joined from training_rooms r where r.id=? and r.status='open'", userId, id);
        if (room == null || number(room.get("joined"), 0) == 0) throw new SecurityException("你不在该训练房间中");
        room.put("is_owner", ((Number) room.get("owner_user_id")).intValue() == userId);
        room.put("members", Rows.list(jdbc, "select u.id,u.username,u.callsign,u.avatar_url,m.member_role,m.joined_at from training_room_members m join users u on u.id=m.user_id where m.room_id=? order by case when m.member_role='owner' then 0 else 1 end,m.joined_at", id));
        room.put("devices", Rows.list(jdbc, "select id,device_code,name,target_name,status,calibration_status,target_no,frame_rate,last_heartbeat_at from training_devices where room_id=? order by target_no,id", id));
        room.putAll(roomReadiness(id, number(room.get("target_count"), 1)));
        room.put("target_hits", targetHitsForRoom(room));
        room.put("target_hits_by_target", targetHitsByTarget(room));
        room.put("running_session", Rows.one(jdbc, "select s.*,u.username,u.callsign from training_sessions s join users u on u.id=s.user_id where s.room_id=? and s.status='running' order by s.id desc limit 1", id));
        return room;
    }
    private Map<String,Object> roomReadiness(long roomId, int targetCount) {
        int connected = jdbc.queryForObject("select count(distinct target_no) from training_devices where room_id=? and target_no between 1 and ? and status='online' and last_heartbeat_at >= date_sub(now(), interval 90 second)", Integer.class, roomId, targetCount);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("connected_target_count", connected);
        out.put("targets_ready", connected >= targetCount);
        out.put("ready", connected >= targetCount);
        return out;
    }
    private int targetHits(int value) { return Math.max(1, Math.min(100, value)); }
    private int targetHitsForRoom(Map<String,Object> room) {
        String raw = String.valueOf(room.getOrDefault("target_hits_json", "[5]"));
        String digits = raw.replaceAll("[^0-9]+", " ").trim();
        if (digits.isEmpty()) return 5;
        try { return targetHits(Integer.parseInt(digits.split(" ")[0])); } catch (Exception e) { return 5; }
    }
    private List<Integer> targetHitsByTarget(Map<String,Object> room) {
        String raw = String.valueOf(room.getOrDefault("target_hits_json", "[5]"));
        List<Integer> out = new ArrayList<>();
        for (String part : raw.replaceAll("[\\[\\]]", "").split(",")) if (!part.trim().isEmpty()) out.add(targetHits(number(part.trim(), 5)));
        if (out.isEmpty()) out.add(5);
        return out;
    }
    private String targetHitsJson(Map<String,Object> body, int targetCount, Map<String,Object> room) {
        Object values = body.get("target_hits_by_target");
        List<Integer> hits = new ArrayList<>();
        if (values instanceof Collection) for (Object value : (Collection<?>) values) hits.add(targetHits(number(value, 5)));
        int unified = targetHits(number(body.get("target_hits"), targetHitsForRoom(room)));
        while (hits.size() < targetCount) hits.add(unified);
        if (hits.size() > targetCount) hits = new ArrayList<>(hits.subList(0, targetCount));
        return hits.toString();
    }
    private List<Map<String,Object>> openRooms(int userId) { return Rows.list(jdbc, "select r.*,u.username owner_username,u.callsign owner_callsign,coalesce((select count(*) from training_room_members m where m.room_id=r.id),0) member_count,coalesce((select count(*) from training_room_members m where m.room_id=r.id and m.user_id=?),0) joined,coalesce((select count(*) from training_sessions s where s.room_id=r.id and s.status='running'),0) running from training_rooms r join users u on u.id=r.owner_user_id where r.status='open' and r.room_type='multi' order by r.created_at desc", userId); }
    private Map<String,Object> regularUser(HttpServletRequest request) { Map<String,Object> user = auth.current(request); Object value = user.get("is_regular_member"); if (!(Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value)))) throw new SecurityException("正式队员才可以进入训练屋"); return user; }
    private static String text(Object value, String fallback) { String s = value == null ? "" : String.valueOf(value).trim(); return s.isEmpty() ? fallback : s; }
    private static int number(Object value, int fallback) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return fallback; } }
    private static double decimal(Object value, double fallback) { try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return fallback; } }
    private static long longNumber(Object value, long fallback) { try { return Long.parseLong(String.valueOf(value)); } catch (Exception e) { return fallback; } }
    private static Long nullableLong(Object value) { try { return value == null ? null : Long.parseLong(String.valueOf(value)); } catch (Exception e) { return null; } }
    private static Double nullableDouble(Object value) { try { return value == null ? null : Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return null; } }
    private int requiredHits(Map<String,Object> session) {
        Map<String,Object> room = Rows.one(jdbc, "select target_hits_json,target_count from training_rooms where id=?", session.get("room_id"));
        int perTarget = 5;
        if (room != null && room.get("target_hits_json") != null) {
            String text = String.valueOf(room.get("target_hits_json")).replaceAll("[^0-9]", "");
            if (!text.isEmpty()) perTarget = Math.max(1, number(text, 5));
        }
        int total = 0;
        for (Integer value : targetHitsByTarget(room)) total += value;
        return total > 0 ? total : perTarget * number(session.get("target_count"), 1);
    }
    private void completeFromHits(long id, Map<String,Object> session) {
        Number count = jdbc.queryForObject("select count(*) from training_hits where session_id=?", Number.class, id);
        Double accuracy = jdbc.queryForObject("select avg(accuracy) from training_hits where session_id=? and accuracy is not null", Double.class, id);
        jdbc.update("update training_sessions set status='completed',finished_at=now(),duration_ms=timestampdiff(microsecond,started_at,now()) div 1000,total_hits=?,average_accuracy=? where id=? and status='running'", count == null ? 0 : count.intValue(), accuracy == null ? 0 : accuracy, id);
    }
}
