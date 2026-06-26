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
        String phone = normalizedPhone(body.get("phone"));
        String idCard = normalizedIdCard(body.get("id_card"));
        if (username.isEmpty() || password.isEmpty()) throw new IllegalArgumentException("用户名、密码不能为空");
        validateIdentity(phone, idCard);
        if (Rows.one(jdbc, "select id from users where username=?", username) != null) throw new IllegalArgumentException("用户名已存在");
        if (!callsign.isEmpty() && Rows.one(jdbc, "select id from users where callsign=?", callsign) != null) throw new IllegalArgumentException("呼号已存在");
        if (Rows.one(jdbc, "select id from users where id_card=?", idCard) != null) throw new IllegalArgumentException("该身份证号已注册，禁止重复注册");
        Integer invitedBy = null;
        String inviteCode = text(body.get("invite_code"));
        if (!inviteCode.isEmpty()) {
            Map<String, Object> inviter = findInviter(inviteCode);
            if (inviter == null) throw new IllegalArgumentException("邀请码不存在");
            invitedBy = ((Number) inviter.get("id")).intValue();
        }
        jdbc.update("insert into users(username,callsign,avatar_url,phone,id_card,password_hash,role,disabled,is_regular_member,attendance_manager,extraction_authorized,extraction_manager,invited_by_id,created_at,last_seen) values(?,?,?,?,?,?,'user',0,0,0,0,0,?,now(),now())",
                username, callsign, text(body.get("avatar_url")), phone, idCard, passwords.encode(password), invitedBy);
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
        String phone = normalizedPhone(body.get("phone"));
        String idCard = normalizedIdCard(body.get("id_card"));
        if (username.isEmpty()) throw new IllegalArgumentException("用户名不能为空");
        validateIdentity(phone, idCard);
        if (Rows.one(jdbc, "select id from users where id<>? and username=?", userId, username) != null) throw new IllegalArgumentException("用户名已存在");
        if (!callsign.isEmpty() && Rows.one(jdbc, "select id from users where id<>? and callsign=?", userId, callsign) != null) throw new IllegalArgumentException("呼号已存在");
        if (Rows.one(jdbc, "select id from users where id<>? and id_card=?", userId, idCard) != null) throw new IllegalArgumentException("该身份证号已注册，禁止重复注册");
        jdbc.update("update users set username=?, callsign=?, avatar_url=?, phone=?, id_card=? where id=?", username, callsign, avatar, phone, idCard, userId);
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

    private String normalizedPhone(Object value) {
        return text(value).replaceAll("\\s+", "");
    }

    private String normalizedIdCard(Object value) {
        return text(value).replaceAll("\\s+", "").toUpperCase();
    }

    private void validateIdentity(String phone, String idCard) {
        if (!phone.matches("^1\\d{10}$")) throw new IllegalArgumentException("请输入合法手机号");
        if (!idCard.matches("^\\d{17}[0-9X]$")) throw new IllegalArgumentException("请输入合法身份证号");
    }

    private Map<String, Object> findInviter(String inviteCode) {
        List<Map<String, Object>> users = Rows.list(jdbc, "select id,username,callsign from users where disabled=0");
        for (Map<String, Object> user : users) {
            if (AuthService.inviteCode(user).equalsIgnoreCase(inviteCode)) return user;
        }
        return null;
    }
}
