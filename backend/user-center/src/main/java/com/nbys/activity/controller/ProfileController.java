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
@RequestMapping("/api/h5")
public class ProfileController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final PasswordService passwords;

    public ProfileController(JdbcTemplate jdbc, AuthService auth, PasswordService passwords) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.passwords = passwords;
    }

    @PostMapping("/auth/register")
    public ApiResponse<Void> register(@RequestBody Map<String, Object> body) {
        String username = text(body.get("username"));
        String callsign = text(body.get("callsign"));
        String password = text(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) throw new IllegalArgumentException("用户名、密码不能为空");
        if (Rows.one(jdbc, "select id from users where username=?", username) != null) throw new IllegalArgumentException("用户名已存在");
        if (!callsign.isEmpty() && Rows.one(jdbc, "select id from users where callsign=?", callsign) != null) throw new IllegalArgumentException("呼号已存在");
        Integer invitedBy = null;
        String inviteCode = text(body.get("invite_code"));
        if (!inviteCode.isEmpty()) {
            Map<String, Object> inviter = findInviter(inviteCode);
            if (inviter == null) throw new IllegalArgumentException("邀请码不存在");
            invitedBy = ((Number) inviter.get("id")).intValue();
        }
        jdbc.update("insert into users(username,callsign,avatar_url,password_hash,role,disabled,is_regular_member,attendance_manager,extraction_authorized,extraction_manager,invited_by_id,created_at,last_seen) values(?,?,?,?,'user',0,0,0,0,0,?,now(),now())",
                username, callsign, text(body.get("avatar_url")), passwords.encode(password), invitedBy);
        return ApiResponse.ok(null);
    }

    @PutMapping("/me/profile")
    public ApiResponse<Void> updateProfile(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        Map<String, Object> current = Rows.one(jdbc, "select username,callsign,avatar_url from users where id=?", userId);
        String username = text(body.get("username"));
        String callsign = text(body.get("callsign"));
        if (username.isEmpty() && current != null) username = text(current.get("username"));
        if (callsign.isEmpty() && current != null) callsign = text(current.get("callsign"));
        String avatar = body.containsKey("avatar_url") ? text(body.get("avatar_url")) : current == null ? "" : text(current.get("avatar_url"));
        if (username.isEmpty()) throw new IllegalArgumentException("用户名不能为空");
        if (Rows.one(jdbc, "select id from users where id<>? and username=?", userId, username) != null) throw new IllegalArgumentException("用户名已存在");
        if (!callsign.isEmpty() && Rows.one(jdbc, "select id from users where id<>? and callsign=?", userId, callsign) != null) throw new IllegalArgumentException("呼号已存在");
        jdbc.update("update users set username=?, callsign=?, avatar_url=? where id=?", username, callsign, avatar, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> updatePassword(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.currentForPasswordChange(req).get("id")).intValue();
        String password = text(body.get("password"));
        String confirm = text(body.get("confirm_password"));
        if (password.isEmpty() || !password.equals(confirm)) throw new IllegalArgumentException("两次密码不一致");
        if (password.length() < 8) throw new IllegalArgumentException("密码至少需要8位");
        jdbc.update("update users set password_hash=?, must_change_password=0, temp_password_expires_at=null where id=?", passwords.encode(password), userId);
        return ApiResponse.ok(null);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> findInviter(String inviteCode) {
        List<Map<String, Object>> users = Rows.list(jdbc, "select id,username,callsign from users where disabled=0");
        for (Map<String, Object> user : users) {
            if (AuthService.inviteCode(user).equalsIgnoreCase(inviteCode)) return user;
        }
        return null;
    }
}
