package com.nbys.escape.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.escape.service.EscapeAccessService;
import com.nbys.escape.service.EscapeAdminService;
import com.nbys.escape.service.EscapeH5Service;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/escape/h5")
public class EscapeH5Controller {
    private final EscapeAccessService access;
    private final EscapeH5Service service;
    private final EscapeAdminService adminService;

    public EscapeH5Controller(EscapeAccessService access, EscapeH5Service service,
                              EscapeAdminService adminService) {
        this.access = access;
        this.service = service;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        Map<String, Object> result = new LinkedHashMap<String, Object>(service.dashboard(me));
        result.put("role", me.role);
        result.put("can_manage_matches", access.canManageMatches(me));
        return ApiResponse.ok(result);
    }

    @GetMapping("/managed-matches")
    public ApiResponse<List<Map<String, Object>>> managedMatches(HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchManager(request);
        return ApiResponse.ok(adminService.managedMatches(actor));
    }

    @GetMapping("/managed-matches/{matchId}")
    public ApiResponse<Map<String, Object>> managedMatch(@PathVariable long matchId,
                                                         HttpServletRequest request) {
        access.requireMatchController(request, matchId);
        return ApiResponse.ok(adminService.match(matchId));
    }

    @GetMapping("/managed-matches/options")
    public ApiResponse<Map<String, Object>> matchOptions(HttpServletRequest request) {
        access.requireMatchManager(request);
        return ApiResponse.ok(adminService.matchOptions());
    }

    @PostMapping("/managed-matches")
    public ApiResponse<Map<String, Object>> createManagedMatch(@RequestBody Map<String, Object> body,
                                                               HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchManager(request);
        return ApiResponse.ok(adminService.createMatch(body, actor));
    }

    @PutMapping("/managed-matches/{matchId}")
    public ApiResponse<Map<String, Object>> updateManagedMatch(@PathVariable long matchId,
                                                               @RequestBody Map<String, Object> body,
                                                               HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchController(request, matchId);
        return ApiResponse.ok(adminService.updateMatch(matchId, body, actor));
    }

    @DeleteMapping("/managed-matches/{matchId}")
    public ApiResponse<Void> cancelManagedMatch(@PathVariable long matchId, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchController(request, matchId);
        adminService.cancelMatch(matchId, actor);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/managed-matches/{matchId}/permanent")
    public ApiResponse<Void> deleteManagedMatch(@PathVariable long matchId, HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchController(request, matchId);
        adminService.deleteCancelledMatch(matchId, actor);
        return ApiResponse.ok(null);
    }

    @GetMapping("/matches")
    public ApiResponse<List<Map<String, Object>>> matches(HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.matches(me));
    }

    @GetMapping("/matches/{matchId}")
    public ApiResponse<Map<String, Object>> matchDetail(@PathVariable long matchId, HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.matchDetail(matchId, me));
    }

    @PostMapping("/matches/{matchId}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable long matchId, HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.joinMatch(matchId, me.userId));
    }

    @PutMapping("/matches/{matchId}/loadout")
    public ApiResponse<Map<String, Object>> saveLoadout(@PathVariable long matchId,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.saveLoadout(matchId, me.userId, body));
    }

    @PostMapping("/matches/{matchId}/loadout/lock")
    public ApiResponse<Map<String, Object>> lockLoadout(@PathVariable long matchId,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.lockLoadout(matchId, me.userId, idempotencyKey(idempotencyKey)));
    }

    @GetMapping("/matches/{matchId}/control")
    public ApiResponse<Map<String, Object>> control(@PathVariable long matchId,
                                                    HttpServletRequest request) {
        access.requireMatchController(request, matchId);
        return ApiResponse.ok(adminService.match(matchId));
    }

    @PostMapping("/matches/{matchId}/control/start")
    public ApiResponse<Map<String, Object>> startMatch(
            @PathVariable long matchId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchController(request, matchId);
        return ApiResponse.ok(adminService.startMatch(
                matchId, idempotencyKey(idempotencyKey), actor));
    }

    @GetMapping("/matches/{matchId}/control/settlement")
    public ApiResponse<Map<String, Object>> settlement(@PathVariable long matchId,
                                                       HttpServletRequest request) {
        access.requireMatchController(request, matchId);
        return ApiResponse.ok(adminService.settlement(matchId));
    }

    @PostMapping("/matches/{matchId}/control/settle")
    public ApiResponse<Map<String, Object>> settleMatch(
            @PathVariable long matchId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        EscapeAccessService.UserContext actor = access.requireMatchController(request, matchId);
        return ApiResponse.ok(adminService.settleMatch(
                matchId, body, idempotencyKey(idempotencyKey), actor));
    }

    @GetMapping("/warehouses/{type}")
    public ApiResponse<Map<String, Object>> warehouse(@PathVariable String type, HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.warehouse(me.userId, type));
    }

    @PostMapping("/inventory/{inventoryId}/move")
    public ApiResponse<Map<String, Object>> move(@PathVariable long inventoryId,
                                                 @RequestBody Map<String, Object> body,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.moveItem(inventoryId, me.userId, body, idempotencyKey(idempotencyKey)));
    }

    @PostMapping("/inventory/{inventoryId}/sell")
    public ApiResponse<Map<String, Object>> sell(@PathVariable long inventoryId,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.sellItem(inventoryId, me.userId, idempotencyKey(idempotencyKey)));
    }

    @PostMapping("/inventory/sell-all")
    public ApiResponse<Map<String, Object>> sellAll(@RequestBody Map<String, Object> body,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        String warehouse = body.get("warehouse_type") == null ? "buffer" : String.valueOf(body.get("warehouse_type"));
        return ApiResponse.ok(service.sellAll(me.userId, warehouse, idempotencyKey(idempotencyKey)));
    }

    @GetMapping("/shop/products")
    public ApiResponse<List<Map<String, Object>>> products(HttpServletRequest request) {
        access.requireUser(request);
        return ApiResponse.ok(service.products());
    }

    @PostMapping("/shop/products/{productId}/purchase")
    public ApiResponse<Map<String, Object>> purchase(@PathVariable int productId,
                                                     @RequestBody Map<String, Object> body,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        int quantity = parseQuantity(body.get("quantity"));
        return ApiResponse.ok(service.purchase(productId, me.userId, quantity, idempotencyKey(idempotencyKey)));
    }

    @GetMapping("/records")
    public ApiResponse<List<Map<String, Object>>> records(
            @RequestParam(value = "only_mine", defaultValue = "true") boolean onlyMine,
            HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.records(me.userId, me.administrator, onlyMine));
    }

    @GetMapping("/records/{matchId}")
    public ApiResponse<Map<String, Object>> recordDetail(@PathVariable long matchId, HttpServletRequest request) {
        EscapeAccessService.UserContext me = access.requireUser(request);
        return ApiResponse.ok(service.recordDetail(matchId, me));
    }

    static String idempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty() || key.length() > 80) throw new IllegalArgumentException("Idempotency-Key不能为空且不能超过80字符");
        return key;
    }

    static int parseQuantity(Object value) {
        try {
            int quantity = Integer.parseInt(String.valueOf(value));
            if (quantity <= 0 || quantity > 99) throw new NumberFormatException();
            return quantity;
        } catch (Exception e) {
            throw new IllegalArgumentException("购买数量必须在1到99之间");
        }
    }
}
