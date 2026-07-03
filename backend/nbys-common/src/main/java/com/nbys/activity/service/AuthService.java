package com.nbys.activity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final Map<String, Integer> sessions = new ConcurrentHashMap<String, Integer>();

    public AuthService(JdbcTemplate jdbc, PasswordService passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    public Map<String, Object> login(String account, String password, boolean admin) {
        Map<String, Object> user = Rows.one(jdbc,
                "select * from users where disabled=0 and (username=? or callsign=?) limit 1", account, account);
        if (user == null || !passwords.matches(password, String.valueOf(user.get("password_hash")))) {
            throw new SecurityException("账号或密码错误");
        }
        if (truthy(user.get("must_change_password")) && temporaryPasswordExpired(user.get("temp_password_expires_at"))) {
            throw new SecurityException("临时密码已过期，请联系管理员重新重置");
        }
        String role = String.valueOf(user.get("role"));
        if (admin && ("guest".equals(role) || "user".equals(role) && !hasAnyBackofficeAccess((Number) user.get("id")))) {
            throw new SecurityException("无后管访问权限");
        }
        String token = user.get("id") + "." + UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, ((Number) user.get("id")).intValue());
        user.remove("password_hash");
        user.put("invite_code", inviteCode(user));
        user.put("token", token);
        user.put("permissions", permissions(role));
        return user;
    }

    public Map<String, Object> current(HttpServletRequest request) {
        Map<String, Object> user = currentForPasswordChange(request);
        if (truthy(user.get("must_change_password"))) throw new SecurityException("请先修改临时密码");
        return user;
    }

    public Map<String, Object> currentForPasswordChange(HttpServletRequest request) {
        Integer id = currentUserId(request);
        if (id == null) throw new SecurityException("未登录");
        Map<String, Object> user = Rows.one(jdbc, "select * from users where id=?", id);
        if (user == null) throw new SecurityException("用户不存在");
        user.remove("password_hash");
        user.put("invite_code", inviteCode(user));
        user.put("permissions", permissions(String.valueOf(user.get("role"))));
        return user;
    }

    private boolean temporaryPasswordExpired(Object value) {
        if (value == null) return true;
        if (value instanceof Timestamp) return ((Timestamp) value).before(new Timestamp(System.currentTimeMillis()));
        try {
            return Timestamp.valueOf(String.valueOf(value).replace('T', ' ')).before(new Timestamp(System.currentTimeMillis()));
        } catch (Exception e) {
            return true;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    public Integer currentUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        Integer sessionUserId = sessions.get(token);
        if (sessionUserId != null) return sessionUserId;
        int dot = token.indexOf('.');
        if (dot <= 0) return null;
        try {
            return Integer.parseInt(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void require(HttpServletRequest request, String permission) {
        Map<String, Object> user = current(request);
        String role = String.valueOf(user.get("role"));
        if ("superadmin".equals(role) || "admin".equals(role)) return;
        if (!permissions(role).contains(permission)) throw new SecurityException("没有操作权限");
    }

    public Set<String> permissions(String role) {
        if ("superadmin".equals(role) || "admin".equals(role)) return allPermissions();
        List<Map<String, Object>> rows = Rows.list(jdbc, "select permission_code from role_permissions where role=?", role);
        if (!rows.isEmpty()) {
            Set<String> result = new HashSet<String>();
            for (Map<String, Object> row : rows) result.add(String.valueOf(row.get("permission_code")));
            return result;
        }
        return defaultPermissions(role);
    }

    private boolean hasAnyBackofficeAccess(Number userId) {
        Map<String, Object> user = Rows.one(jdbc, "select role from users where id=?", userId);
        return user != null && !"guest".equals(String.valueOf(user.get("role")));
    }

    private Set<String> defaultPermissions(String role) {
        Set<String> p = new HashSet<String>();
        Collections.addAll(p, "activity:view", "venue:view", "gameMode:view", "user:view", "role:view", "permission:view", "attendance:view", "launcher:view", "systemImage:view");
        if ("activity_admin".equals(role)) {
            Collections.addAll(p, "activity:create", "activity:update", "activity:delete", "activity:cancel", "activity:restore", "plan:create", "plan:update", "plan:delete");
        }
        if ("attendance_admin".equals(role)) {
            Collections.addAll(p, "attendance:create", "attendance:update", "attendance:delete", "attendance:export");
        }
        return p;
    }

    private Set<String> allPermissions() {
        Set<String> p = defaultPermissions("admin");
        Collections.addAll(p, "activity:create", "activity:update", "activity:delete", "activity:cancel", "activity:restore",
                "plan:create", "plan:update", "plan:delete",
                "venue:create", "venue:update", "venue:delete", "gameMode:create", "gameMode:update", "gameMode:delete",
                "user:update", "user:delete", "user:disable", "user:resetPassword", "role:update", "permission:update",
                "launcher:update", "launcher:delete", "systemImage:update",
                "attendance:create", "attendance:update", "attendance:delete", "attendance:export");
        return p;
    }

    public static String inviteCode(Map<String, Object> user) {
        return md5(String.valueOf(user.get("callsign")) + String.valueOf(user.get("username"))).substring(0, 10);
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
