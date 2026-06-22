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
        if (username.isEmpty() || callsign.isEmpty() || password.isEmpty()) throw new IllegalArgumentException("用户名、呼号、密码不能为空");
        if (Rows.one(jdbc, "select id from users where username=? or callsign=?", username, callsign) != null) throw new IllegalArgumentException("用户名或呼号已存在");
        Integer invitedBy = null;
        String inviteCode = text(body.get("invite_code"));
        if (!inviteCode.isEmpty()) {
            Map<String, Object> inviter = findInviter(inviteCode);
            if (inviter == null) throw new IllegalArgumentException("邀请码不存在");
            invitedBy = ((Number) inviter.get("id")).intValue();
        }
        jdbc.update("insert into users(username,callsign,avatar_url,password_hash,role,disabled,is_regular_member,attendance_manager,extraction_authorized,extraction_manager,invited_by_id,created_at,last_seen) values(?,?,?,?, 'user',0,0,0,0,0,?,now(),now())",
                username, callsign, text(body.get("avatar_url")), passwords.encode(password), invitedBy);
        return ApiResponse.ok(null);
    }

    @PutMapping("/me/profile")
    public ApiResponse<Void> updateProfile(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        String username = text(body.get("username"));
        String callsign = text(body.get("callsign"));
        if (username.isEmpty() || callsign.isEmpty()) throw new IllegalArgumentException("用户名、呼号不能为空");
        if (Rows.one(jdbc, "select id from users where id<>? and (username=? or callsign=?)", userId, username, callsign) != null) throw new IllegalArgumentException("用户名或呼号已存在");
        jdbc.update("update users set username=?, callsign=?, avatar_url=? where id=?", username, callsign, text(body.get("avatar_url")), userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> updatePassword(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = ((Number) auth.current(req).get("id")).intValue();
        String password = text(body.get("password"));
        String confirm = text(body.get("confirm_password"));
        if (password.isEmpty() || !password.equals(confirm)) throw new IllegalArgumentException("两次密码不一致");
        jdbc.update("update users set password_hash=? where id=?", passwords.encode(password), userId);
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
