package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController
public class RentalController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public RentalController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/api/h5/launcher-rentals/my-items")
    public ApiResponse<List<Map<String, Object>>> myItems(HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        return ApiResponse.ok(Rows.list(jdbc, "select * from launcher_rental_items where created_by_id=? order by id desc", userId));
    }

    @PostMapping("/api/h5/launcher-rentals/my-items")
    public ApiResponse<Void> createItem(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        jdbc.update("insert into launcher_rental_items(owner_type,name,description,photo_filename,rent_fee,active,created_by_id,created_at,updated_at) values('user',?,?,?,?,1,?,now(),now())",
                body.get("name"), body.get("description"), body.get("photo_filename"), body.get("rent_fee"), userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/h5/launcher-rentals/my-items/{id}/off")
    public ApiResponse<Void> off(@PathVariable int id, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        jdbc.update("update launcher_rental_items set active=0, updated_at=now() where id=? and created_by_id=?", id, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/h5/launcher-rentals/my-items/{id}/on")
    public ApiResponse<Void> on(@PathVariable int id, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        jdbc.update("update launcher_rental_items set active=1, updated_at=now() where id=? and created_by_id=?", id, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/h5/launcher-rentals/my-items/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable int id, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        Map<String, Object> item = Rows.one(jdbc, "select id from launcher_rental_items where id=? and created_by_id=?", id, userId);
        if (item == null) throw new IllegalArgumentException("发射器不存在或无权删除");
        jdbc.update("delete n from user_notifications n join activity_launcher_rentals r on r.id=n.related_id where n.type='launcher_rental' and r.launcher_id=?", id);
        jdbc.update("delete from activity_launcher_rentals where launcher_id=?", id);
        jdbc.update("delete from launcher_rental_items where id=? and created_by_id=?", id, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/h5/activities/{activityId}/launcher-rentals")
    public ApiResponse<List<Map<String, Object>>> activityItems(@PathVariable int activityId) {
        return ApiResponse.ok(Rows.list(jdbc,
                "select l.*, u.username owner_name, u.callsign owner_callsign, " +
                        "case when r.id is null then null else 'confirmed' end rental_status, r.user_id renter_id " +
                        "from ( " +
                        "  select launcher_id,min(sort_order) sort_order from ( " +
                        "    select o.launcher_id,0 sort_order from activity_launcher_options o where o.activity_id=? " +
                        "    union all " +
                        "    select l2.id launcher_id,1 sort_order from launcher_rental_items l2 join enrollments e on e.user_id=l2.created_by_id and e.activity_id=? where l2.active=1 " +
                        "  ) x group by launcher_id " +
                        ") src join launcher_rental_items l on l.id=src.launcher_id " +
                        "join users u on u.id=l.created_by_id " +
                        "left join activity_launcher_rentals r on r.activity_id=? and r.launcher_id=l.id and r.cancelled_at is null and r.status<>'cancelled' " +
                        "where l.active=1 order by src.sort_order,l.id desc", activityId, activityId, activityId));
    }

    @PostMapping("/api/h5/activities/{activityId}/launcher-rentals/{launcherId}")
    public ApiResponse<Void> rent(@PathVariable int activityId, @PathVariable int launcherId, HttpServletRequest req) {
        Map<String, Object> me = auth.current(req);
        int userId = ((Number) me.get("id")).intValue();
        Map<String, Object> launcher = Rows.one(jdbc, "select * from launcher_rental_items where id=? and active=1", launcherId);
        if (launcher == null) throw new IllegalArgumentException("发射器不存在或未上架");
        if (((Number) launcher.get("created_by_id")).intValue() == userId) throw new IllegalArgumentException("不可租借自己的发射器");
        if (Rows.one(jdbc,
                "select t.launcher_id from (" +
                        "select o.launcher_id from activity_launcher_options o where o.activity_id=? and o.launcher_id=? " +
                        "union all " +
                        "select l.id launcher_id from launcher_rental_items l join enrollments e on e.user_id=l.created_by_id and e.activity_id=? where l.id=? and l.active=1 " +
                        ") t limit 1",
                activityId, launcherId, activityId, launcherId) == null) throw new IllegalArgumentException("该发射器不在当前活动可租赁列表");
        if (Rows.one(jdbc, "select id from activity_launcher_rentals where activity_id=? and launcher_id=? and cancelled_at is null and status<>'cancelled'", activityId, launcherId) != null) throw new IllegalArgumentException("已被租借");
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into activity_launcher_rentals(activity_id,launcher_id,user_id,status,rented_at,confirmed_at) values(?,?,?,'confirmed',now(),now())");
            ps.setInt(1, activityId);
            ps.setInt(2, launcherId);
            ps.setInt(3, userId);
            return ps;
        });
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/h5/launcher-rentals/{rentalId}/confirm")
    public ApiResponse<Void> confirm(@PathVariable int rentalId, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        int updated = jdbc.update("update activity_launcher_rentals r join launcher_rental_items l on l.id=r.launcher_id set r.status='confirmed', r.confirmed_at=now() where r.id=? and l.created_by_id=? and r.status='pending'", rentalId, userId);
        if (updated == 0) throw new IllegalArgumentException("租借申请不存在或已处理");
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/h5/launcher-rentals/{rentalId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable int rentalId, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        jdbc.update("update activity_launcher_rentals set status='cancelled', cancelled_at=now() where id=? and user_id=?", rentalId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/h5/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications(HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        return ApiResponse.ok(Rows.list(jdbc,
                "select n.*, r.id rental_action_id, r.status rental_status, r.user_id rental_user_id, r.launcher_id rental_launcher_id " +
                        "from user_notifications n " +
                        "left join activity_launcher_rentals r on r.id=(select r2.id from activity_launcher_rentals r2 " +
                        "join launcher_rental_items l2 on l2.id=r2.launcher_id " +
                        "where n.type='launcher_rental' and (r2.id=n.related_id or (r2.launcher_id=n.related_id and l2.created_by_id=n.user_id)) " +
                        "order by case when r2.id=n.related_id then 0 else 1 end, r2.id desc limit 1) " +
                        "where n.user_id=? order by n.created_at desc,n.id desc limit 50", userId));
    }

    @PutMapping("/api/h5/notifications/read-all")
    public ApiResponse<Void> readAllNotifications(HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        jdbc.update("update user_notifications set read_at=now() where user_id=? and read_at is null", userId);
        return ApiResponse.ok(null);
    }
}
