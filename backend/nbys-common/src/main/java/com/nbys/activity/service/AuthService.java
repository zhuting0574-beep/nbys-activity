package com.nbys.activity.service;

import com.nbys.activity.config.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {
    private static final long ACCESS_TOKEN_SECONDS = 2 * 60 * 60;
    private static final int REFRESH_TOKEN_SECONDS = 30 * 24 * 60 * 60;
    private static final String REFRESH_COOKIE = "nbysRefreshToken";

    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final byte[] tokenSecret;

    public AuthService(JdbcTemplate jdbc, PasswordService passwords,
                       @Value("${AUTH_TOKEN_SECRET:nbys-local-development-secret-change-me}") String tokenSecret) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokenSecret = tokenSecret.getBytes(StandardCharsets.UTF_8);
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
        if (admin && isGuest(user)) throw new SecurityException("游客暂时无权限登录");
        return authenticatedUser(user);
    }

    public void issueRefreshToken(Number userId, HttpServletRequest request, HttpServletResponse response) {
        String raw = randomToken();
        Timestamp expires = Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS));
        jdbc.update("insert into auth_refresh_tokens(token_hash,user_id,expires_at,created_at,last_used_at) values(?,?,?,now(),now())",
                sha256(raw), userId.intValue(), expires);
        setRefreshCookie(raw, request, response, REFRESH_TOKEN_SECONDS);
    }

    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = cookieValue(request, REFRESH_COOKIE);
        if (raw.isEmpty()) throw new AuthenticationException("登录已失效");
        Map<String, Object> session = Rows.one(jdbc,
                "select r.user_id,u.* from auth_refresh_tokens r join users u on u.id=r.user_id " +
                        "where r.token_hash=? and r.revoked_at is null and r.expires_at>now() and u.disabled=0 limit 1",
                sha256(raw));
        if (session == null) {
            clearRefreshCookie(request, response);
            throw new AuthenticationException("登录已失效");
        }
        Timestamp expires = Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS));
        jdbc.update("update auth_refresh_tokens set expires_at=?,last_used_at=now() where token_hash=?", expires, sha256(raw));
        setRefreshCookie(raw, request, response, REFRESH_TOKEN_SECONDS);
        return authenticatedUser(session);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String raw = cookieValue(request, REFRESH_COOKIE);
        if (!raw.isEmpty()) jdbc.update("update auth_refresh_tokens set revoked_at=now() where token_hash=? and revoked_at is null", sha256(raw));
        clearRefreshCookie(request, response);
    }

    public Map<String, Object> current(HttpServletRequest request) {
        Map<String, Object> user = currentForPasswordChange(request);
        if (truthy(user.get("must_change_password"))) throw new SecurityException("请先修改临时密码");
        return user;
    }

    public Map<String, Object> currentAdmin(HttpServletRequest request) {
        Map<String, Object> user = current(request);
        if (isGuest(user)) throw new SecurityException("游客暂时无权限登录");
        return user;
    }

    public Map<String, Object> currentForPasswordChange(HttpServletRequest request) {
        Integer id = currentUserId(request);
        if (id == null) throw new AuthenticationException("未登录");
        Map<String, Object> user = Rows.one(jdbc, "select * from users where id=? and disabled=0", id);
        if (user == null) throw new AuthenticationException("登录已失效");
        user.remove("password_hash");
        user.put("invite_code", inviteCode(user));
        user.put("permissions", permissions(String.valueOf(user.get("role"))));
        return user;
    }

    public Integer currentUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        int dot = token.lastIndexOf('.');
        if (dot <= 0) return null;
        String payload = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!constantEquals(signature, sign(payload))) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            if (parts.length != 3 || Long.parseLong(parts[1]) <= Instant.now().getEpochSecond()) return null;
            return Integer.parseInt(parts[0]);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void require(HttpServletRequest request, String permission) {
        Map<String, Object> user = currentAdmin(request);
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

    public boolean isGuest(Map<String, Object> user) {
        String role = String.valueOf(user.get("role"));
        return "guest".equals(role) || ("user".equals(role) && !truthy(user.get("is_regular_member")));
    }

    private Map<String, Object> authenticatedUser(Map<String, Object> source) {
        Map<String, Object> user = new HashMap<String, Object>(source);
        user.remove("password_hash");
        user.put("invite_code", inviteCode(user));
        user.put("token", accessToken(((Number) user.get("id")).intValue()));
        user.put("permissions", permissions(String.valueOf(user.get("role"))));
        return user;
    }

    private String accessToken(int userId) {
        String value = userId + ":" + (Instant.now().getEpochSecond() + ACCESS_TOKEN_SECONDS) + ":" + UUID.randomUUID().toString().replace("-", "");
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("令牌签名失败", e);
        }
    }

    private boolean constantEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void setRefreshCookie(String value, HttpServletRequest request, HttpServletResponse response, int maxAge) {
        String secure = isSecure(request) ? "; Secure" : "";
        response.addHeader("Set-Cookie", REFRESH_COOKIE + "=" + value + "; Max-Age=" + maxAge + "; Path=/; HttpOnly; SameSite=Lax" + secure);
    }

    private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        setRefreshCookie("", request, response, 0);
    }

    private boolean isSecure(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return "";
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return "";
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

    private Set<String> defaultPermissions(String role) {
        Set<String> p = new HashSet<String>();
        Collections.addAll(p, "activity:view", "venue:view", "gameMode:view", "user:view", "role:view", "permission:view", "attendance:view", "launcher:view", "systemImage:view");
        if ("activity_admin".equals(role)) {
            Collections.addAll(p, "activity:create", "activity:update", "activity:delete", "activity:cancel", "activity:restore", "plan:create", "plan:update", "plan:delete");
        }
        if ("attendance_admin".equals(role)) {
            Collections.addAll(p, "attendance:create", "attendance:update", "attendance:delete", "attendance:export");
        }
        if ("escape_admin".equals(role)) {
            Collections.addAll(p, "escape:view", "escape:match", "escape:config",
                    "escape:assets", "escape:settle", "escape:audit",
                    "escape:match:view", "escape:match:create", "escape:match:update", "escape:match:delete",
                    "escape:match:start", "escape:match:settle",
                    "escape:item:view", "escape:item:create", "escape:item:update", "escape:item:delete",
                    "escape:shop:view", "escape:shop:create", "escape:shop:update", "escape:shop:delete",
                    "escape:season:view", "escape:season:create", "escape:season:update", "escape:season:delete",
                    "escape:class:view", "escape:class:create", "escape:class:update", "escape:class:delete",
                    "escape:weapon:view", "escape:weapon:create", "escape:weapon:update", "escape:weapon:delete",
                    "escape:userAsset:view", "escape:userAsset:adjust", "escape:itemGrant:create");
        }
        if ("training_admin".equals(role)) Collections.addAll(p, "training:view", "training:update", "training:delete");
        return p;
    }

    private Set<String> allPermissions() {
        Set<String> p = defaultPermissions("admin");
        Collections.addAll(p,
                "activity:create", "activity:update", "activity:delete", "activity:cancel", "activity:restore", "activity:export",
                "plan:create", "plan:update", "plan:delete",
                "venue:create", "venue:update", "venue:delete", "gameMode:create", "gameMode:update", "gameMode:delete",
                "user:update", "user:delete", "user:disable", "user:resetPassword", "role:update", "permission:update",
                "attendance:create", "attendance:update", "attendance:delete", "attendance:export",
                "launcher:create", "launcher:update", "launcher:delete", "systemImage:update",
                "escape:view", "escape:match", "escape:config", "escape:assets", "escape:settle", "escape:audit",
                "escape:match:view", "escape:match:create", "escape:match:update", "escape:match:delete",
                "escape:match:start", "escape:match:settle",
                "escape:item:view", "escape:item:create", "escape:item:update", "escape:item:delete",
                "escape:shop:view", "escape:shop:create", "escape:shop:update", "escape:shop:delete",
                "escape:season:view", "escape:season:create", "escape:season:update", "escape:season:delete",
                "escape:class:view", "escape:class:create", "escape:class:update", "escape:class:delete",
                "escape:weapon:view", "escape:weapon:create", "escape:weapon:update", "escape:weapon:delete",
                "escape:userAsset:view", "escape:userAsset:adjust", "escape:itemGrant:create");
        Collections.addAll(p, "training:view", "training:update", "training:delete");
        return p;
    }

    public static String inviteCode(Map<String, Object> user) {
        try {
            String s = user.get("id") + ":" + user.get("username") + ":nbys";
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 4; i++) b.append(String.format("%02X", d[i]));
            return b.toString();
        } catch (Exception e) {
            return String.valueOf(user.get("id"));
        }
    }
}
