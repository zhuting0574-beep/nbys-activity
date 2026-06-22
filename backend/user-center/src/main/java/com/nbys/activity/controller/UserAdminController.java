package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.PasswordService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final PasswordService passwords;

    public UserAdminController(JdbcTemplate jdbc, AuthService auth, PasswordService passwords) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.passwords = passwords;
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String role,
                                                        @RequestParam(required = false) Integer disabled) {
        String q = keyword == null ? "" : keyword.trim();
        String r = role == null ? "" : role.trim();
        Integer d = disabled == null ? -1 : disabled;
        return ApiResponse.ok(Rows.list(jdbc,
                "select u.*, i.username inviter_name, i.callsign inviter_callsign from users u left join users i on u.invited_by_id=i.id " +
                        "where (?='' or u.username like concat('%',?,'%') or u.callsign like concat('%',?,'%')) " +
                        "and (?='' or u.role=?) and (?=-1 or u.disabled=?) order by u.id desc",
                q, q, q, r, r, d, d));
    }

    @GetMapping("/users/non-formal/options")
    public ApiResponse<List<Map<String, Object>>> nonFormalUsers() {
        return ApiResponse.ok(Rows.list(jdbc, "select id,username,callsign from users where disabled=0 and is_regular_member=0 order by id desc"));
    }

    @GetMapping("/users/formal/options")
    public ApiResponse<List<Map<String, Object>>> formalUsers() {
        return ApiResponse.ok(Rows.list(jdbc, "select id,username,callsign from users where disabled=0 and is_regular_member=1 order by callsign"));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Void> updateUser(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "user:update");
        jdbc.update("update users set callsign=?, role=?, disabled=?, is_regular_member=? where id=?",
                body.get("callsign"), body.get("role"), bool(body.get("disabled")), bool(body.get("is_regular_member")), id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "user:resetPassword");
        jdbc.update("update users set password_hash=? where id=?", passwords.encodeDefaultPassword(), id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "user:delete");
        jdbc.update("delete from users where id=?", id);
        return ApiResponse.ok(null);
    }

    private int bool(Object v) {
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue() == 0 ? 0 : 1;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v)) ? 1 : 0;
    }
}
