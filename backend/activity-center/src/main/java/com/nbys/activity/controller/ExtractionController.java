package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
public class ExtractionController {
    private static final int BUFFER_ROWS = 16;
    private static final int BUFFER_COLS = 48;
    private static final int STORAGE_ROWS = 8;
    private static final int STORAGE_COLS = 12;

    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final Random random = new Random();

    public ExtractionController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/api/h5/extraction/overview")
    public ApiResponse<Map<String, Object>> h5Overview(HttpServletRequest req) {
        Map<String, Object> me = requireExtraction(req);
        syncRuntime();
        int userId = userIdOf(me);
        Map<String, Object> profile = profile(userId);
        normalize(userId, "storage", intValue(profile.get("storage_rows"), STORAGE_ROWS), intValue(profile.get("storage_cols"), STORAGE_COLS));
        normalize(userId, "buffer", BUFFER_ROWS, BUFFER_COLS);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("profile", profile);
        out.put("season", currentSeason());
        out.put("stats", userStats(userId, currentSeason()));
        out.put("buffer", inventory(userId, "buffer"));
        out.put("storage", inventory(userId, "storage"));
        out.put("buffer_rows", BUFFER_ROWS);
        out.put("buffer_cols", BUFFER_COLS);
        out.put("storage_rows", intValue(profile.get("storage_rows"), STORAGE_ROWS));
        out.put("storage_cols", intValue(profile.get("storage_cols"), STORAGE_COLS));
        return ApiResponse.ok(out);
    }

    @PostMapping("/api/h5/extraction/inventory/{itemId}/place")
    @Transactional
    public ApiResponse<Map<String, Object>> placeItem(@PathVariable int itemId, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        Map<String, Object> item = Rows.one(jdbc, itemSql("where i.id=? and i.user_id=? and i.sold_at is null"), itemId, userId);
        if (item == null) throw new IllegalArgumentException("物品不存在");
        String fromLocation = text(item.get("location"));
        String location = text(body.get("location"));
        int row = intValue(body.get("row"), 1);
        int col = intValue(body.get("col"), 1);
        if (!"buffer".equals(location) && !"storage".equals(location)) throw new IllegalArgumentException("仓库位置无效");
        if ("storage".equals(fromLocation) && "buffer".equals(location)) throw new IllegalArgumentException("个人仓库物品不能放回缓冲区");
        Map<String, Object> profile = profile(userId);
        int rows = "buffer".equals(location) ? BUFFER_ROWS : intValue(profile.get("storage_rows"), STORAGE_ROWS);
        int cols = "buffer".equals(location) ? BUFFER_COLS : intValue(profile.get("storage_cols"), STORAGE_COLS);
        int w = intValue(item.get("width"), 1);
        int h = intValue(item.get("height"), 1);
        if (!inside(rows, cols, w, h, row, col)) throw new IllegalArgumentException("目标位置超出仓库范围");

        Map<String, Object> target = null;
        if (isMoney(item)) target = Rows.one(jdbc,
                "select i.*,d.item_category from extraction_inventory_items i join extraction_item_defs d on d.id=i.item_def_id " +
                        "where i.user_id=? and i.sold_at is null and i.location=? and i.`row`=? and i.`col`=? and i.item_def_id=? and i.id<>? limit 1",
                userId, location, row, col, item.get("item_def_id"), itemId);
        if (target != null) {
            int qty = intValue(target.get("quantity"), 1) + intValue(item.get("quantity"), 1);
            jdbc.update("update extraction_inventory_items set quantity=? where id=?", qty, target.get("id"));
            jdbc.update("delete from extraction_inventory_items where id=?", itemId);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("stacked", true);
            out.put("target_id", target.get("id"));
            out.put("quantity", qty);
            out.put("message", "钱币已叠加");
            return ApiResponse.ok(out);
        }
        if (!canPlace(userId, location, rows, cols, w, h, row, col, itemId)) throw new IllegalArgumentException("目标位置空间不足或发生重叠");
        jdbc.update("update extraction_inventory_items set location=?,`row`=?,`col`=? where id=? and user_id=?", location, row, col, itemId, userId);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("stacked", false);
        out.put("row", row);
        out.put("col", col);
        out.put("message", "已移动");
        return ApiResponse.ok(out);
    }

