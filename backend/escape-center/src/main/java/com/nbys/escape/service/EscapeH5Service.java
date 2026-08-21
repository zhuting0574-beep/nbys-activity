package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import com.nbys.escape.domain.GridPacking;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

@Service
public class EscapeH5Service {
    private static final Set<String> WAREHOUSE_TYPES =
            new HashSet<String>(Arrays.asList("personal", "buffer"));

    private final JdbcTemplate jdbc;

    public EscapeH5Service(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> dashboard(EscapeAccessService.UserContext me) {
        ensureAsset(me.userId);
        Map<String, Object> asset = requiredOne(
                "select cash_balance,personal_width,personal_height,buffer_width,buffer_height " +
                        "from escape_user_assets where user_id=?", me.userId);
        Map<String, Object> stats = requiredOne(
                "select count(*) matches_played," +
                        "coalesce(sum(case when p.`escaped`=1 then 1 else 0 end),0) successful_escapes," +
                        "coalesce(round(100*sum(case when p.`escaped`=1 then 1 else 0 end)/nullif(count(*),0),1),0) escape_rate," +
                        "coalesce(round(avg(p.manual_cash+p.kill_cash),2),0) average_cash," +
                        "coalesce((select round(sum(si.price_snapshot*si.quantity)/nullif(count(distinct sd.participant_id),0),2) " +
                        "from escape_match_settlement_details sd left join escape_match_settlement_items si " +
                        "on si.settlement_detail_id=sd.id where sd.user_id=?),0) average_item_value " +
                        "from escape_match_participants p join escape_matches m on m.id=p.match_id " +
                        "where p.user_id=? and m.status='settled'", me.userId, me.userId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("user_id", me.userId);
        result.put("display_name", me.displayName);
        result.put("administrator", me.administrator);
        result.put("asset", asset);
        result.put("stats", stats);
        Map<String, Object> activeSeason = Rows.one(jdbc,
                "select id,name,start_date,end_date,kill_reward from escape_seasons " +
                        "where enabled=1 and current_date between start_date and end_date order by id desc limit 1");
        result.put("active_season", activeSeason);
        result.put("rarity_summary", raritySummary(me.userId, activeSeason));
        result.put("warehouse_summary", Rows.list(jdbc,
                "select warehouse_type,count(*) item_count,coalesce(sum(i.width*i.height),0) used_cells " +
                        "from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "where inv.user_id=? group by warehouse_type", me.userId));
        return result;
    }

    private Map<String, Object> raritySummary(int userId, Map<String, Object> activeSeason) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("extraordinary", 0);
        summary.put("epic", 0);
        summary.put("fine", 0);
        summary.put("normal", 0);
        if (activeSeason == null || activeSeason.get("id") == null) return summary;
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select si.rarity_snapshot rarity,coalesce(sum(si.quantity),0) item_count " +
                        "from escape_match_settlements sh " +
                        "join escape_match_settlement_details sd on sd.settlement_id=sh.id " +
                        "join escape_match_settlement_items si on si.settlement_detail_id=sd.id " +
                        "where sd.user_id=? and sh.season_id=? group by si.rarity_snapshot",
                userId, activeSeason.get("id"));
        for (Map<String, Object> row : rows) {
            String rarity = String.valueOf(row.get("rarity"));
            if (summary.containsKey(rarity)) summary.put(rarity, row.get("item_count"));
        }
        return summary;
    }

    public List<Map<String, Object>> matches(EscapeAccessService.UserContext me) {
        List<Map<String, Object>> matches = Rows.list(jdbc,
                "select m.id,m.name,m.status,m.team_count,m.team_capacity,m.created_by,m.created_at,m.started_at," +
                        "v.name venue_name,p.team_no,p.loadout_status," +
                        "(select count(*) from escape_match_participants x where x.match_id=m.id and x.team_no is not null) participant_count," +
                        "(select count(*) from escape_match_participants t where t.match_id=m.id and t.team_no=p.team_no) team_member_count," +
                        "(select group_concat(coalesce(nullif(tu.callsign,''),tu.username) order by t.id separator '、') " +
                        "from escape_match_participants t join users tu on tu.id=t.user_id " +
                        "where t.match_id=m.id and t.team_no=p.team_no) team_member_names," +
                        "(select concat('[',group_concat(json_object('user_id',tu.id,'avatar_url',tu.avatar_url,'callsign',tu.callsign,'username',tu.username) order by t.id separator ','),']') " +
                        "from escape_match_participants t join users tu on tu.id=t.user_id " +
                        "where t.match_id=m.id and t.team_no=p.team_no) team_members_json " +
                        "from escape_matches m left join venues v on v.id=m.venue_id " +
                        "left join escape_match_participants p on p.match_id=m.id and p.user_id=? " +
                        "where m.status in ('preparing','in_progress') and m.season_id=(select id from escape_seasons " +
                        "where enabled=1 and current_date between start_date and end_date order by id desc limit 1) " +
                        "order by m.id desc", me.userId);
        for (Map<String, Object> match : matches) {
            boolean owner = ((Number) match.get("created_by")).intValue() == me.userId;
            match.put("can_control", owner || "superadmin".equals(me.role));
            Object teamNo = match.get("team_no");
            if (teamNo != null) {
                match.put("team_members", Rows.list(jdbc,
                        "select u.id user_id,u.avatar_url,u.callsign,u.username " +
                                "from escape_match_participants p join users u on u.id=p.user_id " +
                                "where p.match_id=? and p.team_no=? order by p.id", match.get("id"), teamNo));
            } else {
                match.put("team_members", java.util.Collections.emptyList());
            }
        }
        return matches;
    }

    public Map<String, Object> matchDetail(long matchId, EscapeAccessService.UserContext me) {
        Map<String, Object> match = requiredOne(
                "select m.*,v.name venue_name,s.name season_name,s.kill_reward " +
                        "from escape_matches m left join venues v on v.id=m.venue_id " +
                        "left join escape_seasons s on s.id=m.season_id where m.id=?", matchId);
        Map<String, Object> myParticipant = Rows.one(jdbc,
                "select * from escape_match_participants where match_id=? and user_id=?", matchId, me.userId);
        List<Map<String, Object>> raw = Rows.list(jdbc,
                "select p.id,p.user_id,p.team_no,p.profession_id,p.weapon_id,p.special_inventory_id,p.loadout_status," +
                        "coalesce(nullif(u.callsign,''),u.username) callsign,pr.name profession_name,w.name weapon_name " +
                        "from escape_match_participants p join users u on u.id=p.user_id " +
                        "left join escape_professions pr on pr.id=p.profession_id " +
                        "left join escape_weapons w on w.id=p.weapon_id where p.match_id=? order by p.team_no,p.id", matchId);
        String status = String.valueOf(match.get("status"));
        boolean controller = ((Number) match.get("created_by")).intValue() == me.userId
                || "superadmin".equals(me.role);
        Integer myTeam = myParticipant == null || myParticipant.get("team_no") == null
                ? null : ((Number) myParticipant.get("team_no")).intValue();
        List<Map<String, Object>> visible = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : raw) {
            int rowUserId = ((Number) row.get("user_id")).intValue();
            Integer rowTeam = row.get("team_no") == null ? null : ((Number) row.get("team_no")).intValue();
            boolean full = controller || me.administrator || rowUserId == me.userId
                    || (!"preparing".equals(status) && myTeam != null && myTeam.equals(rowTeam));
            Map<String, Object> participant = new LinkedHashMap<String, Object>();
            participant.put("user_id", rowUserId);
            participant.put("callsign", row.get("callsign"));
            // Before lock, members need to see squad composition to choose a team.
            participant.put("team_no", full || "preparing".equals(status) ? rowTeam : null);
            participant.put("loadout_status", full ? row.get("loadout_status") : null);
            participant.put("profession", full ? row.get("profession_name") : null);
            participant.put("weapon", full ? row.get("weapon_name") : null);
            participant.put("details_visible", full);
            visible.add(participant);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("match", match);
        result.put("can_control", controller);
        result.put("me", myParticipant);
        result.put("participants", visible);
        result.put("professions", Rows.list(jdbc,
                "select id,name,health,maintenance_fee,knife_only from escape_professions where enabled=1 order by sort_order,id"));
        result.put("weapons", Rows.list(jdbc,
                "select id,case weapon_type when 'knife' then '近战武器' when 'regular' then '普通武器' " +
                        "when 'special' then '特殊武器' end name,weapon_type,usage_fee,durability_loss_percent " +
                        "from escape_weapons where enabled=1 and item_id is null order by sort_order,id"));
        result.put("special_weapons", Rows.list(jdbc,
                "select inv.id inventory_id,i.id item_id,i.name,i.image_url,inv.durability_percent," +
                        "w.id weapon_id,i.name weapon_name " +
                        "from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "join escape_weapons w on w.item_id is null and w.weapon_type='special' and w.enabled=1 " +
                        "where inv.user_id=? and inv.warehouse_type='personal' and inv.status='available' " +
                        "and i.enabled=1 and i.deleted_at is null and i.category='weapon' and i.weapon_type='special' order by inv.id", me.userId));
        return result;
    }

    @Transactional
    public Map<String, Object> joinMatch(long matchId, int userId) {
        Map<String, Object> match = requiredOne(
                "select id,status,team_count,team_capacity from escape_matches where id=? for update", matchId);
        if (!"preparing".equals(String.valueOf(match.get("status")))) {
            throw new IllegalArgumentException("该对局已停止加入");
        }
        Map<String, Object> existing = Rows.one(jdbc,
                "select * from escape_match_participants where match_id=? and user_id=?", matchId, userId);
        if (existing != null) return existing;
        Number participantCount = jdbc.queryForObject(
                "select count(*) from escape_match_participants where match_id=?", Number.class, matchId);
        int capacity = ((Number) match.get("team_count")).intValue()
                * ((Number) match.get("team_capacity")).intValue();
        if (participantCount != null && participantCount.intValue() >= capacity) {
            throw new IllegalArgumentException("该战局人数已满");
        }
        try {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "insert into escape_match_participants(match_id,user_id,loadout_status,joined_at) " +
                                "values(?,?,'draft',now())", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, matchId);
                ps.setInt(2, userId);
                return ps;
            }, key);
            return requiredOne("select * from escape_match_participants where id=?", key.getKey().longValue());
        } catch (DuplicateKeyException ignored) {
            return requiredOne("select * from escape_match_participants where match_id=? and user_id=?", matchId, userId);
        }
    }

    @Transactional
    public Map<String, Object> saveLoadout(long matchId, int userId, Map<String, Object> body) {
        Map<String, Object> match = requiredOne(
                "select id,status,team_count,team_capacity from escape_matches where id=? for update", matchId);
        if (!"preparing".equals(String.valueOf(match.get("status")))) {
            throw new IllegalArgumentException("对局已开始，不能修改整备");
        }
        Map<String, Object> participant = Rows.one(jdbc,
                "select * from escape_match_participants where match_id=? and user_id=? for update", matchId, userId);
        if (participant == null) {
            Number participantCount = jdbc.queryForObject(
                    "select count(*) from escape_match_participants where match_id=? and team_no is not null",
                    Number.class, matchId);
            int capacity = ((Number) match.get("team_count")).intValue()
                    * ((Number) match.get("team_capacity")).intValue();
            if (participantCount != null && participantCount.intValue() >= capacity) {
                throw new IllegalArgumentException("该战局人数已满");
            }
            jdbc.update("insert into escape_match_participants(match_id,user_id,loadout_status,joined_at) " +
                    "values(?,?,'draft',now())", matchId, userId);
            participant = requiredOne(
                    "select * from escape_match_participants where match_id=? and user_id=? for update", matchId, userId);
        }
        if ("locked".equals(String.valueOf(participant.get("loadout_status")))) {
            throw new IllegalArgumentException("整备已锁定");
        }
        int teamNo = positiveInt(body.get("team_no"), "请选择小队");
        int professionId = positiveInt(body.get("profession_id"), "请选择兵种");
        int weaponId = positiveInt(body.get("weapon_id"), "请选择武器");
        int teamCount = ((Number) match.get("team_count")).intValue();
        if (teamNo > teamCount) throw new IllegalArgumentException("小队不存在");
        Number occupied = jdbc.queryForObject(
                "select count(*) from escape_match_participants where match_id=? and team_no=? and user_id<>?",
                Number.class, matchId, teamNo, userId);
        if (occupied != null && occupied.intValue() >= ((Number) match.get("team_capacity")).intValue()) {
            throw new IllegalArgumentException("该小队人数已满");
        }
        Map<String, Object> profession = requiredOne(
                "select id,knife_only from escape_professions where id=? and enabled=1", professionId);
        Map<String, Object> weapon = requiredOne(
                "select id,weapon_type from escape_weapons where id=? and enabled=1", weaponId);
        String weaponType = String.valueOf(weapon.get("weapon_type"));
        if (truthy(profession.get("knife_only")) && !"knife".equals(weaponType)) {
            throw new IllegalArgumentException("跑刀仔只能选择刀");
        }
        if (!truthy(profession.get("knife_only")) && "knife".equals(weaponType)) {
            throw new IllegalArgumentException("非跑刀仔不能选择近战武器");
        }
        Long specialInventoryId = nullableLong(body.get("special_inventory_id"));
        if ("special".equals(weaponType)) {
            if (specialInventoryId == null) throw new IllegalArgumentException("请选择个人仓库中的特殊武器");
            requiredOne("select inv.id from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                            "join escape_weapons w on w.id=? and w.item_id is null " +
                            "where inv.id=? and inv.user_id=? and inv.warehouse_type='personal' and inv.status='available' " +
                            "and i.enabled=1 and i.deleted_at is null and i.category='weapon' " +
                            "and i.weapon_type='special' and w.weapon_type='special'",
                    weaponId, specialInventoryId, userId);
        } else if (specialInventoryId != null) {
            throw new IllegalArgumentException("当前武器类型不能绑定仓库武器");
        }
        jdbc.update("update escape_match_participants set team_no=?,profession_id=?,weapon_id=?,special_inventory_id=? " +
                        "where match_id=? and user_id=?",
                teamNo, professionId, weaponId, specialInventoryId, matchId, userId);
        return requiredOne("select * from escape_match_participants where match_id=? and user_id=?", matchId, userId);
    }

    @Transactional
    public Map<String, Object> lockLoadout(long matchId, int userId, String idempotencyKey) {
        String operationType = operationType("lock_loadout", matchId);
        if (!claimOperation(userId, operationType, idempotencyKey)) {
            return requiredOne("select * from escape_match_participants where match_id=? and user_id=?", matchId, userId);
        }
        Map<String, Object> match = requiredOne(
                "select id,status from escape_matches where id=? for update", matchId);
        if (!"preparing".equals(String.valueOf(match.get("status")))) {
            throw new IllegalArgumentException("对局已开始，不能锁定整备");
        }
        Map<String, Object> participant = requiredOne(
                "select * from escape_match_participants where match_id=? and user_id=? for update", matchId, userId);
        if ("locked".equals(String.valueOf(participant.get("loadout_status")))) {
            completeOperation(userId, operationType, idempotencyKey, String.valueOf(matchId));
            return participant;
        }
        if (participant.get("team_no") == null || participant.get("profession_id") == null || participant.get("weapon_id") == null) {
            throw new IllegalArgumentException("请先完成小队、兵种和武器选择");
        }
        Map<String, Object> fees = requiredOne(
                "select p.maintenance_fee,w.usage_fee,w.weapon_type from escape_professions p " +
                        "join escape_weapons w on w.id=? where p.id=?",
                participant.get("weapon_id"), participant.get("profession_id"));
        BigDecimal requiredCash = decimal(fees.get("maintenance_fee")).add(decimal(fees.get("usage_fee")));
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select cash_balance from escape_user_assets where user_id=? for update", userId);
        if (decimal(asset.get("cash_balance")).compareTo(requiredCash) < 0) {
            throw new IllegalArgumentException("现金不足以支付开局费用");
        }
        if ("special".equals(String.valueOf(fees.get("weapon_type")))) {
            if (participant.get("special_inventory_id") == null) throw new IllegalArgumentException("特殊武器未绑定");
            int updated = jdbc.update(
                    "update escape_inventory_instances set status='loadout_locked',version=version+1 " +
                            "where id=? and user_id=? and warehouse_type='personal' and status='available'",
                    participant.get("special_inventory_id"), userId);
            if (updated == 0) throw new IllegalArgumentException("特殊武器已被占用或不存在");
        }
        jdbc.update("update escape_match_participants set loadout_status='locked',locked_at=now(),version=version+1 where id=?",
                participant.get("id"));
        completeOperation(userId, operationType, idempotencyKey, String.valueOf(matchId));
        return requiredOne("select * from escape_match_participants where id=?", participant.get("id"));
    }

    public Map<String, Object> warehouse(int userId, String type) {
        requireWarehouseType(type);
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select personal_width,personal_height,buffer_width,buffer_height from escape_user_assets where user_id=?",
                userId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", type);
        result.put("width", asset.get(type + "_width"));
        result.put("height", asset.get(type + "_height"));
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select inv.id inventory_id,inv.pos_x,inv.pos_y,inv.durability_percent,inv.status," +
                        "i.id item_id,i.name,i.rarity,i.category,i.current_price,i.previous_price,i.width,i.height,i.image_url," +
                        "i.weapon_type from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "where inv.user_id=? and inv.warehouse_type=? order by inv.pos_y,inv.pos_x,inv.id",
                userId, type);
        result.put("items", stackWarehouseItems(rows));
        return result;
    }

    private List<Map<String, Object>> stackWarehouseItems(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> stacks = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            boolean stackable = row.get("durability_percent") == null;
            String key = stackable
                    ? String.valueOf(row.get("item_id")) + "|" + String.valueOf(row.get("status"))
                    : "inventory|" + String.valueOf(row.get("inventory_id"));
            Map<String, Object> stack = stacks.get(key);
            if (stack == null) {
                stack = new LinkedHashMap<String, Object>(row);
                stack.put("quantity", 1);
                List<Long> ids = new ArrayList<Long>();
                ids.add(((Number) row.get("inventory_id")).longValue());
                stack.put("inventory_ids", ids);
                stacks.put(key, stack);
            } else {
                @SuppressWarnings("unchecked")
                List<Long> ids = (List<Long>) stack.get("inventory_ids");
                ids.add(((Number) row.get("inventory_id")).longValue());
                stack.put("quantity", ((Number) stack.get("quantity")).intValue() + 1);
            }
        }
        return new ArrayList<Map<String, Object>>(stacks.values());
    }

    @Transactional
    public Map<String, Object> moveItem(long inventoryId, int userId, Map<String, Object> body,
                                        String idempotencyKey) {
        String operationType = operationType("move_item", inventoryId);
        if (!claimOperation(userId, operationType, idempotencyKey)) return inventory(inventoryId, userId);
        String targetType = String.valueOf(body.get("target_warehouse"));
        requireWarehouseType(targetType);
        Map<String, Object> item = requiredOne(
                "select inv.*,i.width,i.height from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "where inv.id=? and inv.user_id=? for update", inventoryId, userId);
        if (!"available".equals(String.valueOf(item.get("status")))) {
            throw new IllegalArgumentException("已锁定的物品不能移动");
        }
        String sourceType = String.valueOf(item.get("warehouse_type"));
        Integer requestedX = nullableInt(body.get("pos_x"));
        Integer requestedY = nullableInt(body.get("pos_y"));
        List<Long> movingIds = stackInventoryIds(item, userId);
        Map<String, Object> targetStack = item.get("durability_percent") == null && !sourceType.equals(targetType)
                ? Rows.one(jdbc, "select pos_x,pos_y from escape_inventory_instances where user_id=? " +
                        "and warehouse_type=? and item_id=? and status=? and durability_percent is null " +
                        "order by id limit 1 for update", userId, targetType, item.get("item_id"), item.get("status"))
                : null;
        Placement placement = targetStack == null
                ? findPlacement(userId, targetType, ((Number) item.get("width")).intValue(),
                        ((Number) item.get("height")).intValue(), movingIds, requestedX, requestedY)
                : new Placement(((Number) targetStack.get("pos_x")).intValue(),
                        ((Number) targetStack.get("pos_y")).intValue());
        if (sourceType.equals(targetType)
                && placement.x == ((Number) item.get("pos_x")).intValue()
                && placement.y == ((Number) item.get("pos_y")).intValue()) {
            completeOperation(userId, operationType, idempotencyKey, String.valueOf(inventoryId));
            return inventory(inventoryId, userId);
        }
        for (Long movingId : movingIds) {
            jdbc.update("update escape_inventory_instances set warehouse_type=?,pos_x=?,pos_y=?,version=version+1 where id=? and user_id=?",
                    targetType, placement.x, placement.y, movingId, userId);
        }
        completeOperation(userId, operationType, idempotencyKey, String.valueOf(inventoryId));
        return inventory(inventoryId, userId);
    }

    @Transactional
    public Map<String, Object> sellItem(long inventoryId, int userId, String idempotencyKey) {
        String operationType = operationType("sell_item", inventoryId);
        if (!claimOperation(userId, operationType, idempotencyKey)) return balanceResult(userId);
        Map<String, Object> item = requiredOne(
                "select inv.id,inv.item_id,inv.status,i.name,i.current_price from escape_inventory_instances inv " +
                        "join escape_items i on i.id=inv.item_id where inv.id=? and inv.user_id=? for update",
                inventoryId, userId);
        if (!"available".equals(String.valueOf(item.get("status")))) {
            throw new IllegalArgumentException("已锁定的物品不能出售");
        }
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select cash_balance from escape_user_assets where user_id=? for update", userId);
        BigDecimal price = decimal(item.get("current_price"));
        BigDecimal after = decimal(asset.get("cash_balance")).add(price);
        jdbc.update("delete from escape_inventory_instances where id=? and user_id=?", inventoryId, userId);
        returnItemStock(Collections.singletonMap(((Number) item.get("item_id")).intValue(), 1));
        jdbc.update("update escape_user_assets set cash_balance=?,version=version+1 where user_id=?", after, userId);
        jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                        "values(?,?,?,?,?,?)",
                userId, price, after, "item_sale", String.valueOf(inventoryId), "出售物品：" + item.get("name"));
        completeOperation(userId, operationType, idempotencyKey, String.valueOf(inventoryId));
        return balanceResult(userId);
    }

    @Transactional
    public Map<String, Object> sellAll(int userId, String warehouseType, String idempotencyKey) {
        requireWarehouseType(warehouseType);
        String operationType = "sell_all_" + warehouseType;
        if (!claimOperation(userId, operationType, idempotencyKey)) return balanceResult(userId);
        List<Map<String, Object>> items = Rows.list(jdbc,
                "select inv.id,inv.item_id,i.current_price from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "where inv.user_id=? and inv.warehouse_type=? and inv.status='available' for update",
                userId, warehouseType);
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) total = total.add(decimal(item.get("current_price")));
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select cash_balance from escape_user_assets where user_id=? for update", userId);
        BigDecimal after = decimal(asset.get("cash_balance")).add(total);
        if (!items.isEmpty()) {
            jdbc.update("delete from escape_inventory_instances where user_id=? and warehouse_type=? and status='available'",
                    userId, warehouseType);
            returnItemStock(soldItemCounts(items));
            jdbc.update("update escape_user_assets set cash_balance=?,version=version+1 where user_id=?", after, userId);
            jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                            "values(?,?,?,?,?,?)",
                    userId, total, after, "warehouse_sale", idempotencyKey, "批量出售" + items.size() + "件物品");
        }
        completeOperation(userId, operationType, idempotencyKey, String.valueOf(items.size()));
        Map<String, Object> result = balanceResult(userId);
        result.put("sold_count", items.size());
        result.put("sale_amount", total);
        return result;
    }

    static Map<Integer, Integer> soldItemCounts(List<Map<String, Object>> items) {
        Map<Integer, Integer> counts = new LinkedHashMap<Integer, Integer>();
        for (Map<String, Object> item : items) {
            int itemId = ((Number) item.get("item_id")).intValue();
            counts.put(itemId, counts.containsKey(itemId) ? counts.get(itemId) + 1 : 1);
        }
        return counts;
    }

    void returnItemStock(Map<Integer, Integer> itemCounts) {
        for (Map.Entry<Integer, Integer> item : itemCounts.entrySet()) {
            jdbc.update("update escape_items set stock_quantity=stock_quantity+?,version=version+1 where id=?",
                    item.getValue(), item.getKey());
        }
    }

    public List<Map<String, Object>> products() {
        return Rows.list(jdbc,
                "select p.id,p.name,p.product_type,p.price," +
                        "case when p.product_type='expansion' then p.stock else least(p.stock,i.stock_quantity) end stock," +
                        "p.warehouse_width,p.warehouse_height,p.off_shelf_at," +
                        "i.name item_name,i.rarity,i.category,i.width,i.height,i.image_url,i.weapon_type " +
                        "from escape_shop_products p left join escape_items i on i.id=p.item_id " +
                        "where p.enabled=1 and p.stock>0 and (p.off_shelf_at is null or p.off_shelf_at>now()) " +
                        "and (p.product_type='expansion' or (i.enabled=1 and i.deleted_at is null and i.stock_quantity>0)) " +
                        "order by p.id desc");
    }

    @Transactional
    public Map<String, Object> purchase(int productId, int userId, int quantity, String idempotencyKey) {
        if (quantity <= 0 || quantity > 99) throw new IllegalArgumentException("购买数量必须在1到99之间");
        Map<String, Object> existing = Rows.one(jdbc,
                "select id,total_amount from escape_orders where user_id=? and idempotency_key=?", userId, idempotencyKey);
        if (existing != null) return existing;
        Map<String, Object> product = requiredOne(
                "select p.*,i.width item_width,i.height item_height,i.name item_name,i.category item_category," +
                        "i.stock_quantity item_stock_quantity " +
                        "from escape_shop_products p left join escape_items i on i.id=p.item_id " +
                        "where p.id=? and p.enabled=1 and (p.off_shelf_at is null or p.off_shelf_at>now()) " +
                        "and (p.product_type='expansion' or (i.enabled=1 and i.deleted_at is null)) for update",
                productId);
        if (((Number) product.get("stock")).intValue() < quantity) throw new IllegalArgumentException("商品库存不足");
        String type = String.valueOf(product.get("product_type"));
        if ("expansion".equals(type) && quantity != 1) throw new IllegalArgumentException("仓库扩充商品每次只能购买1件");
        if (!"expansion".equals(type)
                && ((Number) product.get("item_stock_quantity")).intValue() < quantity) {
            throw new IllegalArgumentException("物品库存不足");
        }
        BigDecimal total = decimal(product.get("price")).multiply(BigDecimal.valueOf(quantity));
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select * from escape_user_assets where user_id=? for update", userId);
        BigDecimal before = decimal(asset.get("cash_balance"));
        if (before.compareTo(total) < 0) throw new IllegalArgumentException("现金不足");
        List<Placement> placements = new ArrayList<Placement>();
        if ("expansion".equals(type)) {
            if (product.get("warehouse_width") == null || product.get("warehouse_height") == null) {
                throw new IllegalArgumentException("仓库扩充商品配置不完整");
            }
            int width = ((Number) product.get("warehouse_width")).intValue();
            int height = ((Number) product.get("warehouse_height")).intValue();
            if (width < ((Number) asset.get("personal_width")).intValue()
                    || height < ((Number) asset.get("personal_height")).intValue()) {
                throw new IllegalArgumentException("扩充后的仓库不能小于当前仓库");
            }
            ensureExistingItemsFit(userId, "personal", width, height);
        } else {
            if (product.get("item_id") == null) throw new IllegalArgumentException("商品未关联物品");
            placements = findPlacements(userId, "buffer",
                    ((Number) product.get("item_width")).intValue(),
                    ((Number) product.get("item_height")).intValue(), quantity,
                    "weapon".equals(product.get("item_category")) ? null : ((Number) product.get("item_id")).longValue());
        }
        BigDecimal after = before.subtract(total);
        int stockUpdated = jdbc.update(
                "update escape_shop_products set stock=stock-?,version=version+1 where id=? and stock>=?",
                quantity, productId, quantity);
        if (stockUpdated == 0) throw new IllegalArgumentException("商品库存不足");
        deductItemStock(product, quantity);
        jdbc.update("update escape_user_assets set cash_balance=?,version=version+1 where user_id=?", after, userId);
        if ("expansion".equals(type)) {
            jdbc.update("update escape_user_assets set personal_width=?,personal_height=? where user_id=?",
                    product.get("warehouse_width"), product.get("warehouse_height"), userId);
        } else {
            for (Placement placement : placements) {
                jdbc.update("insert into escape_inventory_instances(user_id,item_id,warehouse_type,pos_x,pos_y," +
                                "durability_percent,status,source_type,source_id) values(?,?,'buffer',?,?,?,'available','shop',?)",
                        userId, product.get("item_id"), placement.x, placement.y,
                        "weapon".equals(product.get("item_category")) ? 100 : null, productId);
            }
        }
        KeyHolder orderKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into escape_orders(user_id,product_id,quantity,total_amount,idempotency_key) values(?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setBigDecimal(4, total);
            ps.setString(5, idempotencyKey);
            return ps;
        }, orderKey);
        jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                        "values(?,?,?,?,?,?)",
                userId, total.negate(), after, "shop_purchase", String.valueOf(orderKey.getKey()),
                "购买商品：" + product.get("name"));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("order_id", orderKey.getKey());
        result.put("total_amount", total);
        result.put("cash_balance", after);
        return result;
    }

    void deductItemStock(Map<String, Object> product, int quantity) {
        if ("expansion".equals(String.valueOf(product.get("product_type")))) return;
        Object itemId = product.get("item_id");
        if (itemId == null) throw new IllegalArgumentException("商品未关联物品");
        int updated = jdbc.update(
                "update escape_items set stock_quantity=stock_quantity-?,version=version+1 " +
                        "where id=? and stock_quantity>=?",
                quantity, itemId, quantity);
        if (updated == 0) throw new IllegalArgumentException("物品库存不足");
    }

    public List<Map<String, Object>> records(int userId, boolean administrator, boolean onlyMine) {
        if (administrator && !onlyMine) {
            return Rows.list(jdbc,
                    "select m.id,m.name,m.season_id,s.name season_name,m.started_at,m.settled_at,count(p.id) participant_count," +
                            "sum(case when p.`escaped`=1 then 1 else 0 end) escaped_count " +
                            "from escape_matches m join escape_match_participants p on p.match_id=m.id " +
                            "left join escape_seasons s on s.id=m.season_id " +
                            "where m.status='settled' group by m.id,s.name order by m.id desc");
        }
        return Rows.list(jdbc,
                "select m.id,m.name,m.season_id,s.name season_name,m.started_at,m.settled_at,p.team_no,p.`escaped`,p.kills," +
                        "p.manual_cash,p.kill_cash,pr.name profession_name,w.name weapon_name " +
                        "from escape_match_participants p join escape_matches m on m.id=p.match_id " +
                        "left join escape_seasons s on s.id=m.season_id " +
                        "left join escape_professions pr on pr.id=p.profession_id left join escape_weapons w on w.id=p.weapon_id " +
                        "where p.user_id=? and m.status='settled' order by m.id desc", userId);
    }

    public Map<String, Object> recordDetail(long matchId, EscapeAccessService.UserContext me) {
        Map<String, Object> own = Rows.one(jdbc,
                "select id from escape_match_participants where match_id=? and user_id=?", matchId, me.userId);
        if (!me.administrator && own == null) throw new SecurityException("无权查看该对局记录");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("match", requiredOne(
                "select m.*,v.name venue_name,s.name season_name from escape_matches m " +
                        "left join venues v on v.id=m.venue_id left join escape_seasons s on s.id=m.season_id " +
                        "where m.id=? and m.status='settled'", matchId));
        if (own == null && me.administrator) {
            result.put("participants", Rows.list(jdbc,
                    "select p.*,coalesce(nullif(u.callsign,''),u.username) callsign,pr.name profession_name,w.name weapon_name " +
                            "from escape_match_participants p join users u on u.id=p.user_id " +
                            "left join escape_professions pr on pr.id=p.profession_id left join escape_weapons w on w.id=p.weapon_id " +
                            "where p.match_id=? order by p.team_no,p.id", matchId));
            result.put("items", Rows.list(jdbc,
                    "select sd.user_id,si.item_id,si.item_name_snapshot,si.rarity_snapshot,si.price_snapshot,si.quantity " +
                            "from escape_match_settlements sh join escape_match_settlement_details sd on sd.settlement_id=sh.id " +
                            "join escape_match_settlement_items si on si.settlement_detail_id=sd.id " +
                            "where sh.match_id=? order by sd.id,si.id", matchId));
        } else {
            result.put("participants", Rows.list(jdbc,
                    "select p.team_no,p.`escaped`,p.kills,p.manual_cash,p.kill_cash,p.joined_at,p.settled_at," +
                            "pr.name profession_name,w.name weapon_name " +
                            "from escape_match_participants p left join escape_professions pr on pr.id=p.profession_id " +
                            "left join escape_weapons w on w.id=p.weapon_id where p.match_id=? and p.user_id=?",
                    matchId, me.userId));
            result.put("items", Rows.list(jdbc,
                    "select si.item_id,si.item_name_snapshot,si.rarity_snapshot,si.price_snapshot,si.quantity " +
                            "from escape_match_settlements sh join escape_match_settlement_details sd on sd.settlement_id=sh.id " +
                            "join escape_match_settlement_items si on si.settlement_detail_id=sd.id " +
                            "where sh.match_id=? and sd.user_id=? order by si.id", matchId, me.userId));
        }
        return result;
    }

    private void ensureAsset(int userId) {
        jdbc.update("insert ignore into escape_user_assets(user_id) values(?)", userId);
    }

    private Map<String, Object> balanceResult(int userId) {
        ensureAsset(userId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("cash_balance", jdbc.queryForObject(
                "select cash_balance from escape_user_assets where user_id=?", BigDecimal.class, userId));
        return result;
    }

    private Map<String, Object> inventory(long inventoryId, int userId) {
        return requiredOne(
                "select inv.*,i.name,i.width,i.height,i.current_price from escape_inventory_instances inv " +
                        "join escape_items i on i.id=inv.item_id where inv.id=? and inv.user_id=?",
                inventoryId, userId);
    }

    private Placement findPlacement(int userId, String warehouseType, int itemWidth, int itemHeight,
                                    List<Long> ignoredIds, Integer requestedX, Integer requestedY) {
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select personal_width,personal_height,buffer_width,buffer_height from escape_user_assets where user_id=? for update",
                userId);
        int gridWidth = ((Number) asset.get(warehouseType + "_width")).intValue();
        int gridHeight = ((Number) asset.get(warehouseType + "_height")).intValue();
        List<GridPacking.Rect> occupied = occupied(userId, warehouseType, ignoredIds);
        GridPacking.Position found;
        if (requestedX != null || requestedY != null) {
            if (requestedX == null || requestedY == null
                    || !GridPacking.fitsAt(gridWidth, gridHeight, requestedX, requestedY, itemWidth, itemHeight, occupied)) {
                throw new IllegalArgumentException("目标位置超出仓库或与其他物品重叠");
            }
            found = new GridPacking.Position(requestedX, requestedY);
        } else {
            found = GridPacking.firstFit(gridWidth, gridHeight, itemWidth, itemHeight, occupied);
            if (found == null) throw new IllegalArgumentException("目标仓库空间不足");
        }
        return new Placement(found.x, found.y);
    }

    private List<Placement> findPlacements(int userId, String warehouseType, int itemWidth, int itemHeight,
                                           int quantity, Long stackItemId) {
        ensureAsset(userId);
        Map<String, Object> asset = requiredOne(
                "select personal_width,personal_height,buffer_width,buffer_height from escape_user_assets where user_id=? for update",
                userId);
        int gridWidth = ((Number) asset.get(warehouseType + "_width")).intValue();
        int gridHeight = ((Number) asset.get(warehouseType + "_height")).intValue();
        List<GridPacking.Rect> occupied = occupied(userId, warehouseType, null);
        List<Placement> result = new ArrayList<Placement>();
        if (stackItemId != null) {
            Map<String, Object> existing = Rows.one(jdbc,
                    "select pos_x,pos_y from escape_inventory_instances where user_id=? and warehouse_type=? " +
                            "and item_id=? and durability_percent is null and status='available' order by id limit 1 for update",
                    userId, warehouseType, stackItemId);
            if (existing != null) {
                Placement stack = new Placement(((Number) existing.get("pos_x")).intValue(),
                        ((Number) existing.get("pos_y")).intValue());
                for (int i = 0; i < quantity; i++) result.add(stack);
                return result;
            }
        }
        for (int i = 0; i < quantity; i++) {
            GridPacking.Position position = GridPacking.firstFit(gridWidth, gridHeight, itemWidth, itemHeight, occupied);
            if (position == null) throw new IllegalArgumentException("缓冲区仓库空间不足");
            occupied.add(new GridPacking.Rect(-(i + 1L), position.x, position.y, itemWidth, itemHeight));
            result.add(new Placement(position.x, position.y));
        }
        return result;
    }

    private List<GridPacking.Rect> occupied(int userId, String warehouseType, List<Long> ignoredIds) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select inv.id,inv.item_id,inv.pos_x,inv.pos_y,inv.durability_percent,inv.status,i.width,i.height " +
                        "from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "where inv.user_id=? and inv.warehouse_type=? for update",
                userId, warehouseType);
        List<GridPacking.Rect> result = new ArrayList<GridPacking.Rect>();
        Set<String> stacked = new HashSet<String>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            if (ignoredIds != null && ignoredIds.contains(id)) continue;
            if (row.get("durability_percent") == null) {
                String key = String.valueOf(row.get("item_id")) + "|" + String.valueOf(row.get("status"));
                if (!stacked.add(key)) continue;
            }
            result.add(new GridPacking.Rect(((Number) row.get("id")).longValue(),
                    ((Number) row.get("pos_x")).intValue(), ((Number) row.get("pos_y")).intValue(),
                    ((Number) row.get("width")).intValue(), ((Number) row.get("height")).intValue()));
        }
        return result;
    }

    private void ensureExistingItemsFit(int userId, String warehouseType, int width, int height) {
        List<GridPacking.Rect> items = occupied(userId, warehouseType, null);
        for (GridPacking.Rect item : items) {
            if (item.x + item.width > width || item.y + item.height > height) {
                throw new IllegalArgumentException("扩充尺寸不能容纳现有物品布局");
            }
        }
    }

    private List<Long> stackInventoryIds(Map<String, Object> item, int userId) {
        if (item.get("durability_percent") != null) {
            return Collections.singletonList(((Number) item.get("id")).longValue());
        }
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select id from escape_inventory_instances where user_id=? and warehouse_type=? and item_id=? " +
                        "and status=? and durability_percent is null for update",
                userId, item.get("warehouse_type"), item.get("item_id"), item.get("status"));
        List<Long> ids = new ArrayList<Long>();
        for (Map<String, Object> row : rows) ids.add(((Number) row.get("id")).longValue());
        return ids;
    }

    private boolean claimOperation(int userId, String operationType, String key) {
        return jdbc.update("insert ignore into escape_operation_idempotency" +
                        "(user_id,operation_type,idempotency_key,result_reference) values(?,?,?,null)",
                userId, operationType, key) == 1;
    }

    private void completeOperation(int userId, String operationType, String key, String reference) {
        jdbc.update("update escape_operation_idempotency set result_reference=? " +
                        "where user_id=? and operation_type=? and idempotency_key=?",
                reference, userId, operationType, key);
    }

    private String operationType(String prefix, long businessId) {
        return prefix + "_" + businessId;
    }

    private Map<String, Object> requiredOne(String sql, Object... args) {
        Map<String, Object> row = Rows.one(jdbc, sql, args);
        if (row == null) throw new IllegalArgumentException("数据不存在或已失效");
        return row;
    }

    private void requireWarehouseType(String type) {
        if (!WAREHOUSE_TYPES.contains(type)) throw new IllegalArgumentException("仓库类型无效");
    }

    private int positiveInt(Object value, String message) {
        try {
            int result = Integer.parseInt(String.valueOf(value));
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    private Integer nullableInt(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("格子坐标无效");
        }
    }

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("物品ID无效");
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static final class Placement {
        private final int x;
        private final int y;

        private Placement(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
