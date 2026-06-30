package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@RestController
public class AdminSystemController {
    private static final List<String> HOMEPAGE_SECTIONS = Arrays.asList(
            "top", "about", "records", "fields", "activities", "safe", "media", "cooperate");
    private static final Set<String> HOMEPAGE_SECTION_SET = new HashSet<String>(HOMEPAGE_SECTIONS);
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

    @GetMapping("/api/public/system-settings/homepage-carousels")
    public ApiResponse<Map<String, Object>> publicHomepageCarousels() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String section : HOMEPAGE_SECTIONS) {
            result.put(section, Rows.list(jdbc,
                    "select id,image_url,sort_order from homepage_carousel_images where section_key=? and active=1 order by sort_order,id",
                    section));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/api/admin/system/homepage-carousels")
    public ApiResponse<List<Map<String, Object>>> adminHomepageCarousels(HttpServletRequest req) {
        auth.require(req, "systemImage:view");
        return ApiResponse.ok(Rows.list(jdbc,
                "select id,section_key,image_url,sort_order,active,created_at,updated_at from homepage_carousel_images order by section_key,sort_order,id"));
    }

    @PostMapping("/api/admin/system/homepage-carousels")
    @Transactional
    public ApiResponse<Void> createHomepageCarousel(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "systemImage:update");
        String section = homepageSection(body.get("section_key"));
        String imageUrl = requiredImageUrl(body.get("image_url"));
        Integer maxOrder = jdbc.queryForObject(
                "select coalesce(max(sort_order),0) from homepage_carousel_images where section_key=?", Integer.class, section);
        int sortOrder = body.get("sort_order") == null ? (maxOrder == null ? 10 : maxOrder + 10) : intValue(body.get("sort_order"));
        jdbc.update("insert into homepage_carousel_images(section_key,image_url,sort_order,active,created_at,updated_at) values(?,?,?,?,now(),now())",
                section, imageUrl, sortOrder, bool(body.get("active")) ? 1 : 0);
        ensureActiveImage(section);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/system/homepage-carousels/{id}")
    @Transactional
    public ApiResponse<Void> updateHomepageCarousel(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "systemImage:update");
        Map<String, Object> current = carouselImage(id);
        if (current == null) throw new IllegalArgumentException("轮播图片不存在");
        String section = homepageSection(body.get("section_key") == null ? current.get("section_key") : body.get("section_key"));
        String imageUrl = body.get("image_url") == null ? text(current.get("image_url")) : requiredImageUrl(body.get("image_url"));
        int sortOrder = body.get("sort_order") == null ? intValue(current.get("sort_order")) : intValue(body.get("sort_order"));
        boolean active = body.get("active") == null ? bool(current.get("active")) : bool(body.get("active"));
        String oldSection = text(current.get("section_key"));
        jdbc.update("update homepage_carousel_images set section_key=?,image_url=?,sort_order=?,active=?,updated_at=now() where id=?",
                section, imageUrl, sortOrder, active ? 1 : 0, id);
        ensureActiveImage(oldSection);
        if (!oldSection.equals(section)) ensureActiveImage(section);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/system/homepage-carousels/{id}")
    @Transactional
    public ApiResponse<Void> deleteHomepageCarousel(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "systemImage:update");
        Map<String, Object> current = carouselImage(id);
        if (current == null) throw new IllegalArgumentException("轮播图片不存在");
        String section = text(current.get("section_key"));
        if (bool(current.get("active")) && activeImageCount(section) <= 1) {
            throw new IllegalArgumentException("每个板块至少保留一张启用图片");
        }
        jdbc.update("delete from homepage_carousel_images where id=?", id);
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

    private Map<String, Object> carouselImage(int id) {
        return Rows.one(jdbc, "select * from homepage_carousel_images where id=?", id);
    }

    private String homepageSection(Object value) {
        String section = text(value);
        if (!HOMEPAGE_SECTION_SET.contains(section)) throw new IllegalArgumentException("首页板块无效");
        return section;
    }

    private String requiredImageUrl(Object value) {
        String imageUrl = text(value);
        if (imageUrl.isEmpty()) throw new IllegalArgumentException("请上传轮播图片");
        if (imageUrl.length() > 500) throw new IllegalArgumentException("图片地址过长");
        return imageUrl;
    }

    private int activeImageCount(String section) {
        Integer count = jdbc.queryForObject(
                "select count(*) from homepage_carousel_images where section_key=? and active=1", Integer.class, section);
        return count == null ? 0 : count;
    }

    private void ensureActiveImage(String section) {
        if (activeImageCount(section) == 0) throw new IllegalArgumentException("每个板块至少保留一张启用图片");
    }

    private int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return 0;
        }
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
