package com.nbys.escape.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.escape.service.EscapeAccessService;
import com.nbys.escape.service.EscapeAdminService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/escape/admin")
public class EscapeAdminController {
    private final EscapeAccessService access;
    private final EscapeAdminService service;

    public EscapeAdminController(EscapeAccessService access, EscapeAdminService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(HttpServletRequest request) {
        access.requireAdmin(request, "escape:view");
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/matches")
    public ApiResponse<List<Map<String, Object>>> matches(
            @RequestParam(value = "status", required = false) String status, HttpServletRequest request) {
        access.requireAdmin(request, "escape:match:view");
        return ApiResponse.ok(service.matches(status));
    }

    @GetMapping("/matches/{id}")
    public ApiResponse<Map<String, Object>> match(@PathVariable long id, HttpServletRequest request) {
        access.requireAdmin(request, "escape:match:view");
        return ApiResponse.ok(service.match(id));
    }

    @PostMapping("/matches")
    public ApiResponse<Map<String, Object>> createMatch(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:match:create");
        return ApiResponse.ok(service.createMatch(body, actor));
    }

    @PutMapping("/matches/{id}")
    public ApiResponse<Map<String, Object>> updateMatch(@PathVariable long id,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:match:update");
        return ApiResponse.ok(service.updateMatch(id, body, actor));
    }

    @DeleteMapping("/matches/{id}")
    public ApiResponse<Void> cancelMatch(@PathVariable long id, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:match:delete");
        service.cancelMatch(id, actor);
        return ApiResponse.ok(null);
    }

    @PostMapping("/matches/{id}/start")
    public ApiResponse<Map<String, Object>> startMatch(
            @PathVariable long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:match:start");
        return ApiResponse.ok(service.startMatch(id, idempotencyKey(key), actor));
    }

    /**
     * 结算预览返回完整 participants，管理端据此补齐每个人的 escaped/kills/manual_cash/items。
     */
    @GetMapping("/matches/{id}/settlement")
    public ApiResponse<Map<String, Object>> settlement(@PathVariable long id, HttpServletRequest request) {
        access.requireAdmin(request, "escape:match:settle");
        return ApiResponse.ok(service.settlement(id));
    }

    @PostMapping("/matches/{id}/settle")
    public ApiResponse<Map<String, Object>> settleMatch(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:match:settle");
        return ApiResponse.ok(service.settleMatch(id, body, idempotencyKey(key), actor));
    }

    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> items(HttpServletRequest request) {
        return listCatalog("items", "escape:item:view", request);
    }

    @GetMapping("/items/options")
    public ApiResponse<List<Map<String, Object>>> itemOptions(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "include_id", required = false) Integer includeId,
            HttpServletRequest request) {
        access.requireAdmin(request, "escape:item:view");
        return ApiResponse.ok(service.itemOptions(category, includeId));
    }

    @PostMapping("/items")
    public ApiResponse<Map<String, Object>> createItem(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createCatalog("items", "escape:item:create", body, request);
    }

    @PutMapping("/items/{id}")
    public ApiResponse<Map<String, Object>> updateItem(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                       HttpServletRequest request) {
        return updateCatalog("items", "escape:item:update", id, body, request);
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable long id, HttpServletRequest request) {
        return deleteCatalog("items", "escape:item:delete", id, request);
    }

    @GetMapping("/shop-products")
    public ApiResponse<List<Map<String, Object>>> products(HttpServletRequest request) {
        return listCatalog("products", "escape:shop:view", request);
    }

    @PostMapping("/shop-products")
    public ApiResponse<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createCatalog("products", "escape:shop:create", body, request);
    }

    @PutMapping("/shop-products/{id}")
    public ApiResponse<Map<String, Object>> updateProduct(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        return updateCatalog("products", "escape:shop:update", id, body, request);
    }

    @DeleteMapping("/shop-products/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable long id, HttpServletRequest request) {
        return deleteCatalog("products", "escape:shop:delete", id, request);
    }

    @GetMapping("/seasons")
    public ApiResponse<List<Map<String, Object>>> seasons(HttpServletRequest request) {
        return listCatalog("seasons", "escape:season:view", request);
    }

    @PostMapping("/seasons")
    public ApiResponse<Map<String, Object>> createSeason(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createCatalog("seasons", "escape:season:create", body, request);
    }

    @PutMapping("/seasons/{id}")
    public ApiResponse<Map<String, Object>> updateSeason(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        return updateCatalog("seasons", "escape:season:update", id, body, request);
    }

    @DeleteMapping("/seasons/{id}")
    public ApiResponse<Void> deleteSeason(@PathVariable long id, HttpServletRequest request) {
        return deleteCatalog("seasons", "escape:season:delete", id, request);
    }

