package com.nbys.escape.service;

import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Service
public class EscapeAccessService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public EscapeAccessService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    public UserContext requireUser(HttpServletRequest request) {
        Map<String, Object> user = auth.current(request);
        int userId = ((Number) user.get("id")).intValue();
        String role = String.valueOf(user.get("role"));
        boolean platformAdmin = "admin".equals(role) || "superadmin".equals(role) || "escape_admin".equals(role);
        boolean regularMember = truthy(user.get("is_regular_member"));
        if (!platformAdmin && !regularMember && Rows.one(jdbc,
                "select user_id from escape_user_access where user_id=? and enabled=1", userId) == null) {
            throw new SecurityException("尚未获得逃离西撇镇访问权限");
        }
        return new UserContext(userId, role, displayName(user), platformAdmin);
    }

    /**
     * 后台接口统一从这里做平台登录态和细粒度权限校验，避免 Controller
     * 通过角色名自行判断而造成权限规则分叉。
     */
    public UserContext requireAdmin(HttpServletRequest request, String permission) {
        auth.require(request, permission);
        Map<String, Object> user = auth.current(request);
        int userId = ((Number) user.get("id")).intValue();
        return new UserContext(userId, String.valueOf(user.get("role")), displayName(user), true);
    }

    /**
     * 现场战局控制只允许战局发起人和超级管理员。普通管理员不会因为后台角色
     * 自动获得其他发起人战局的开局、结算权限。
     */
    public UserContext requireMatchController(HttpServletRequest request, long matchId) {
        UserContext me = requireUser(request);
        Map<String, Object> match = Rows.one(jdbc,
                "select created_by from escape_matches where id=?", matchId);
        if (match == null) throw new IllegalArgumentException("战局不存在");
        if (!canControlMatch(me, ((Number) match.get("created_by")).intValue())) {
            throw new SecurityException("仅战局发起人或超级管理员可以控制该战局");
        }
        return me;
    }

    public UserContext requireMatchManager(HttpServletRequest request) {
        UserContext me = requireUser(request);
        if (!canManageMatches(me)) {
            throw new SecurityException("仅活动发起人或超级管理员可以管理对局");
        }
        return me;
    }

    public boolean canManageMatches(UserContext me) {
        if ("superadmin".equals(me.role)) return true;
        boolean activityCreator = Rows.one(jdbc,
                "select id from activities where created_by_id=? limit 1", me.userId) != null;
        boolean matchOwner = Rows.one(jdbc,
                "select id from escape_matches where created_by=? limit 1", me.userId) != null;
        return canManageMatches(me, activityCreator, matchOwner);
    }

    static boolean canManageMatches(UserContext me, boolean activityCreator, boolean matchOwner) {
        return "superadmin".equals(me.role) || activityCreator || matchOwner;
    }

    static boolean canControlMatch(UserContext me, int createdBy) {
        return me.userId == createdBy || "superadmin".equals(me.role);
    }

    private String displayName(Map<String, Object> user) {
        Object callsign = user.get("callsign");
        if (callsign != null && !String.valueOf(callsign).trim().isEmpty()) return String.valueOf(callsign);
        return String.valueOf(user.get("username"));
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    public static final class UserContext {
        public final int userId;
        public final String role;
        public final String displayName;
        public final boolean administrator;

        public UserContext(int userId, String role, String displayName, boolean administrator) {
            this.userId = userId;
            this.role = role;
            this.displayName = displayName;
            this.administrator = administrator;
        }
    }
}