    @PostMapping("/api/h5/extraction/inventory/sell")
    @Transactional
    public ApiResponse<Map<String, Object>> sellItems(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        List<Integer> ids = intList(body.get("item_ids"));
        int total = 0;
        int count = 0;
        for (Integer itemId : ids) {
            Map<String, Object> item = Rows.one(jdbc, itemSql("where i.id=? and i.user_id=? and i.sold_at is null"), itemId, userId);
            if (item == null) continue;
            int price = todayPrice(item);
            int amount = price * intValue(item.get("quantity"), 1);
            jdbc.update("update extraction_inventory_items set sold_at=now(),sold_price=? where id=?", amount, itemId);
            total += amount;
            count++;
        }
        if (total > 0) jdbc.update("update extraction_user_profiles set cash=cash+?,updated_at=now() where user_id=?", total, userId);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("count", count);
        out.put("cash", total);
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/h5/extraction/shop")
    public ApiResponse<List<Map<String, Object>>> h5Shop(HttpServletRequest req) {
        requireExtraction(req);
        syncRuntime();
        return ApiResponse.ok(Rows.list(jdbc,
                "select * from extraction_shop_items where active=1 and (shelf_until is null or shelf_until>=curdate()) order by created_at desc,id desc"));
    }

    @PostMapping("/api/h5/extraction/shop/{shopId}/buy")
    @Transactional
    public ApiResponse<Void> buy(@PathVariable int shopId, HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        Map<String, Object> shop = Rows.one(jdbc, "select * from extraction_shop_items where id=? for update", shopId);
        Map<String, Object> profile = profile(userId);
        if (shop == null || !bool(shop.get("active")) || intValue(shop.get("stock"), 0) <= 0) throw new IllegalArgumentException("商品不可购买");
        if (shop.get("shelf_until") != null && LocalDate.parse(String.valueOf(shop.get("shelf_until"))).isBefore(LocalDate.now())) throw new IllegalArgumentException("商品已下架");
        int price = intValue(shop.get("price"), 0);
        if (intValue(profile.get("cash"), 0) < price) throw new IllegalArgumentException("甬士币不足");
        jdbc.update("update extraction_user_profiles set cash=cash-?,updated_at=now() where user_id=?", price, userId);
        jdbc.update("update extraction_shop_items set stock=stock-1 where id=?", shopId);
        String category = text(shop.get("category"));
        if ("storage".equals(category)) {
            jdbc.update("update extraction_user_profiles set storage_rows=greatest(storage_rows,?),storage_cols=greatest(storage_cols,?),updated_at=now() where user_id=?",
                    intValue(shop.get("storage_rows"), STORAGE_ROWS), intValue(shop.get("storage_cols"), STORAGE_COLS), userId);
        } else {
            int itemDefId = intValue(shop.get("item_def_id"), 0);
            if (itemDefId == 0) itemDefId = ensureItemDef(text(shop.get("name")), shopCategory(category), Math.max(1, price));
            addInventory(userId, itemDefId, 1, currentSeasonId(), "buffer", null);
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/h5/extraction/matches")
    public ApiResponse<Map<String, Object>> h5Matches(HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        syncRuntime();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("active", Rows.list(jdbc,
                "select m.*, (select count(*) from extraction_match_participants p where p.match_id=m.id) participant_count, " +
                        "(select p.id from extraction_match_participants p where p.match_id=m.id and p.user_id=?) my_participant_id " +
                        "from extraction_matches m where m.status in ('preparing','started') order by m.created_at desc", userId));
        out.put("records", Rows.list(jdbc,
                "select distinct m.* from extraction_matches m join extraction_match_participants p on p.match_id=m.id " +
                        "where m.status='ended' and p.user_id=? order by m.ended_at desc,m.id desc limit 30", userId));
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/h5/extraction/matches/{matchId}")
    public ApiResponse<Map<String, Object>> h5MatchDetail(@PathVariable int matchId, HttpServletRequest req) {
        Map<String, Object> me = requireExtraction(req);
        return ApiResponse.ok(matchDetail(matchId, userIdOf(me), canManageExtraction(me)));
    }

    @PostMapping("/api/h5/extraction/matches/{matchId}/join")
    @Transactional
    public ApiResponse<Void> joinMatch(@PathVariable int matchId, HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        Map<String, Object> match = Rows.one(jdbc, "select * from extraction_matches where id=?", matchId);
        if (match == null || !"preparing".equals(text(match.get("status")))) throw new IllegalArgumentException("当前对局不能加入");
        try {
            jdbc.update("insert into extraction_match_participants(match_id,user_id,created_at,updated_at) values(?,?,now(),now())", matchId, userId);
        } catch (DuplicateKeyException ignored) {
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/h5/extraction/matches/{matchId}/configure")
    @Transactional
    public ApiResponse<Void> configureMatch(@PathVariable int matchId, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        Map<String, Object> match = Rows.one(jdbc, "select * from extraction_matches where id=?", matchId);
        Map<String, Object> p = Rows.one(jdbc, "select * from extraction_match_participants where match_id=? and user_id=?", matchId, userId);
        if (match == null || p == null || !"preparing".equals(text(match.get("status")))) throw new IllegalArgumentException("当前对局不能修改");
        if (bool(p.get("locked"))) throw new IllegalArgumentException("你已经锁定，不能修改");
        int squadNo = intValue(body.get("squad_no"), 0);
        int classRuleId = intValue(body.get("class_rule_id"), 0);
        String weaponChoice = text(body.get("weapon_choice"));
        if (squadNo < 1 || squadNo > intValue(match.get("squad_count"), 1)) throw new IllegalArgumentException("小队无效");
        int used = intValue(Rows.one(jdbc, "select count(*) c from extraction_match_participants where match_id=? and squad_no=? and id<>?", matchId, squadNo, p.get("id")).get("c"), 0);
        if (used >= intValue(match.get("squad_limit"), 5)) throw new IllegalArgumentException("该小队人数已满");
        Map<String, Object> cls = Rows.one(jdbc, "select * from extraction_class_rules where id=? and active=1", classRuleId);
        WeaponChoice weapon = parseWeaponChoice(userId, weaponChoice);
        if (cls == null || weapon.rule == null) throw new IllegalArgumentException("请选择有效兵种和武器");
        if ("跑刀仔".equals(text(cls.get("name"))) && !"knife".equals(text(weapon.rule.get("weapon_type")))) throw new IllegalArgumentException("跑刀仔只能选择刀");
        jdbc.update("update extraction_match_participants set squad_no=?,class_rule_id=?,weapon_rule_id=?,weapon_inventory_item_id=?,updated_at=now() where id=?",
                squadNo, classRuleId, weapon.rule.get("id"), weapon.inventoryId, p.get("id"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/h5/extraction/matches/{matchId}/lock")
    public ApiResponse<Void> lockMatch(@PathVariable int matchId, HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        int updated = jdbc.update("update extraction_match_participants set locked=1,updated_at=now() where match_id=? and user_id=? and squad_no is not null and class_rule_id is not null and weapon_rule_id is not null", matchId, userId);
        if (updated == 0) throw new IllegalArgumentException("请先选择小队、兵种和武器");
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/h5/extraction/options")
    public ApiResponse<Map<String, Object>> h5Options(HttpServletRequest req) {
        int userId = userIdOf(requireExtraction(req));
        ensureRules();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("classes", Rows.list(jdbc, "select * from extraction_class_rules where active=1 order by sort_order,id"));
        out.put("weapons", weaponChoices(userId));
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/admin/extraction/bootstrap")
    public ApiResponse<Map<String, Object>> adminBootstrap(HttpServletRequest req) {
        Map<String, Object> user = auth.current(req);
        if (!canAdmin(user, "extraction:view")) throw new SecurityException("没有操作权限");
        syncRuntime();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("items", canAdmin(user, "extraction:item") ? adminItemRows() : Collections.emptyList());
        out.put("shop", canAdmin(user, "extraction:shop") ? adminShopRows() : Collections.emptyList());
        out.put("seasons", canAdmin(user, "extraction:season") ? adminSeasonRows() : Collections.emptyList());
        out.put("classes", canAdmin(user, "extraction:manage") ? adminClassRows() : Collections.emptyList());
        out.put("weapons", canAdmin(user, "extraction:manage") ? adminWeaponRows() : Collections.emptyList());
        out.put("matches", canAdmin(user, "extraction:match") ? adminMatchRows() : Collections.emptyList());
        out.put("users", Rows.list(jdbc, "select id,username,callsign from users where disabled=0 order by callsign,username"));
        out.put("reset_records", canAdmin(user, "extraction:reset") ? Rows.list(jdbc, resetSql()) : Collections.emptyList());
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/admin/extraction/items")
    public ApiResponse<List<Map<String, Object>>> adminItems(HttpServletRequest req) {
        auth.require(req, "extraction:item");
        return ApiResponse.ok(adminItemRows());
    }

    @PostMapping("/api/admin/extraction/items")
    public ApiResponse<Void> createItem(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:item");
        jdbc.update("insert into extraction_item_defs(name,level,item_category,min_price,max_price,width,height,photo_filename,active,created_by_id,created_at) values(?,?,?,?,?,?,?,?,?,?,now())",
                required(body, "name"), level(body.get("level")), category(body.get("item_category")), positive(body.get("min_price"), 1), positive(body.get("max_price"), positive(body.get("min_price"), 1)),
                between(body.get("width"), 1, 30, 1), between(body.get("height"), 1, 12, 1), text(body.get("photo_filename")), boolObj(body.get("active"), true) ? 1 : 0, userIdOf(auth.current(req)));
        ensureRules();
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/extraction/items/{id}")
    public ApiResponse<Void> updateItem(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:item");
        int updated = jdbc.update("update extraction_item_defs set name=?,level=?,item_category=?,min_price=?,max_price=?,width=?,height=?,photo_filename=?,active=? where id=?",
                required(body, "name"), level(body.get("level")), category(body.get("item_category")), positive(body.get("min_price"), 1), positive(body.get("max_price"), positive(body.get("min_price"), 1)),
                between(body.get("width"), 1, 30, 1), between(body.get("height"), 1, 12, 1), text(body.get("photo_filename")), boolObj(body.get("active"), true) ? 1 : 0, id);
        if (updated == 0) throw new IllegalArgumentException("物品不存在");
        ensureRules();
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/extraction/items/{id}")
    @Transactional
    public ApiResponse<Void> deleteItem(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "extraction:item");
        jdbc.update("delete from extraction_inventory_items where item_def_id=?", id);
        jdbc.update("delete from extraction_item_prices where item_def_id=?", id);
        jdbc.update("update extraction_shop_items set item_def_id=null where item_def_id=?", id);
        jdbc.update("delete from extraction_weapon_rules where item_def_id=?", id);
        jdbc.update("delete from extraction_item_defs where id=?", id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/extraction/entry")
    public ApiResponse<Void> entryItem(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:item");
        int userId = intValue(body.get("user_id"), 0);
        int itemDefId = intValue(body.get("item_def_id"), 0);
        int qty = Math.max(1, intValue(body.get("quantity"), 1));
        Map<String, Object> item = Rows.one(jdbc, "select * from extraction_item_defs where id=?", itemDefId);
        if (Rows.one(jdbc, "select id from users where id=? and disabled=0", userId) == null || item == null) throw new IllegalArgumentException("用户或物品不存在");
        if (!isMoney(item)) qty = Math.min(qty, 10);
        addInventory(userId, itemDefId, qty, currentSeasonId(), "buffer", null);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/shop")
    public ApiResponse<List<Map<String, Object>>> adminShop(HttpServletRequest req) {
        auth.require(req, "extraction:shop");
        return ApiResponse.ok(adminShopRows());
    }

    @PostMapping("/api/admin/extraction/shop")
    public ApiResponse<Void> createShop(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:shop");
        saveShop(null, body);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/extraction/shop/{id}")
    public ApiResponse<Void> updateShop(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:shop");
        saveShop(id, body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/extraction/shop/{id}")
    public ApiResponse<Void> deleteShop(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "extraction:shop");
        jdbc.update("delete from extraction_shop_items where id=?", id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/seasons")
    public ApiResponse<List<Map<String, Object>>> adminSeasons(HttpServletRequest req) {
        auth.require(req, "extraction:season");
        return ApiResponse.ok(adminSeasonRows());
    }

    @PostMapping("/api/admin/extraction/seasons")
    public ApiResponse<Void> createSeason(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:season");
        jdbc.update("insert into extraction_seasons(name,start_date,end_date,kill_reward_cash,active,created_by_id,created_at) values(?,?,?,?,1,?,now())",
                required(body, "name"), required(body, "start_date"), required(body, "end_date"), Math.max(0, intValue(body.get("kill_reward_cash"), 0)), userIdOf(auth.current(req)));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/extraction/seasons/{id}")
    public ApiResponse<Void> updateSeason(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:season");
        jdbc.update("update extraction_seasons set name=?,start_date=?,end_date=?,kill_reward_cash=?,active=? where id=?",
                required(body, "name"), required(body, "start_date"), required(body, "end_date"), Math.max(0, intValue(body.get("kill_reward_cash"), 0)), boolObj(body.get("active"), true) ? 1 : 0, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/extraction/banner")
    public ApiResponse<Void> updateBanner(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:season");
        jdbc.update("insert into extraction_ui_settings(id,banner_filename,updated_at) values(1,?,now()) on duplicate key update banner_filename=values(banner_filename),updated_at=now()", text(body.get("banner_filename")));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/classes")
    public ApiResponse<List<Map<String, Object>>> adminClasses(HttpServletRequest req) {
        auth.require(req, "extraction:manage");
        ensureRules();
        return ApiResponse.ok(adminClassRows());
    }

    @PutMapping("/api/admin/extraction/classes")
    public ApiResponse<Void> updateClasses(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:manage");
        Object list = body.get("rules");
        if (list instanceof List) {
            for (Object o : (List<?>) list) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> r = (Map<?, ?>) o;
                jdbc.update("update extraction_class_rules set name=?,health=?,maintenance_fee=? where id=?",
                        required(r, "name"), Math.max(1, intValue(r.get("health"), 100)), Math.max(0, intValue(r.get("maintenance_fee"), 0)), r.get("id"));
            }
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/weapons")
    public ApiResponse<List<Map<String, Object>>> adminWeapons(HttpServletRequest req) {
        auth.require(req, "extraction:manage");
        ensureRules();
        return ApiResponse.ok(adminWeaponRows());
    }

    @PutMapping("/api/admin/extraction/weapons")
    public ApiResponse<Void> updateWeapons(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:manage");
        Object list = body.get("rules");
        if (list instanceof List) {
            for (Object o : (List<?>) list) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> r = (Map<?, ?>) o;
                jdbc.update("update extraction_weapon_rules set usage_fee=?,durability_cost_percent=? where id=?",
                        Math.max(0, intValue(r.get("usage_fee"), 0)), between(r.get("durability_cost_percent"), 0, 100, 0), r.get("id"));
            }
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/matches")
    public ApiResponse<List<Map<String, Object>>> adminMatches(HttpServletRequest req) {
        auth.require(req, "extraction:match");
        return ApiResponse.ok(adminMatchRows());
    }

    @PostMapping("/api/admin/extraction/matches")
    public ApiResponse<Void> createMatch(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:match");
        jdbc.update("insert into extraction_matches(name,venue_name,squad_count,squad_limit,status,created_by_id,created_at) values(?,?,?,?,'preparing',?,now())",
                required(body, "name"), text(body.get("venue_name")), between(body.get("squad_count"), 1, 20, 2), between(body.get("squad_limit"), 1, 50, 5), userIdOf(auth.current(req)));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/extraction/matches/{id}")
    public ApiResponse<Void> updateMatch(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:match");
        jdbc.update("update extraction_matches set name=?,venue_name=?,squad_count=?,squad_limit=? where id=? and status='preparing'",
                required(body, "name"), text(body.get("venue_name")), between(body.get("squad_count"), 1, 20, 2), between(body.get("squad_limit"), 1, 50, 5), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/matches/{matchId}")
    public ApiResponse<Map<String, Object>> adminMatchDetail(@PathVariable int matchId, HttpServletRequest req) {
        auth.require(req, "extraction:match");
        return ApiResponse.ok(matchDetail(matchId, userIdOf(auth.current(req)), true));
    }

    @PostMapping("/api/admin/extraction/matches/{matchId}/unlock/{participantId}")
    public ApiResponse<Void> unlockParticipant(@PathVariable int matchId, @PathVariable int participantId, HttpServletRequest req) {
        auth.require(req, "extraction:match");
        jdbc.update("update extraction_match_participants set locked=0 where id=? and match_id=? and exists(select 1 from extraction_matches m where m.id=? and m.status='preparing')", participantId, matchId, matchId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/extraction/matches/{matchId}/start")
    @Transactional
    public ApiResponse<Void> startMatch(@PathVariable int matchId, HttpServletRequest req) {
        auth.require(req, "extraction:match");
        Map<String, Object> match = Rows.one(jdbc, "select * from extraction_matches where id=?", matchId);
        if (match == null || !"preparing".equals(text(match.get("status")))) throw new IllegalArgumentException("当前对局不能开局");
        List<Map<String, Object>> ps = participants(matchId);
        if (ps.isEmpty()) throw new IllegalArgumentException("无人加入，不能开局");
        for (Map<String, Object> p : ps) {
            if (!bool(p.get("locked"))) throw new IllegalArgumentException("还有用户未锁定，不能开局");
            int cost = participantCost(p);
            Map<String, Object> profile = profile(intValue(p.get("user_id"), 0));
            if (intValue(profile.get("cash"), 0) < cost) throw new IllegalArgumentException(text(p.get("callsign")) + " 甬士币不足，不能开局");
            if ("special".equals(text(p.get("weapon_type")))) {
                Map<String, Object> inv = Rows.one(jdbc, itemSql("where i.id=? and i.user_id=? and i.location='storage' and i.sold_at is null"), p.get("weapon_inventory_item_id"), p.get("user_id"));
                if (inv == null) throw new IllegalArgumentException(text(p.get("callsign")) + " 的特殊武器已不可用");
            }
        }
        for (Map<String, Object> p : ps) {
            int cost = participantCost(p);
            jdbc.update("update extraction_user_profiles set cash=cash-?,updated_at=now() where user_id=?", cost, p.get("user_id"));
            jdbc.update("update extraction_match_participants set paid_cash=? where id=?", cost, p.get("id"));
            if ("special".equals(text(p.get("weapon_type"))) && p.get("weapon_inventory_item_id") != null) {
                jdbc.update("update extraction_inventory_items set location='in_match',`row`=null,`col`=null where id=?", p.get("weapon_inventory_item_id"));
            }
        }
        jdbc.update("update extraction_matches set status='started',started_at=now() where id=?", matchId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/extraction/matches/{matchId}/settle")
    @Transactional
    public ApiResponse<Void> settleMatch(@PathVariable int matchId, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:match");
        Map<String, Object> match = Rows.one(jdbc, "select * from extraction_matches where id=?", matchId);
        if (match == null || !"started".equals(text(match.get("status")))) throw new IllegalArgumentException("只有进行中的对局可以终局结算");
        int rewardUnit = currentSeason() == null ? 0 : intValue(currentSeason().get("kill_reward_cash"), 0);
        Object rows = body.get("participants");
        if (rows instanceof List) {
            for (Object o : (List<?>) rows) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> row = (Map<?, ?>) o;
                int pid = intValue(row.get("id"), 0);
                Map<String, Object> p = Rows.one(jdbc, "select * from extraction_match_participants where id=? and match_id=?", pid, matchId);
                if (p == null) continue;
                int userId = intValue(p.get("user_id"), 0);
                boolean evacuated = boolObj(row.get("evacuated"), false);
                int kills = Math.max(0, intValue(row.get("kills"), 0));
                int earned = Math.max(0, intValue(row.get("earned_cash"), 0));
                int killCash = kills * rewardUnit;
                jdbc.update("update extraction_user_profiles set cash=cash+?,updated_at=now() where user_id=?", earned + killCash, userId);
                Object rewards = row.get("rewards");
                if (rewards instanceof List) {
                    for (Object ro : (List<?>) rewards) {
                        if (!(ro instanceof Map)) continue;
                        Map<?, ?> reward = (Map<?, ?>) ro;
                        int itemDefId = intValue(reward.get("item_def_id"), 0);
                        int qty = Math.max(0, intValue(reward.get("quantity"), 0));
                        if (itemDefId > 0 && qty > 0) addInventory(userId, itemDefId, Math.min(qty, 99), currentSeasonId(), "buffer", pid);
                    }
                }
                handleSpecialWeaponAfterSettle(p, evacuated);
                jdbc.update("update extraction_match_participants set evacuated=?,kills=?,earned_cash=?,kill_reward_cash=?,settled_at=now() where id=?",
                        evacuated ? 1 : 0, kills, earned, killCash, pid);
            }
        }
        jdbc.update("update extraction_matches set status='ended',ended_at=now() where id=?", matchId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/extraction/assets")
    public ApiResponse<Map<String, Object>> adminAssets(@RequestParam int userId, HttpServletRequest req) {
        auth.require(req, "extraction:asset");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Map<String, Object> profile = profile(userId);
        out.put("profile", profile);
        out.put("stats", userStats(userId, currentSeason()));
        out.put("storage", inventory(userId, "storage"));
        out.put("buffer", inventory(userId, "buffer"));
        return ApiResponse.ok(out);
    }

    @PostMapping("/api/admin/extraction/reset/{type}")
    @Transactional
    public ApiResponse<String> reset(@PathVariable String type, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "extraction:reset");
        int userId = userIdOf(auth.current(req));
        String phrase = "assets".equals(type) ? "资产重置" : "data".equals(type) ? "数据重置" : "";
        if (phrase.isEmpty()) throw new IllegalArgumentException("未知的重置类型");
        if (!phrase.equals(text(body.get("confirm_text")))) throw new IllegalArgumentException("请输入“" + phrase + "”确认操作");
        Map<String, Object> pending = Rows.one(jdbc, "select * from extraction_reset_records where reset_type=? and status='pending' order by created_at desc limit 1", type);
        if (pending == null) {
            jdbc.update("insert into extraction_reset_records(reset_type,requested_by_id,status,created_at) values(?,?,'pending',now())", type, userId);
            return ApiResponse.ok("已申请" + phrase + "，需要另一名管理员确认");
        }
        if (intValue(pending.get("requested_by_id"), 0) == userId) throw new IllegalArgumentException("你已经申请了" + phrase + "，需要另一名管理员确认");
        if ("assets".equals(type)) resetAssets();
        else resetData();
        jdbc.update("update extraction_reset_records set confirmed_by_id=?,status='completed',confirmed_at=now() where id=?", userId, pending.get("id"));
        return ApiResponse.ok(phrase + "已完成");
    }

    private Map<String, Object> requireExtraction(HttpServletRequest req) {
        Map<String, Object> user = auth.current(req);
        if (!auth.permissions(text(user.get("role"))).contains("extraction:view") && !canManageExtraction(user)) throw new SecurityException("需要逃离西撇镇授权");
        return user;
    }

    private boolean canManageExtraction(Map<String, Object> user) {
        return "admin".equals(text(user.get("role"))) || "superadmin".equals(text(user.get("role"))) || auth.permissions(text(user.get("role"))).contains("extraction:manage") || auth.permissions(text(user.get("role"))).contains("extraction:match");
    }

    private boolean canAdmin(Map<String, Object> user, String permission) {
        String role = text(user.get("role"));
        return "admin".equals(role) || "superadmin".equals(role) || auth.permissions(role).contains(permission);
    }

    private void syncRuntime() {
        ensureRules();
        jdbc.update("update extraction_seasons set active=0 where active=1 and end_date<curdate()");
        for (Map<String, Object> item : Rows.list(jdbc, "select * from extraction_item_defs where active=1")) todayPrice(item);
        LocalDate cutoff = LocalDate.now().minusDays(1);
        for (Map<String, Object> item : Rows.list(jdbc, itemSql("where i.location='buffer' and i.sold_at is null and i.created_at<?"), cutoff.toString())) {
            int amount = todayPrice(item) * intValue(item.get("quantity"), 1);
            jdbc.update("update extraction_inventory_items set sold_at=now(),sold_price=? where id=?", amount, item.get("id"));
            jdbc.update("update extraction_user_profiles set cash=cash+?,updated_at=now() where user_id=?", amount, item.get("user_id"));
        }
    }

    private void ensureRules() {
        if (Rows.one(jdbc, "select id from extraction_class_rules where name='跑刀仔'") == null) {
            jdbc.update("insert into extraction_class_rules(name,health,maintenance_fee,sort_order,active,created_at) values('跑刀仔',100,0,1,1,now()),('普通兵',100,50,2,1,now()),('重装兵',150,100,3,1,now())");
        }
        if (Rows.one(jdbc, "select id from extraction_weapon_rules where weapon_type='knife' and item_def_id is null") == null) {
            jdbc.update("insert into extraction_weapon_rules(weapon_type,name,usage_fee,durability_cost_percent,active,created_at) values('knife','刀',0,0,1,now())");
        }
        if (Rows.one(jdbc, "select id from extraction_weapon_rules where weapon_type='regular' and item_def_id is null") == null) {
            jdbc.update("insert into extraction_weapon_rules(weapon_type,name,usage_fee,durability_cost_percent,active,created_at) values('regular','常规武器',50,0,1,now())");
        }
        for (Map<String, Object> item : Rows.list(jdbc, "select * from extraction_item_defs where active=1 and item_category='武器'")) {
            if (Rows.one(jdbc, "select id from extraction_weapon_rules where weapon_type='special' and item_def_id=?", item.get("id")) == null) {
                jdbc.update("insert into extraction_weapon_rules(weapon_type,name,item_def_id,usage_fee,durability_cost_percent,active,created_at) values('special',?,?,0,10,1,now())", item.get("name"), item.get("id"));
            }
        }
    }

    private Map<String, Object> profile(int userId) {
        Map<String, Object> p = Rows.one(jdbc, "select * from extraction_user_profiles where user_id=?", userId);
        if (p == null) {
            jdbc.update("insert into extraction_user_profiles(user_id,cash,storage_rows,storage_cols,runs_count,updated_at) values(?,0,?,?,0,now())", userId, STORAGE_ROWS, STORAGE_COLS);
            p = Rows.one(jdbc, "select * from extraction_user_profiles where user_id=?", userId);
        }
        if (intValue(p.get("storage_rows"), 0) < STORAGE_ROWS || intValue(p.get("storage_cols"), 0) < STORAGE_COLS) {
            jdbc.update("update extraction_user_profiles set storage_rows=greatest(storage_rows,?),storage_cols=greatest(storage_cols,?) where user_id=?", STORAGE_ROWS, STORAGE_COLS, userId);
            p = Rows.one(jdbc, "select * from extraction_user_profiles where user_id=?", userId);
        }
        return p;
    }

    private Map<String, Object> currentSeason() {
        return Rows.one(jdbc, "select * from extraction_seasons where active=1 and start_date<=curdate() and end_date>=curdate() order by start_date desc,id desc limit 1");
    }

    private Integer currentSeasonId() {
        Map<String, Object> s = currentSeason();
        return s == null ? null : intValue(s.get("id"), 0);
    }

    private List<Map<String, Object>> inventory(int userId, String location) {
        List<Map<String, Object>> rows = Rows.list(jdbc, itemSql("where i.user_id=? and i.location=? and i.sold_at is null order by i.`row`,i.`col`,i.id"), userId, location);
        for (Map<String, Object> row : rows) {
            row.put("price", todayPrice(row));
            row.put("trend", trend(row));
        }
        return rows;
    }

    private List<Map<String, Object>> adminItemRows() {
        List<Map<String, Object>> rows = Rows.list(jdbc, "select * from extraction_item_defs order by created_at desc,id desc");
        for (Map<String, Object> row : rows) {
            row.put("today_price", todayPrice(row));
            row.put("trend", trend(row));
        }
        return rows;
    }

    private List<Map<String, Object>> adminShopRows() {
        return Rows.list(jdbc, "select s.*,d.name item_def_name from extraction_shop_items s left join extraction_item_defs d on d.id=s.item_def_id order by s.active desc,s.created_at desc,s.id desc");
    }

    private List<Map<String, Object>> adminSeasonRows() {
        return Rows.list(jdbc, "select * from extraction_seasons order by start_date desc,id desc");
    }

    private List<Map<String, Object>> adminClassRows() {
        ensureRules();
        return Rows.list(jdbc, "select * from extraction_class_rules order by sort_order,id");
    }

    private List<Map<String, Object>> adminWeaponRows() {
        ensureRules();
        return Rows.list(jdbc, "select w.*,d.name item_name from extraction_weapon_rules w left join extraction_item_defs d on d.id=w.item_def_id where w.active=1 order by w.weapon_type,w.name");
    }

    private List<Map<String, Object>> adminMatchRows() {
        return Rows.list(jdbc,
                "select m.*, (select count(*) from extraction_match_participants p where p.match_id=m.id) participant_count " +
                        "from extraction_matches m order by case m.status when 'preparing' then 0 when 'started' then 1 when 'ended' then 2 else 3 end,m.created_at desc,m.id desc limit 100");
    }

    private String itemSql(String where) {
        return "select i.*,d.name,d.level,d.item_category,d.width,d.height,d.photo_filename,d.min_price,d.max_price from extraction_inventory_items i join extraction_item_defs d on d.id=i.item_def_id " + where;
    }

    private int todayPrice(Map<String, Object> item) {
        int itemDefId = intValue(item.get("item_def_id") == null ? item.get("id") : item.get("item_def_id"), 0);
        String today = LocalDate.now().toString();
        Map<String, Object> row = Rows.one(jdbc, "select price from extraction_item_prices where item_def_id=? and price_date=?", itemDefId, today);
        if (row != null) return intValue(row.get("price"), 1);
        int min = positive(item.get("min_price"), 1);
        int max = Math.max(min, positive(item.get("max_price"), min));
        int price = min + random.nextInt(max - min + 1);
        try {
            jdbc.update("insert into extraction_item_prices(item_def_id,price_date,price,created_at) values(?,?,?,now())", itemDefId, today, price);
        } catch (DuplicateKeyException ignored) {
        }
        return price;
    }

    private String trend(Map<String, Object> item) {
        int itemDefId = intValue(item.get("item_def_id") == null ? item.get("id") : item.get("item_def_id"), 0);
        Map<String, Object> today = Rows.one(jdbc, "select price from extraction_item_prices where item_def_id=? and price_date=curdate()", itemDefId);
        Map<String, Object> yesterday = Rows.one(jdbc, "select price from extraction_item_prices where item_def_id=? and price_date=date_sub(curdate(), interval 1 day)", itemDefId);
        if (today == null || yesterday == null) return "flat";
        int t = intValue(today.get("price"), 0);
        int y = intValue(yesterday.get("price"), 0);
        return t > y ? "up" : t < y ? "down" : "flat";
    }

    private boolean canPlace(int userId, String location, int rows, int cols, int w, int h, int row, int col, int excludeId) {
        if (!inside(rows, cols, w, h, row, col)) return false;
        Set<String> target = cells(row, col, w, h);
        for (Map<String, Object> other : Rows.list(jdbc, itemSql("where i.user_id=? and i.location=? and i.sold_at is null and i.id<>?"), userId, location, excludeId)) {
            Set<String> occupied = cells(intValue(other.get("row"), 1), intValue(other.get("col"), 1), intValue(other.get("width"), 1), intValue(other.get("height"), 1));
            for (String cell : occupied) if (target.contains(cell)) return false;
        }
        return true;
    }

    private void normalize(int userId, String location, int rows, int cols) {
        for (Map<String, Object> item : Rows.list(jdbc, itemSql("where i.user_id=? and i.location=? and i.sold_at is null order by i.created_at,i.id"), userId, location)) {
            int row = intValue(item.get("row"), 0);
            int col = intValue(item.get("col"), 0);
            int w = intValue(item.get("width"), 1);
            int h = intValue(item.get("height"), 1);
            int itemId = intValue(item.get("id"), 0);
            if (row > 0 && col > 0 && canPlace(userId, location, rows, cols, w, h, row, col, itemId)) continue;
            int[] slot = findSlot(userId, location, rows, cols, w, h, itemId);
            jdbc.update("update extraction_inventory_items set `row`=?,`col`=? where id=?", slot[0], slot[1], itemId);
        }
    }

    private int[] findSlot(int userId, String location, int rows, int cols, int w, int h, int excludeId) {
        for (int r = 1; r <= rows; r++) for (int c = 1; c <= cols; c++) if (canPlace(userId, location, rows, cols, w, h, r, c, excludeId)) return new int[]{r, c};
        throw new IllegalArgumentException("仓库空间不足");
    }

    private void addInventory(int userId, int itemDefId, int qty, Integer seasonId, String location, Integer participantId) {
        Map<String, Object> item = Rows.one(jdbc, "select * from extraction_item_defs where id=?", itemDefId);
        if (item == null) throw new IllegalArgumentException("物品不存在");
        if (isMoney(item)) {
            Map<String, Object> existing = Rows.one(jdbc, "select * from extraction_inventory_items where user_id=? and item_def_id=? and location=? and sold_at is null and match_participant_id " + (participantId == null ? "is null" : "=?") + " order by id limit 1",
                    participantId == null ? new Object[]{userId, itemDefId, location} : new Object[]{userId, itemDefId, location, participantId});
            if (existing != null) {
                jdbc.update("update extraction_inventory_items set quantity=quantity+? where id=?", qty, existing.get("id"));
                return;
            }
        }
        Map<String, Object> profile = profile(userId);
        int rows = "buffer".equals(location) ? BUFFER_ROWS : intValue(profile.get("storage_rows"), STORAGE_ROWS);
        int cols = "buffer".equals(location) ? BUFFER_COLS : intValue(profile.get("storage_cols"), STORAGE_COLS);
        int[] slot = findSlot(userId, location, rows, cols, intValue(item.get("width"), 1), intValue(item.get("height"), 1), 0);
        jdbc.update("insert into extraction_inventory_items(user_id,item_def_id,season_id,location,`row`,`col`,quantity,durability_percent,match_participant_id,created_at) values(?,?,?,?,?,?,?,?,?,now())",
                userId, itemDefId, seasonId, location, slot[0], slot[1], Math.max(1, qty), 100, participantId);
    }

    private Map<String, Object> matchDetail(int matchId, int userId, boolean manage) {
        Map<String, Object> match = Rows.one(jdbc, "select * from extraction_matches where id=?", matchId);
        if (match == null) throw new IllegalArgumentException("对局不存在");
        List<Map<String, Object>> ps = participants(matchId);
        Map<String, Object> me = Rows.one(jdbc, "select * from extraction_match_participants where match_id=? and user_id=?", matchId, userId);
        boolean started = !"preparing".equals(text(match.get("status")));
        Integer mySquad = me == null || me.get("squad_no") == null ? null : intValue(me.get("squad_no"), 0);
        for (Map<String, Object> p : ps) {
            boolean canSee = manage || (started && mySquad != null && mySquad == intValue(p.get("squad_no"), -1)) || intValue(p.get("user_id"), 0) == userId;
            if (!canSee) {
                p.remove("class_name");
                p.remove("weapon_name");
                p.remove("weapon_type");
                p.remove("weapon_inventory_item_id");
            }
            p.put("cost", participantCost(p));
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("match", match);
        out.put("participant", me);
        out.put("participants", ps);
        out.put("classes", Rows.list(jdbc, "select * from extraction_class_rules where active=1 order by sort_order,id"));
        out.put("weapons", weaponChoices(userId));
        out.put("item_defs", Rows.list(jdbc, "select * from extraction_item_defs where active=1 order by item_category desc,name"));
        out.put("season", currentSeason());
        return out;
    }

    private List<Map<String, Object>> participants(int matchId) {
        return Rows.list(jdbc,
                "select p.*,u.username,u.callsign,c.name class_name,c.health,c.maintenance_fee,w.name weapon_name,w.weapon_type,w.usage_fee,w.durability_cost_percent " +
                        "from extraction_match_participants p join users u on u.id=p.user_id " +
                        "left join extraction_class_rules c on c.id=p.class_rule_id " +
                        "left join extraction_weapon_rules w on w.id=p.weapon_rule_id " +
                        "where p.match_id=? order by p.squad_no,p.created_at,p.id", matchId);
    }

    private List<Map<String, Object>> weaponChoices(int userId) {
        ensureRules();
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : Rows.list(jdbc, "select * from extraction_weapon_rules where active=1 and weapon_type in ('knife','regular') order by weapon_type")) {
            Map<String, Object> row = new LinkedHashMap<String, Object>(r);
            row.put("value", "rule:" + r.get("id"));
            row.put("label", text(r.get("name")) + "（使用费 " + intValue(r.get("usage_fee"), 0) + "）");
            out.add(row);
        }
        for (Map<String, Object> item : Rows.list(jdbc, itemSql("where i.user_id=? and i.location='storage' and i.sold_at is null and d.item_category='武器' order by d.name,i.id"), userId)) {
            Map<String, Object> rule = Rows.one(jdbc, "select * from extraction_weapon_rules where active=1 and weapon_type='special' and item_def_id=?", item.get("item_def_id"));
            if (rule == null) continue;
            Map<String, Object> row = new LinkedHashMap<String, Object>(rule);
            row.put("value", "special:" + item.get("id"));
            row.put("label", "特殊武器：" + item.get("name") + "（耐久 " + intValue(item.get("durability_percent"), 100) + "%）");
            row.put("inventory_item_id", item.get("id"));
            out.add(row);
        }
        return out;
    }

    private WeaponChoice parseWeaponChoice(int userId, String raw) {
        if (raw.startsWith("special:")) {
            int invId = intValue(raw.substring(8), 0);
            Map<String, Object> inv = Rows.one(jdbc, itemSql("where i.id=? and i.user_id=? and i.location='storage' and i.sold_at is null and d.item_category='武器'"), invId, userId);
            if (inv == null) return new WeaponChoice(null, null);
            return new WeaponChoice(Rows.one(jdbc, "select * from extraction_weapon_rules where active=1 and weapon_type='special' and item_def_id=?", inv.get("item_def_id")), invId);
        }
        if (raw.startsWith("rule:")) return new WeaponChoice(Rows.one(jdbc, "select * from extraction_weapon_rules where active=1 and id=? and weapon_type in ('knife','regular')", intValue(raw.substring(5), 0)), null);
        return new WeaponChoice(null, null);
    }

    private int participantCost(Map<String, Object> p) {
        int total = intValue(p.get("maintenance_fee"), 0);
        if ("regular".equals(text(p.get("weapon_type")))) total += intValue(p.get("usage_fee"), 0);
        return total;
    }

    private void handleSpecialWeaponAfterSettle(Map<String, Object> p, boolean evacuated) {
        if (!"special".equals(text(p.get("weapon_type"))) || p.get("weapon_inventory_item_id") == null) return;
        int invId = intValue(p.get("weapon_inventory_item_id"), 0);
        if (!evacuated) {
            jdbc.update("delete from extraction_inventory_items where id=?", invId);
            return;
        }
        int newDurability = intValue(Rows.one(jdbc, "select durability_percent from extraction_inventory_items where id=?", invId).get("durability_percent"), 100) - intValue(p.get("durability_cost_percent"), 0);
        if (newDurability <= 0) jdbc.update("delete from extraction_inventory_items where id=?", invId);
        else {
            Map<String, Object> inv = Rows.one(jdbc, itemSql("where i.id=?"), invId);
            Map<String, Object> profile = profile(intValue(p.get("user_id"), 0));
            int[] slot = findSlot(intValue(p.get("user_id"), 0), "storage", intValue(profile.get("storage_rows"), STORAGE_ROWS), intValue(profile.get("storage_cols"), STORAGE_COLS), intValue(inv.get("width"), 1), intValue(inv.get("height"), 1), invId);
            jdbc.update("update extraction_inventory_items set durability_percent=?,location='storage',`row`=?,`col`=? where id=?", newDurability, slot[0], slot[1], invId);
        }
    }

    private Map<String, Object> userStats(int userId, Map<String, Object> season) {
        Map<String, Object> total = Rows.one(jdbc,
                "select count(*) match_count, sum(case when evacuated=1 then 1 else 0 end) evac_count, avg(earned_cash+kill_reward_cash) avg_cash " +
                        "from extraction_match_participants p join extraction_matches m on m.id=p.match_id where p.user_id=? and m.status='ended'", userId);
        Map<String, Object> seasonStats = new LinkedHashMap<String, Object>();
        if (season != null) seasonStats = Rows.one(jdbc,
                "select count(*) match_count, sum(case when p.evacuated=1 then 1 else 0 end) evac_count, avg(p.earned_cash+p.kill_reward_cash) avg_cash " +
                        "from extraction_match_participants p join extraction_matches m on m.id=p.match_id where p.user_id=? and m.status='ended' and m.ended_at>=? and m.ended_at<=?",
                userId, season.get("start_date") + " 00:00:00", season.get("end_date") + " 23:59:59");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("total", normalizeStats(total));
        out.put("season", normalizeStats(seasonStats));
        return out;
    }

    private Map<String, Object> normalizeStats(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        int matchCount = row == null ? 0 : intValue(row.get("match_count"), 0);
        int evacCount = row == null ? 0 : intValue(row.get("evac_count"), 0);
        out.put("match_count", matchCount);
        out.put("evac_count", evacCount);
        out.put("evac_rate", matchCount == 0 ? 0 : Math.round(evacCount * 1000.0 / matchCount) / 10.0);
        out.put("avg_cash", row == null || row.get("avg_cash") == null ? 0 : row.get("avg_cash"));
        return out;
    }

    private void saveShop(Integer id, Map<String, Object> body) {
        String category = shopRawCategory(body.get("category"));
        Integer itemDefId = intValue(body.get("item_def_id"), 0) == 0 ? null : intValue(body.get("item_def_id"), 0);
        Integer rows = "storage".equals(category) ? positive(body.get("storage_rows"), STORAGE_ROWS) : null;
        Integer cols = "storage".equals(category) ? positive(body.get("storage_cols"), STORAGE_COLS) : null;
        if (id == null) jdbc.update("insert into extraction_shop_items(category,name,price,stock,shelf_until,storage_rows,storage_cols,item_def_id,active,created_at) values(?,?,?,?,?,?,?,?,?,now())",
                category, required(body, "name"), Math.max(0, intValue(body.get("price"), 0)), Math.max(0, intValue(body.get("stock"), 0)), emptyToNull(body.get("shelf_until")), rows, cols, itemDefId, boolObj(body.get("active"), true) ? 1 : 0);
        else jdbc.update("update extraction_shop_items set category=?,name=?,price=?,stock=?,shelf_until=?,storage_rows=?,storage_cols=?,item_def_id=?,active=? where id=?",
                category, required(body, "name"), Math.max(0, intValue(body.get("price"), 0)), Math.max(0, intValue(body.get("stock"), 0)), emptyToNull(body.get("shelf_until")), rows, cols, itemDefId, boolObj(body.get("active"), true) ? 1 : 0, id);
    }

    private int ensureItemDef(String name, String category, int price) {
        Map<String, Object> item = Rows.one(jdbc, "select id from extraction_item_defs where name=? and item_category=? limit 1", name, category);
        if (item != null) return intValue(item.get("id"), 0);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement("insert into extraction_item_defs(name,level,item_category,min_price,max_price,width,height,active,created_at) values(?,'普通',?,?,?,1,1,1,now())", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setInt(3, Math.max(1, price));
            ps.setInt(4, Math.max(1, price));
            return ps;
        }, key);
        return key.getKey().intValue();
    }

    private void resetAssets() {
        jdbc.update("update extraction_match_participants set weapon_inventory_item_id=null");
        jdbc.update("delete from extraction_inventory_items");
        jdbc.update("update extraction_user_profiles set cash=0,storage_rows=?,storage_cols=?,updated_at=now()", STORAGE_ROWS, STORAGE_COLS);
    }

    private void resetData() {
        jdbc.update("update extraction_match_participants set weapon_inventory_item_id=null");
        jdbc.update("delete from extraction_inventory_items");
        jdbc.update("delete from extraction_match_participants");
        jdbc.update("delete from extraction_matches");
        jdbc.update("delete from extraction_run_records");
        jdbc.update("delete from extraction_user_profiles");
    }

    private String resetSql() {
        return "select r.*,req.callsign requested_by_name,conf.callsign confirmed_by_name from extraction_reset_records r left join users req on req.id=r.requested_by_id left join users conf on conf.id=r.confirmed_by_id order by r.created_at desc limit 50";
    }

    private boolean inside(int rows, int cols, int w, int h, int row, int col) {
        return row >= 1 && col >= 1 && row + h - 1 <= rows && col + w - 1 <= cols;
    }

    private Set<String> cells(int row, int col, int w, int h) {
        Set<String> out = new HashSet<String>();
        for (int r = row; r < row + h; r++) for (int c = col; c < col + w; c++) out.add(r + "," + c);
        return out;
    }

    private boolean isMoney(Map<String, Object> item) {
        return "钱币".equals(text(item.get("item_category")));
    }

    private int userIdOf(Map<String, Object> user) {
        return intValue(user.get("id"), 0);
    }

    private int intValue(Object value, int fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty() || "null".equals(String.valueOf(value))) return fallback;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private int positive(Object value, int fallback) {
        return Math.max(1, intValue(value, fallback));
    }

    private int between(Object value, int min, int max, int fallback) {
        return Math.max(min, Math.min(max, intValue(value, fallback)));
    }

    private boolean bool(Object value) {
        return boolObj(value, false);
    }

    private boolean boolObj(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        String s = String.valueOf(value);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String required(Map<?, ?> body, String key) {
        String value = text(body.get(key));
        if (value.isEmpty()) throw new IllegalArgumentException("请填写必填项");
        return value;
    }

    private Object emptyToNull(Object value) {
        String s = text(value);
        return s.isEmpty() ? null : s;
    }

    private String level(Object value) {
        String v = text(value);
        return Arrays.asList("超凡", "史诗", "精品", "普通").contains(v) ? v : "普通";
    }

    private String category(Object value) {
        String v = text(value);
        return Arrays.asList("常规物品", "武器", "钱币").contains(v) ? v : "常规物品";
    }

    private String shopRawCategory(Object value) {
        String v = text(value);
        return Arrays.asList("storage", "item", "weapon", "money").contains(v) ? v : "item";
    }

    private String shopCategory(String value) {
        if ("weapon".equals(value)) return "武器";
        if ("money".equals(value)) return "钱币";
        return "常规物品";
    }

    private List<Integer> intList(Object value) {
        List<Integer> out = new ArrayList<Integer>();
        if (value instanceof Collection) for (Object v : (Collection<?>) value) out.add(intValue(v, 0));
        return out;
    }

    private static class WeaponChoice {
        final Map<String, Object> rule;
        final Integer inventoryId;

        WeaponChoice(Map<String, Object> rule, Integer inventoryId) {
            this.rule = rule;
            this.inventoryId = inventoryId;
        }
    }
}
