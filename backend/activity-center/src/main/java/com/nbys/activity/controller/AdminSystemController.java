package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AdminSystemController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminSystemController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/api/public/system-settings/images")
    public ApiResponse<Map<String, Object>> publicImages() {
        return ApiResponse.ok(imageSettings());
    }

    @GetMapping("/api/admin/system/images")
    public ApiResponse<Map<String, Object>> adminImages(HttpServletRequest req) {
        auth.require(req, "systemImage:view");
        return ApiResponse.ok(imageSettings());
    }

    @PutMapping("/api/admin/system/images")
    public ApiResponse<Void> saveImages(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "systemImage:update");
        saveSetting("h5_login_background_url", text(body.get("login_background_url")));
        saveSetting("h5_login_logo_url", text(body.get("login_logo_url")));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/launchers")
    public ApiResponse<List<Map<String, Object>>> launchers(@RequestParam(required = false) String name,
                                                            @RequestParam(required = false) String user,
                                                            HttpServletRequest req) {
        auth.require(req, "launcher:view");
        String n = text(name);
        String u = text(user);
        return ApiResponse.ok(Rows.list(jdbc,
                "select l.*, u.username owner_name, u.callsign owner_callsign " +
                        "from launcher_rental_items l join users u on u.id=l.created_by_id " +
                        "where (?='' or l.name like concat('%',?,'%')) " +
                        "and (?='' or u.username like concat('%',?,'%') or u.callsign like concat('%',?,'%')) " +
                        "order by l.id desc",
                n, n, u, u, u));
    }

    @PutMapping("/api/admin/launchers/{id}")
    public ApiResponse<Void> updateLauncher(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "launcher:update");
        int updated = jdbc.update(
                "update launcher_rental_items set name=?, rent_fee=?, description=?, active=?, updated_at=now() where id=?",
                body.get("name"), body.get("rent_fee"), body.get("description"), bool(body.get("active")) ? 1 : 0, id);
        if (updated == 0) throw new IllegalArgumentException("发射器不存在");
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/launchers/{id}")
    @Transactional
    public ApiResponse<Void> deleteLauncher(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "launcher:delete");
        Map<String, Object> item = Rows.one(jdbc, "select id from launcher_rental_items where id=?", id);
        if (item == null) throw new IllegalArgumentException("发射器不存在");
        jdbc.update("delete n from user_notifications n join activity_launcher_rentals r on r.id=n.related_id where n.type='launcher_rental' and r.launcher_id=?", id);
        jdbc.update("delete from activity_launcher_options where launcher_id=?", id);
        jdbc.update("delete from activity_launcher_rentals where launcher_id=?", id);
        jdbc.update("delete from launcher_rental_items where id=?", id);
        return ApiResponse.ok(null);
    }

    private Map<String, Object> imageSettings() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("login_background_url", setting("h5_login_background_url"));
        result.put("login_logo_url", setting("h5_login_logo_url"));
        return result;
    }

    private String setting(String key) {
        Map<String, Object> row = Rows.one(jdbc, "select setting_value from system_settings where setting_key=?", key);
        return row == null ? "" : text(row.get("setting_value"));
    }

    private void saveSetting(String key, String value) {
        jdbc.update("insert into system_settings(setting_key,setting_value,updated_at) values(?,?,now()) " +
                "on duplicate key update setting_value=values(setting_value),updated_at=now()", key, value);
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