    @PostMapping("/seasons/{id}/enable")
    public ApiResponse<Map<String, Object>> enableSeason(@PathVariable long id, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:season:update");
        return ApiResponse.ok(service.enableSeason(id, actor));
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> professions(HttpServletRequest request) {
        return listCatalog("professions", "escape:class:view", request);
    }

    @PostMapping("/classes")
    public ApiResponse<Map<String, Object>> createProfession(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createCatalog("professions", "escape:class:create", body, request);
    }

    @PutMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> updateProfession(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                             HttpServletRequest request) {
        return updateCatalog("professions", "escape:class:update", id, body, request);
    }

    @DeleteMapping("/classes/{id}")
    public ApiResponse<Void> deleteProfession(@PathVariable long id, HttpServletRequest request) {
        return deleteCatalog("professions", "escape:class:delete", id, request);
    }

    @GetMapping("/weapons")
    public ApiResponse<List<Map<String, Object>>> weapons(HttpServletRequest request) {
        return listCatalog("weapons", "escape:weapon:view", request);
    }

    @PostMapping("/weapons")
    public ApiResponse<Map<String, Object>> createWeapon(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createCatalog("weapons", "escape:weapon:create", body, request);
    }

    @PutMapping("/weapons/{id}")
    public ApiResponse<Map<String, Object>> updateWeapon(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        return updateCatalog("weapons", "escape:weapon:update", id, body, request);
    }

    @DeleteMapping("/weapons/{id}")
    public ApiResponse<Void> deleteWeapon(@PathVariable long id, HttpServletRequest request) {
        return deleteCatalog("weapons", "escape:weapon:delete", id, request);
    }

    @GetMapping("/user-assets")
    public ApiResponse<List<Map<String, Object>>> userAssets(
            @RequestParam(value = "keyword", required = false) String keyword, HttpServletRequest request) {
        access.requireAdmin(request, "escape:userAsset:view");
        return ApiResponse.ok(service.userAssets(keyword));
    }

    @PostMapping("/user-assets/{userId}/adjust")
    public ApiResponse<Map<String, Object>> adjustAsset(
            @PathVariable int userId, @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:userAsset:adjust");
        return ApiResponse.ok(service.adjustAsset(userId, body, idempotencyKey(key), actor));
    }

    @GetMapping("/item-grants")
    public ApiResponse<List<Map<String, Object>>> itemGrants(HttpServletRequest request) {
        access.requireAdmin(request, "escape:itemGrant:create");
        return ApiResponse.ok(service.itemGrants());
    }

    @PostMapping("/item-grants")
    public ApiResponse<Map<String, Object>> grantItems(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, "escape:itemGrant:create");
        int userId = positiveInt(body.get("user_id"), "用户ID无效");
        return ApiResponse.ok(service.grantItems(userId, body, idempotencyKey(key), actor));
    }

    @GetMapping("/audit")
    public ApiResponse<List<Map<String, Object>>> audit(
            @RequestParam(value = "limit", defaultValue = "100") int limit, HttpServletRequest request) {
        access.requireAdmin(request, "escape:audit");
        return ApiResponse.ok(service.audit(limit));
    }

    private ApiResponse<List<Map<String, Object>>> listCatalog(String type, String permission,
                                                               HttpServletRequest request) {
        access.requireAdmin(request, permission);
        return ApiResponse.ok(service.catalog(type, request.getParameter("keyword"),
                request.getParameter("rarity")));
    }

    private ApiResponse<Map<String, Object>> createCatalog(String type, String permission, Map<String, Object> body,
                                                           HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, permission);
        return ApiResponse.ok("products".equals(type)
                ? service.createProduct(body, actor) : service.createCatalog(type, body, actor));
    }

    private ApiResponse<Map<String, Object>> updateCatalog(String type, String permission, long id,
                                                           Map<String, Object> body, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, permission);
        return ApiResponse.ok("products".equals(type)
                ? service.updateProduct(id, body, actor) : service.updateCatalog(type, id, body, actor));
    }

    private ApiResponse<Void> deleteCatalog(String type, String permission, long id, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireAdmin(request, permission);
        service.deleteCatalog(type, id, actor);
        return ApiResponse.ok(null);
    }

    static String idempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.length() > 80) throw new IllegalArgumentException("Idempotency-Key不能超过80字符");
        // 兼容第一版管理端；客户端传入时可获得跨请求幂等，未传时仍由领域唯一约束防止重复结算。
        return key.isEmpty() ? UUID.randomUUID().toString() : key;
    }

    static int positiveInt(Object value, String message) {
        try {
            int number = Integer.parseInt(String.valueOf(value));
            if (number <= 0) throw new NumberFormatException();
            return number;
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }
}
