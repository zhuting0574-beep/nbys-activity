package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class PermissionController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public PermissionController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/permissions/pages")
    public ApiResponse<List<Map<String, Object>>> pages() {
        List<Map<String, Object>> pages = new ArrayList<Map<String, Object>>();
        add(pages, "activity", "活动管理", "view,create,update,delete,cancel,restore");
        add(pages, "venue", "场地管理", "view,create,update,delete");
        add(pages, "gameMode", "模式管理", "view,create,update,delete");
        add(pages, "user", "用户管理", "view,update,delete,disable,resetPassword");
        add(pages, "launcher", "发射器管理", "view,update,delete");
        add(pages, "attendance", "出勤统计", "view,create,update,delete,export");
        add(pages, "systemImage", "图片管理", "view,update");
        add(pages, "extraction", "逃离管理", "view,item,shop,season,match,asset,reset,manage");
        add(pages, "permission", "权限管理", "view,update");
        return ApiResponse.ok(pages);
    }

    @GetMapping("/roles/options")
    public ApiResponse<List<Map<String, Object>>> roles() {
        List<Map<String, Object>> roles = new ArrayList<Map<String, Object>>();
        role(roles, "guest", "游客");
        role(roles, "user", "普通用户");
        role(roles, "activity_admin", "活动管理员");
        role(roles, "attendance_admin", "出勤管理员");
        role(roles, "superadmin", "超级管理员");
        return ApiResponse.ok(roles);
    }

    @GetMapping("/roles/{role}/permissions")
    public ApiResponse<Set<String>> rolePermissions(@PathVariable String role) {
        return ApiResponse.ok(auth.permissions(role));
    }

    @PutMapping("/roles/{role}/permissions")
    public ApiResponse<Void> saveRolePermissions(@PathVariable String role, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "permission:update");
        if ("superadmin".equals(role)) throw new IllegalArgumentException("超级管理员权限不允许修改");
        jdbc.update("delete from role_permissions where role=?", role);
        Object permissions = body.get("permissions");
        if (permissions instanceof List) {
            for (Object p : (List<?>) permissions) jdbc.update("insert into role_permissions(role,permission_code,created_at) values(?,?,now())", role, p);
        }
        return ApiResponse.ok(null);
    }

    private void add(List<Map<String, Object>> pages, String key, String name, String actions) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("key", key);
        row.put("name", name);
        row.put("actions", Arrays.asList(actions.split(",")));
        pages.add(row);
    }

    private void role(List<Map<String, Object>> roles, String key, String name) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("value", key);
        row.put("label", name);
        roles.add(row);
    }
}
