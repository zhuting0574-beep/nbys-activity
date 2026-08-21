package com.nbys.escape.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 逃离西撇镇后台应用服务。所有后台写操作在这里形成事务边界并写入审计日志；
 * Controller 只负责权限入口和 HTTP 契约，避免规则散落。
 */
@Service
public class EscapeAdminService {
    private static final Set<String> CATALOGS = new HashSet<String>(Arrays.asList(
            "items", "products", "seasons", "professions", "weapons"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EscapeAdminService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> matchCounts = requiredOne("select " +
                "sum(case when status='preparing' then 1 else 0 end) preparing," +
                "sum(case when status='in_progress' then 1 else 0 end) in_progress," +
                "sum(case when status='settled' then 1 else 0 end) settled from escape_matches");
        Map<String, Object> cards = requiredOne("select count(*) player_count,coalesce(sum(cash_balance),0) cash_in_circulation " +
                "from escape_user_assets");
        cards.put("buffer_item_count", jdbc.queryForObject(
                "select count(*) from escape_inventory_instances where warehouse_type='buffer'", Number.class));
        cards.put("season_participations", jdbc.queryForObject("select count(*) from escape_match_participants p " +
                "join escape_matches m on m.id=p.match_id join escape_seasons s on s.id=m.season_id " +
                "where s.enabled=1", Number.class));
        cards.put("extraction_rate", jdbc.queryForObject("select coalesce(round(100*sum(case when p.`escaped`=1 then 1 else 0 end)" +
                "/nullif(count(*),0),1),0) from escape_match_participants p join escape_matches m on m.id=p.match_id " +
                "join escape_seasons s on s.id=m.season_id where s.enabled=1 and m.status='settled'", BigDecimal.class));
        result.put("cards", cards);
        result.put("matches", matchCounts);
        result.put("economy", requiredOne("select " +
                "coalesce(sum(case when amount>0 and date(created_at)=current_date then amount else 0 end),0) income_today," +
                "coalesce(sum(case when amount<0 and date(created_at)=current_date then -amount else 0 end),0) expense_today," +
                "coalesce(sum(case when date(created_at)=current_date then amount else 0 end),0) net_today from escape_cash_ledger"));
        result.put("active_season", Rows.one(jdbc, "select * from escape_seasons where enabled=1 " +
                "and current_date between start_date and end_date order by id desc limit 1"));
        result.put("current_matches", matchRows("where m.status in ('preparing','in_progress')", new Object[0]));
        return result;
    }

    public List<Map<String, Object>> matches(String status) {
        if (blank(status)) {
            return matchRows("", new Object[0]);
        }
        return matchRows("where m.status=?", new Object[]{normalizeStatus(status)});
    }

    public List<Map<String, Object>> managedMatches(EscapeAccessService.UserContext actor, Integer seasonId) {
        String seasonWhere;
        Object[] seasonArgs;
        if (seasonId == null) {
            seasonWhere = "where m.season_id=(select id from escape_seasons " +
                    "where enabled=1 and current_date between start_date and end_date " +
                    "order by id desc limit 1)";
            seasonArgs = new Object[0];
        } else {
            seasonWhere = "where m.season_id=?";
            seasonArgs = new Object[]{seasonId};
        }
        if ("superadmin".equals(actor.role)) return matchRows(seasonWhere, seasonArgs);
        Object[] args = Arrays.copyOf(seasonArgs, seasonArgs.length + 1);
        args[args.length - 1] = actor.userId;
        return matchRows(seasonWhere + " and m.created_by=?", args);
    }

    public Map<String, Object> matchOptions() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("venues", Rows.list(jdbc, "select id,name,address from venues order by name,id"));
        result.put("seasons", Rows.list(jdbc, "select id,name,start_date,end_date from escape_seasons " +
                "where enabled=1 and deleted_at is null and current_date between start_date and end_date " +
                "order by start_date desc,id desc"));
        result.put("items", Rows.list(jdbc, "select id,name,rarity,category,weapon_type,image_url,stock_quantity,material_type " +
                "from escape_items where enabled=1 and deleted_at is null and material_type='activity' order by name,id"));
        return result;
    }

    public Map<String, Object> match(long matchId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("match", requiredOne("select m.*,m.team_count*m.team_capacity capacity,v.name venue_name,s.name season_name,s.kill_reward " +
                "from escape_matches m left join venues v on v.id=m.venue_id " +
                "left join escape_seasons s on s.id=m.season_id where m.id=?", matchId));
        result.put("participants", Rows.list(jdbc, "select p.*,coalesce(nullif(u.callsign,''),u.username) callsign," +
                "pr.name profession_name,pr.maintenance_fee,case when w.weapon_type='special' then coalesce(si.name,w.name) else w.name end weapon_name,w.weapon_type,w.usage_fee " +
                "from escape_match_participants p join users u on u.id=p.user_id " +
                "left join escape_professions pr on pr.id=p.profession_id " +
                "left join escape_weapons w on w.id=p.weapon_id " +
                "left join escape_inventory_instances inv on inv.id=p.special_inventory_id " +
                "left join escape_items si on si.id=inv.item_id where p.match_id=? order by p.team_no,p.id", matchId));
        result.put("match_items", matchItems(matchId, false));
        Map<String, Object> settlement = Rows.one(jdbc, "select s.*,coalesce(nullif(u.callsign,''),u.username) settled_by_name " +
                "from escape_match_settlements s left join users u on u.id=s.settled_by " +
                "where s.match_id=? order by s.id desc limit 1", matchId);
        if (settlement != null) {
            List<Map<String, Object>> details = Rows.list(jdbc, "select d.* from escape_match_settlement_details d " +
                    "where d.settlement_id=? order by d.team_no,d.id", settlement.get("id"));
            for (Map<String, Object> detail : details) {
                detail.put("items", Rows.list(jdbc, "select item_id,item_name_snapshot,rarity_snapshot,price_snapshot,quantity " +
                        "from escape_match_settlement_items where settlement_detail_id=? order by id", detail.get("id")));
            }
            settlement.put("participants", details);
            result.put("settlement", settlement);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> confirmSpecialWeapon(long matchId, long participantId, EscapeAccessService.UserContext actor) {
        int changed = jdbc.update("update escape_match_participants p join escape_weapons w on w.id=p.weapon_id set p.special_weapon_confirmed=1,p.version=p.version+1 where p.id=? and p.match_id=? and w.weapon_type='special' and p.loadout_status='locked'", participantId, matchId);
        if (changed != 1) throw new IllegalArgumentException("该成员不是待确认的特殊武器配装");
        audit(actor, "escape:match", "confirm-special-weapon", "participant", participantId, Collections.emptyMap());
        return match(matchId);
    }

    @Transactional
    public Map<String, Object> createMatch(Map<String, Object> body, EscapeAccessService.UserContext actor) {
        String name = text(body, "name", 100);
        int teamCount = atLeast(first(body, "team_count", "squad_count"), 2, "小队数量不能少于2个");
        int teamCapacity = positive(first(body, "team_capacity", "squad_capacity"), "每队人数必须大于0");
        Integer venueId = nullableInt(body.get("venue_id"));
        Integer seasonId = nullableInt(body.get("season_id"));
        if (seasonId == null) throw new IllegalArgumentException("请选择赛季");
        if (venueId != null) requiredOne("select id from venues where id=?", venueId);
        requiredOne("select id from escape_seasons where id=? and enabled=1 and deleted_at is null " +
                "and current_date between start_date and end_date", seasonId);
        long id = insert("insert into escape_matches(season_id,name,venue_id,team_count,team_capacity,status,created_by) " +
                "values(?,?,?,?,?,'preparing',?)", seasonId, name, venueId, teamCount, teamCapacity, actor.userId);
        replaceMatchItems(id, body.get("match_items"));
        audit(actor, "escape:match", "create", "match", id, body);
        return match(id);
    }

    @Transactional
    public Map<String, Object> updateMatch(long matchId, Map<String, Object> body,
                                           EscapeAccessService.UserContext actor) {
        Map<String, Object> match = requiredOne("select * from escape_matches where id=? for update", matchId);
        if (!"preparing".equals(String.valueOf(match.get("status")))) {
            throw new IllegalArgumentException("只有整备中的战局可以编辑");
        }
        String name = body.containsKey("name") ? text(body, "name", 100) : String.valueOf(match.get("name"));
        int teamCount = body.containsKey("team_count") || body.containsKey("squad_count")
                ? atLeast(first(body, "team_count", "squad_count"), 2, "小队数量不能少于2个")
                : number(match.get("team_count"));
        int teamCapacity = body.containsKey("team_capacity") || body.containsKey("squad_capacity")
                ? positive(first(body, "team_capacity", "squad_capacity"), "每队人数必须大于0")
                : number(match.get("team_capacity"));
        Number overflow = jdbc.queryForObject("select count(*) from (select team_no,count(*) c " +
                "from escape_match_participants where match_id=? group by team_no " +
                "having team_no>? or count(*)>?) x", Number.class, matchId, teamCount, teamCapacity);
        if (overflow != null && overflow.intValue() > 0) throw new IllegalArgumentException("新编组容量无法容纳现有参与者");
        Integer venueId = body.containsKey("venue_id") ? nullableInt(body.get("venue_id")) : nullableInt(match.get("venue_id"));
        Integer seasonId = body.containsKey("season_id") ? nullableInt(body.get("season_id")) : nullableInt(match.get("season_id"));
        jdbc.update("update escape_matches set name=?,venue_id=?,season_id=?,team_count=?,team_capacity=?,version=version+1 " +
                "where id=?", name, venueId, seasonId, teamCount, teamCapacity, matchId);
        if (body.containsKey("match_items")) replaceMatchItems(matchId, body.get("match_items"));
        audit(actor, "escape:match", "update", "match", matchId, body);
        return match(matchId);
    }

    @Transactional
    public void cancelMatch(long matchId, EscapeAccessService.UserContext actor) {
        Map<String, Object> match = requiredOne("select status from escape_matches where id=? for update", matchId);
        if (!"preparing".equals(String.valueOf(match.get("status")))) {
            throw new IllegalArgumentException("只有整备中的战局可以取消");
        }
        List<Map<String, Object>> locked = Rows.list(jdbc, "select special_inventory_id from escape_match_participants " +
                "where match_id=? and special_inventory_id is not null for update", matchId);
        for (Map<String, Object> row : locked) {
            jdbc.update("update escape_inventory_instances set status='available',version=version+1 " +
                    "where id=? and status='loadout_locked'", row.get("special_inventory_id"));
        }
        returnUnusedMatchItems(matchId);
        jdbc.update("update escape_matches set status='cancelled',version=version+1 where id=?", matchId);
        audit(actor, "escape:match", "cancel", "match", matchId, Collections.emptyMap());
    }

    @Transactional
    public int closeExpiredSeasonMatches() {
        List<Map<String, Object>> matches = Rows.list(jdbc,
                "select m.id from escape_matches m join escape_seasons s on s.id=m.season_id " +
                        "where (s.end_date<current_date or s.enabled=0) and m.status in ('preparing','in_progress') for update");
        for (Map<String, Object> match : matches) {
            long matchId = ((Number) match.get("id")).longValue();
            List<Map<String, Object>> participants = Rows.list(jdbc,
                    "select special_inventory_id from escape_match_participants " +
                            "where match_id=? and special_inventory_id is not null for update", matchId);
            for (Map<String, Object> participant : participants) {
                jdbc.update("update escape_inventory_instances set status='available',version=version+1 " +
                                "where id=? and status in ('loadout_locked','in_match')",
                        participant.get("special_inventory_id"));
            }
            returnUnusedMatchItems(matchId);
            jdbc.update("update escape_matches set status='cancelled',version=version+1 where id=?", matchId);
        }
        return matches.size();
    }

    @Transactional
    public void deleteCancelledMatch(long matchId, EscapeAccessService.UserContext actor) {
        Map<String, Object> match = requiredOne("select status from escape_matches where id=? for update", matchId);
        if (!"cancelled".equals(String.valueOf(match.get("status")))) {
            throw new IllegalArgumentException("只有已取消的对局可以删除");
        }

        List<Map<String, Object>> participants = Rows.list(jdbc,
                "select special_inventory_id from escape_match_participants " +
                        "where match_id=? and special_inventory_id is not null for update", matchId);
        for (Map<String, Object> participant : participants) {
            jdbc.update("update escape_inventory_instances set status='available',version=version+1 " +
                    "where id=? and status in ('loadout_locked','available')", participant.get("special_inventory_id"));
        }

        jdbc.update("delete from escape_match_settlement_items where settlement_detail_id in " +
                "(select id from escape_match_settlement_details where settlement_id in " +
                "(select id from escape_match_settlements where match_id=?))", matchId);
        jdbc.update("delete from escape_match_settlement_details where settlement_id in " +
                "(select id from escape_match_settlements where match_id=?)", matchId);
        jdbc.update("delete from escape_match_settlements where match_id=?", matchId);
        jdbc.update("delete from escape_match_participants where match_id=?", matchId);
        jdbc.update("delete from escape_match_items where match_id=?", matchId);
        jdbc.update("delete from escape_matches where id=? and status='cancelled'", matchId);
        audit(actor, "escape:match", "delete", "match", matchId, Collections.emptyMap());
    }

    @Transactional
    public Map<String, Object> startMatch(long matchId, String key, EscapeAccessService.UserContext actor) {
        Map<String, Object> existing = operation(actor.userId, "admin_start_" + matchId, key);
        if (existing != null && existing.get("result_reference") != null) return match(matchId);
        claimOperation(actor.userId, "admin_start_" + matchId, key);
        Map<String, Object> match = requiredOne("select * from escape_matches where id=? for update", matchId);
        if ("in_progress".equals(String.valueOf(match.get("status")))) return match(matchId);
        if (!"preparing".equals(String.valueOf(match.get("status")))) throw new IllegalArgumentException("当前战局不能开始");
        List<Map<String, Object>> participants = Rows.list(jdbc, "select p.*,pr.maintenance_fee,w.usage_fee,w.weapon_type " +
                "from escape_match_participants p join escape_professions pr on pr.id=p.profession_id and pr.enabled=1 " +
                "join escape_weapons w on w.id=p.weapon_id and w.enabled=1 " +
                "where p.match_id=? order by p.user_id for update", matchId);
        if (participants.isEmpty()) throw new IllegalArgumentException("战局尚无参与者");
        Number total = jdbc.queryForObject("select count(*) from escape_match_participants where match_id=?", Number.class, matchId);
        if (total == null || total.intValue() != participants.size()) throw new IllegalArgumentException("存在无效兵种或武器配置");
        for (Map<String, Object> p : participants) {
            if (!"locked".equals(String.valueOf(p.get("loadout_status")))) throw new IllegalArgumentException("仍有成员未锁定配装");
            if ("special".equals(String.valueOf(p.get("weapon_type"))) && !truthy(p.get("special_weapon_confirmed"))) throw new IllegalArgumentException("存在特殊武器尚未确认，无法开始战局");
            int userId = number(p.get("user_id"));
            ensureAsset(userId);
            BigDecimal fee = decimal(p.get("maintenance_fee")).add(decimal(p.get("usage_fee")));
            Map<String, Object> asset = requiredOne("select cash_balance from escape_user_assets where user_id=? for update", userId);
            BigDecimal before = decimal(asset.get("cash_balance"));
            BigDecimal after = before.subtract(fee);
            if (after.signum() < 0) throw new IllegalArgumentException("成员现金不足，无法开始战局");
            jdbc.update("update escape_user_assets set cash_balance=?,version=version+1 where user_id=?", after, userId);
            jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                    "values(?,?,?,'match_start_fee',?,'战局开局费用')", userId, fee.negate(), after, matchId + ":" + p.get("id"));
            if ("special".equals(String.valueOf(p.get("weapon_type")))) {
                int changed = jdbc.update("update escape_inventory_instances set status='in_match',version=version+1 " +
                        "where id=? and user_id=? and status='loadout_locked'", p.get("special_inventory_id"), userId);
                if (changed != 1) throw new IllegalArgumentException("特殊武器状态异常，无法开始战局");
            }
            jdbc.update("update escape_match_participants set loadout_status='in_match',version=version+1 where id=?", p.get("id"));
        }
        jdbc.update("update escape_matches set status='in_progress',started_at=now(),version=version+1 where id=?", matchId);
        completeOperation(actor.userId, "admin_start_" + matchId, key, String.valueOf(matchId));
        audit(actor, "escape:match", "start", "match", matchId, Collections.emptyMap());
        return match(matchId);
    }

    public Map<String, Object> settlement(long matchId) {
        Map<String, Object> head = Rows.one(jdbc, "select * from escape_match_settlements where match_id=?", matchId);
        if (head == null) return match(matchId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("match", requiredOne("select m.*,s.kill_reward from escape_matches m " +
                "left join escape_seasons s on s.id=m.season_id where m.id=?", matchId));
        result.put("settlement", head);
        result.put("details", Rows.list(jdbc, "select d.*,coalesce(nullif(u.callsign,''),u.username) callsign " +
                "from escape_match_settlement_details d join users u on u.id=d.user_id " +
                "where d.settlement_id=? order by d.id", head.get("id")));
        result.put("items", Rows.list(jdbc, "select i.* from escape_match_settlement_items i " +
                "join escape_match_settlement_details d on d.id=i.settlement_detail_id " +
                "where d.settlement_id=? order by i.id", head.get("id")));
        result.put("match_items", matchItems(matchId, false));
        return result;
    }

    @Transactional
    public Map<String, Object> settleMatch(long matchId, Map<String, Object> body, String key,
                                           EscapeAccessService.UserContext actor) {
        Map<String, Object> existing = Rows.one(jdbc, "select id from escape_match_settlements where match_id=?", matchId);
        if (existing != null) return settlement(matchId);
        Map<String, Object> match = requiredOne("select * from escape_matches where id=? for update", matchId);
        if (!"in_progress".equals(String.valueOf(match.get("status")))) throw new IllegalArgumentException("只有进行中的战局可以结算");
        List<Map<String, Object>> participants = Rows.list(jdbc, "select p.*,coalesce(nullif(u.callsign,''),u.username) callsign_snapshot," +
                "pr.name profession_name_snapshot,pr.maintenance_fee," +
                "case when w.weapon_type='special' then si.name else w.name end weapon_name_snapshot,w.weapon_type," +
                "coalesce(si.durability_loss_percent,0) durability_loss_percent " +
                "from escape_match_participants p join users u on u.id=p.user_id " +
                "join escape_professions pr on pr.id=p.profession_id join escape_weapons w on w.id=p.weapon_id " +
                "left join escape_inventory_instances sinv on sinv.id=p.special_inventory_id " +
                "left join escape_items si on si.id=sinv.item_id " +
                "where p.match_id=? order by p.id for update", matchId);
        List<Map<String, Object>> inputs = maps(body.get("participants"), "participants不能为空");
        Map<Long, Map<String, Object>> byParticipant = indexSettlementInputs(inputs);
        if (byParticipant.size() != participants.size()) throw new IllegalArgumentException("必须一次性提交全部参与者的结算结果");
        validateSettlementExtractionRules(inputs);
        validateTeamKillLimits(participants, byParticipant);
        Map<Integer, Integer> requestedItems = settlementItemTotals(inputs);
        validateSettlementItemTotals(matchId, requestedItems);
        BigDecimal killReward = BigDecimal.ZERO;
        if (match.get("season_id") != null) {
            Map<String, Object> season = Rows.one(jdbc, "select kill_reward from escape_seasons where id=?", match.get("season_id"));
            if (season != null) killReward = decimal(season.get("kill_reward"));
        }
        long settlementId;
        try {
            settlementId = insert("insert into escape_match_settlements(match_id,season_id,kill_reward_snapshot,settled_by,idempotency_key) " +
                    "values(?,?,?,?,?)", matchId, match.get("season_id"), killReward, actor.userId, key);
        } catch (DuplicateKeyException e) {
            return settlement(matchId);
        }
        for (Map<String, Object> p : participants) {
            long participantId = ((Number) p.get("id")).longValue();
            Map<String, Object> input = byParticipant.get(participantId);
            if (input == null) throw new IllegalArgumentException("结算参与者与战局不匹配");
            boolean escaped = bool(input.get("escaped"));
            int kills = nonNegative(input.get("kills"), "击杀数不能为负");
            BigDecimal manualCash = nonNegativeDecimal(input.get("manual_cash"), "人工现金不能为负");
            BigDecimal killCash = killReward.multiply(BigDecimal.valueOf(kills));
            int userId = number(p.get("user_id"));
            ensureAsset(userId);
            Map<String, Object> asset = requiredOne("select cash_balance from escape_user_assets where user_id=? for update", userId);
            BigDecimal before = decimal(asset.get("cash_balance"));
            BigDecimal after = before.add(manualCash).add(killCash);
            if (after.signum() < 0) throw new IllegalArgumentException("结算后余额不能为负");
            String specialOutcome = settleSpecialWeapon(p, escaped);
            long detailId = insert("insert into escape_match_settlement_details(" +
                    "settlement_id,participant_id,user_id,callsign_snapshot,team_no,profession_id,profession_name_snapshot," +
                    "weapon_id,weapon_name_snapshot,special_inventory_id,`escaped`,kills,manual_cash,kill_cash,cash_before,cash_after," +
                    "special_weapon_outcome) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", settlementId, participantId, userId,
                    p.get("callsign_snapshot"), p.get("team_no"), p.get("profession_id"), p.get("profession_name_snapshot"),
                    p.get("weapon_id"), p.get("weapon_name_snapshot"), p.get("special_inventory_id"), escaped ? 1 : 0,
                    kills, manualCash, killCash, before, after, specialOutcome);
            List<Map<String, Object>> items = input.get("items") == null
                    ? Collections.<Map<String, Object>>emptyList() : maps(input.get("items"), "结算物品格式错误");
            for (Map<String, Object> grant : items) grantSettlementItem(userId, detailId, grant);
            jdbc.update("update escape_user_assets set cash_balance=?,version=version+1 where user_id=?", after, userId);
            BigDecimal income = manualCash.add(killCash);
            if (income.signum() != 0) {
                jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                        "values(?,?,?,'match_settlement',?,'战局结算收入')", userId, income, after, String.valueOf(detailId));
            }
            jdbc.update("update escape_match_participants set `escaped`=?,kills=?,manual_cash=?,kill_cash=?," +
                    "loadout_status='settled',settled_at=now(),version=version+1 where id=?",
                    escaped ? 1 : 0, kills, manualCash, killCash, participantId);
        }
        for (Map.Entry<Integer, Integer> item : requestedItems.entrySet()) {
            jdbc.update("update escape_match_items set consumed_quantity=consumed_quantity+?,updated_at=now() " +
                    "where match_id=? and item_id=?", item.getValue(), matchId, item.getKey());
        }
        returnUnusedMatchItems(matchId);
        jdbc.update("update escape_matches set status='settled',settled_at=now(),version=version+1 where id=?", matchId);
        audit(actor, "escape:settle", "settle", "match", matchId, body);
        return settlement(matchId);
    }

    public List<Map<String, Object>> catalog(String type) {
        return catalog(type, null, null);
    }

    public List<Map<String, Object>> catalog(String type, String keyword, String rarity) {
        Catalog c = catalogDef(type);
        String alias = "products".equals(type) ? "p." : "";
        List<String> conditions = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        if (!blank(keyword)) {
            String value = "%" + keyword.trim() + "%";
            if ("weapons".equals(type)) {
                conditions.add("(case weapon_type when 'knife' then '近战武器' when 'regular' then '普通武器' when 'special' then '特殊武器' else '' end like ?)");
                args.add(value);
            } else {
                conditions.add("(cast(" + alias + "id as char) like ? or " + alias + "name like ?)");
                args.add(value);
                args.add(value);
            }
        }
        if ("items".equals(type) && !blank(rarity)) {
            conditions.add("rarity=?");
            args.add(normalizeRarityFilter(rarity));
        }
        if ("seasons".equals(type)) conditions.add("deleted_at is null");
        if ("weapons".equals(type)) {
            conditions.add("item_id is null");
            conditions.add("id=(select min(canonical.id) from escape_weapons canonical where canonical.item_id is null and canonical.weapon_type=escape_weapons.weapon_type)");
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        if ("products".equals(type)) {
            return decorate(type, Rows.list(jdbc, "select p.*,i.name item_name,i.current_price item_current_price,i.weapon_type item_weapon_type," +
                    "i.stock_quantity item_stock_quantity,i.enabled item_enabled,i.deleted_at item_deleted_at " +
                    "from escape_shop_products p left join escape_items i on i.id=p.item_id" + where +
                    " order by p.id desc", args.toArray()));
        }
        return decorate(type, Rows.list(jdbc, "select * from " + c.table + where + " order by id desc", args.toArray()));
    }

    static String normalizeRarityFilter(String rarity) {
        String value = String.valueOf(rarity).trim().toLowerCase(Locale.ROOT);
        Map<String, String> values = new HashMap<String, String>();
        values.put("超凡", "extraordinary"); values.put("史诗", "epic");
        values.put("精品", "fine"); values.put("普通", "normal");
        if (values.containsKey(value)) value = values.get(value);
        if (!Arrays.asList("extraordinary", "epic", "fine", "normal").contains(value)) {
            throw new IllegalArgumentException("物品品质无效");
        }
        return value;
    }

    public List<Map<String, Object>> itemOptions(String category, Integer includeId) {
        String normalized = category == null ? "" : String.valueOf(category).toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty() && !Arrays.asList("regular", "weapon").contains(normalized)) {
            throw new IllegalArgumentException("物品分类无效");
        }
        StringBuilder sql = new StringBuilder("select * from escape_items where material_type='product' and ");
        List<Object> args = new ArrayList<Object>();
        if (normalized.isEmpty()) {
            sql.append("1=1");
        } else {
            sql.append("category=?");
            args.add(normalized);
        }
        sql.append(" and (enabled=1 and deleted_at is null");
        if (includeId != null && includeId > 0) {
            sql.append(" or id=?");
            args.add(includeId);
        }
        sql.append(") order by name,id");
        return decorate("items", Rows.list(jdbc, sql.toString(), args.toArray()));
    }

    @Transactional
    public Map<String, Object> createCatalog(String type, Map<String, Object> body,
                                             EscapeAccessService.UserContext actor) {
        Catalog c = catalogDef(type);
        validateCatalog(type, body, false);
        List<String> fields = presentFields(c, body);
        if (fields.isEmpty()) throw new IllegalArgumentException("没有可保存的字段");
        StringBuilder sql = new StringBuilder("insert into ").append(c.table).append("(").append(String.join(",", fields))
                .append(") values(").append(placeholders(fields.size())).append(")");
        long id = insert(sql.toString(), values(fields, body).toArray());
        if ("products".equals(type)) syncLinkedItemPrice(id);
        audit(actor, "escape:config", "create", type, id, body);
        return decorate(type, requiredOne("select * from " + c.table + " where id=?", id));
    }

    @Transactional
    public Map<String, Object> createProduct(Map<String, Object> body, EscapeAccessService.UserContext actor) {
        Map<String, Object> input = new LinkedHashMap<String, Object>(body);
        normalizeCatalogInput("products", input);
        Map<String, Object> prepared = prepareProduct(input);
        long id = insertProduct(prepared);
        syncLinkedItemPrice(id);
        audit(actor, "escape:config", "create", "products", id, body);
        return decorate("products", requiredOne("select * from escape_shop_products where id=?", id));
    }

    @Transactional
    public Map<String, Object> updateProduct(long id, Map<String, Object> body, EscapeAccessService.UserContext actor) {
        requiredOne("select id from escape_shop_products where id=? for update", id);
        Map<String, Object> input = new LinkedHashMap<String, Object>(body);
        normalizeCatalogInput("products", input);
        Map<String, Object> prepared = prepareProduct(input);
        List<String> fields = presentFields(catalogDef("products"), prepared);
        if (fields.isEmpty()) throw new IllegalArgumentException("没有可更新的字段");
        List<String> assignments = new ArrayList<String>();
        for (String field : fields) assignments.add(field + "=?");
        List<Object> args = values(fields, prepared);
        args.add(id);
        jdbc.update("update escape_shop_products set " + String.join(",", assignments) + ",version=version+1 where id=?", args.toArray());
        audit(actor, "escape:config", "update", "products", id, body);
        return decorate("products", requiredOne("select * from escape_shop_products where id=?", id));
    }

    private Map<String, Object> prepareProduct(Map<String, Object> input) {
        String type = String.valueOf(input.get("product_type")).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("expansion", "regular", "weapon").contains(type)) {
            throw new IllegalArgumentException("商品类型无效");
        }
        BigDecimal price = nonNegativeDecimal(input.get("price"), "商品价格不能为负");
        Map<String, Object> prepared = new LinkedHashMap<String, Object>();
        prepared.put("name", text(input, "name", 100));
        prepared.put("product_type", type);
        prepared.put("off_shelf_at", input.get("off_shelf_at"));
        if ("expansion".equals(type)) {
            prepared.put("item_id", null);
            prepared.put("price", price);
            prepared.put("stock", 999);
            prepared.put("warehouse_width", positive(input.get("warehouse_width"), "扩容宽度无效"));
            prepared.put("warehouse_height", positive(input.get("warehouse_height"), "扩容高度无效"));
            prepared.put("enabled", !input.containsKey("enabled") || bool(input.get("enabled")));
            return prepared;
        }
        Integer itemId = input.get("item_id") == null || blank(input.get("item_id"))
                ? null : positive(input.get("item_id"), "关联物品无效");
        Map<String, Object> item;
        if (itemId != null) {
            item = requiredOne("select * from escape_items where id=? for update", itemId);
            String expectedCategory = "weapon".equals(type) ? "weapon" : "regular";
            if (!expectedCategory.equals(String.valueOf(item.get("category")))) {
                throw new IllegalArgumentException("关联物品分类与商品类型不匹配");
            }
            if ("weapon".equals(type) && item.get("weapon_type") == null) {
                throw new IllegalArgumentException("关联武器物品缺少武器分类");
            }
            int itemStock = number(item.get("stock_quantity"));
            int stock = nonNegative(input.get("stock"), "商品库存不能为负");
            if (stock > itemStock) throw new IllegalArgumentException("商品库存不能超过物品配置数量");
            BigDecimal itemPrice = decimal(item.get("current_price"));
            validateLinkedProductPrice(price, itemPrice);
            prepared.put("item_id", itemId);
            prepared.put("name", item.get("name"));
            prepared.put("price", price);
            prepared.put("stock", stock);
            prepared.put("enabled", bool(item.get("enabled")) && item.get("deleted_at") == null
                    && (!input.containsKey("enabled") || bool(input.get("enabled"))));
        } else {
            int stock = nonNegative(input.get("stock"), "商品库存不能为负");
            int width = positive(input.get("item_width"), "物品宽度无效");
            int height = positive(input.get("item_height"), "物品高度无效");
            String rarity = input.containsKey("item_rarity") && !blank(input.get("item_rarity"))
                    ? String.valueOf(input.get("item_rarity")) : "normal";
            if (Arrays.asList("普通", "精品", "史诗", "超凡").contains(rarity)) {
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("普通", "normal"); labels.put("精品", "fine"); labels.put("史诗", "epic"); labels.put("超凡", "extraordinary");
                rarity = labels.get(rarity);
            }
            oneOf(rarity, "物品品质无效", "extraordinary", "epic", "fine", "normal");
            Map<String, Object> itemBody = new LinkedHashMap<String, Object>();
            itemBody.put("name", text(input, "name", 100));
            itemBody.put("rarity", rarity);
            itemBody.put("category", "weapon".equals(type) ? "weapon" : "regular");
            if ("weapon".equals(type)) itemBody.put("weapon_type", input.get("weapon_type"));
            itemBody.put("min_price", price); itemBody.put("max_price", price); itemBody.put("current_price", price);
            itemBody.put("width", width); itemBody.put("height", height); itemBody.put("stock_quantity", stock);
            itemBody.put("image_url", input.get("item_image_url")); itemBody.put("enabled", true);
            validateCatalog("items", itemBody, false);
            long createdId = insertCatalogRow(catalogDef("items"), itemBody);
            prepared.put("item_id", createdId);
            prepared.put("name", itemBody.get("name")); prepared.put("price", price); prepared.put("stock", stock);
            prepared.put("enabled", !input.containsKey("enabled") || bool(input.get("enabled")));
        }
        return prepared;
    }

    private long insertProduct(Map<String, Object> body) {
        return insertCatalogRow(catalogDef("products"), body);
    }

    private long insertCatalogRow(Catalog c, Map<String, Object> body) {
        List<String> fields = presentFields(c, body);
        if (fields.isEmpty()) throw new IllegalArgumentException("没有可保存的字段");
        String sql = "insert into " + c.table + "(" + String.join(",", fields) + ") values(" + placeholders(fields.size()) + ")";
        return insert(sql, values(fields, body).toArray());
    }

    @Transactional
    public Map<String, Object> updateCatalog(String type, long id, Map<String, Object> body,
                                             EscapeAccessService.UserContext actor) {
        Catalog c = catalogDef(type);
        requiredOne("select id from " + c.table + " where id=? for update", id);
        validateCatalog(type, body, true);
        List<String> fields = presentFields(c, body);
        if (fields.isEmpty()) throw new IllegalArgumentException("没有可更新的字段");
        List<String> assignments = new ArrayList<String>();
        for (String field : fields) assignments.add(field + "=?");
        List<Object> args = values(fields, body);
        args.add(id);
        jdbc.update("update " + c.table + " set " + String.join(",", assignments) +
                (c.versioned ? ",version=version+1" : "") + " where id=?", args.toArray());
        if ("items".equals(type)) syncLinkedProducts(id);
        if ("products".equals(type)) syncLinkedItemPrice(id);
        audit(actor, "escape:config", "update", type, id, body);
        return decorate(type, requiredOne("select * from " + c.table + " where id=?", id));
    }

    @Transactional
    public void deleteCatalog(String type, long id, EscapeAccessService.UserContext actor) {
        Catalog c = catalogDef(type);
        requiredOne("select id from " + c.table + " where id=? for update", id);
        if ("items".equals(type)) {
            jdbc.update("update escape_items set enabled=0,deleted_at=coalesce(deleted_at,now()),version=version+1 where id=?", id);
            jdbc.update("update escape_shop_products set enabled=0,version=version+1 where item_id=?", id);
        } else if ("seasons".equals(type)) {
            jdbc.update("update escape_seasons set enabled=0,deleted_at=coalesce(deleted_at,now()),version=version+1 where id=?", id);
        } else {
            jdbc.update("update " + c.table + " set enabled=0" + (c.versioned ? ",version=version+1" : "") + " where id=?", id);
        }
        audit(actor, "escape:config", "disable", type, id, Collections.emptyMap());
    }

    private void syncLinkedProducts(long itemId) {
        Map<String, Object> item = requiredOne("select current_price,stock_quantity,enabled,deleted_at from escape_items where id=?", itemId);
        jdbc.update("update escape_shop_products set price=greatest(price,?),stock=least(stock,?),enabled=case when ?=1 and ? is null then enabled else 0 end,version=version+1 where item_id=?",
                item.get("current_price"), item.get("stock_quantity"), item.get("enabled"), item.get("deleted_at"), itemId);
    }

    private void syncLinkedItemPrice(long productId) {
        Map<String, Object> product = Rows.one(jdbc,
                "select item_id,price from escape_shop_products where id=?", productId);
        if (product == null || product.get("item_id") == null || product.get("price") == null) return;
        jdbc.update("update escape_items set previous_price=current_price,current_price=?,version=version+1 where id=?",
                product.get("price"), product.get("item_id"));
    }

    static void validateLinkedProductPrice(BigDecimal productPrice, BigDecimal itemPrice) {
        if (productPrice.compareTo(itemPrice) < 0) {
            throw new IllegalArgumentException("商品售价不能低于物品配置价格");
        }
    }

    @Transactional
    public Map<String, Object> enableSeason(long id, EscapeAccessService.UserContext actor) {
        requiredOne("select id from escape_seasons where id=? and deleted_at is null for update", id);
        jdbc.update("update escape_seasons set enabled=0,version=version+1 where enabled=1 and id<>?", id);
        jdbc.update("update escape_seasons set enabled=1,version=version+1 where id=?", id);
        audit(actor, "escape:config", "enable", "seasons", id, Collections.emptyMap());
        return decorate("seasons", requiredOne("select * from escape_seasons where id=?", id));
    }

    public List<Map<String, Object>> userAssets(String keyword) {
        String value = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<Map<String, Object>> rows = Rows.list(jdbc, "select u.id user_id,u.username,u.callsign,null mobile,a.cash_balance,a.personal_width," +
                "a.personal_height,a.buffer_width,a.buffer_height,a.version," +
                "(select count(*) from escape_inventory_instances i where i.user_id=u.id and i.warehouse_type='personal') personal_item_count," +
                "(select count(*) from escape_inventory_instances i where i.user_id=u.id and i.warehouse_type='buffer') buffer_item_count " +
                "from users u left join escape_user_assets a on a.user_id=u.id " +
                "where ?='%%' or u.username like ? or u.callsign like ? order by u.id desc limit 200", value, value, value);
        for (Map<String, Object> row : rows) {
            row.put("id", row.get("user_id"));
            row.put("cash", row.get("cash_balance"));
            row.put("personal_usage", row.get("personal_item_count"));
            row.put("buffer_usage", row.get("buffer_item_count"));
        }
        return rows;
    }

    public List<Map<String, Object>> itemGrants() {
        return Rows.list(jdbc, "select g.id,g.created_at,coalesce(nullif(u.callsign,''),u.username) callsign," +
                "i.name item_name,g.quantity,g.reason,coalesce(nullif(op.callsign,''),op.username) operator_name " +
                "from escape_item_grants g join users u on u.id=g.user_id join escape_items i on i.id=g.item_id " +
                "join users op on op.id=g.operator_user_id order by g.id desc limit 500");
    }

    @Transactional
    public Map<String, Object> adjustAsset(int userId, Map<String, Object> body, String key,
                                           EscapeAccessService.UserContext actor) {
        ensureAsset(userId);
        if (operation(actor.userId, "asset_adjust_" + userId, key) != null) return asset(userId);
        claimOperation(actor.userId, "asset_adjust_" + userId, key);
        Map<String, Object> current = requiredOne("select * from escape_user_assets where user_id=? for update", userId);
        BigDecimal delta = decimal(body.get("cash_delta"));
        BigDecimal after = decimal(current.get("cash_balance")).add(delta);
        if (after.signum() < 0) throw new IllegalArgumentException("调整后余额不能为负");
        int pw = body.containsKey("personal_width") ? positive(body.get("personal_width"), "个人仓库宽度无效") : number(current.get("personal_width"));
        int ph = body.containsKey("personal_height") ? positive(body.get("personal_height"), "个人仓库高度无效") : number(current.get("personal_height"));
        jdbc.update("update escape_user_assets set cash_balance=?,personal_width=?,personal_height=?,version=version+1 where user_id=?",
                after, pw, ph, userId);
        if (delta.signum() != 0) jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                        "values(?,?,?,'admin_adjust',?,?)", userId, delta, after, key,
                blank(body.get("reason")) ? "后台资产调整" : String.valueOf(body.get("reason")));
        completeOperation(actor.userId, "asset_adjust_" + userId, key, String.valueOf(userId));
        audit(actor, "escape:assets", "adjust", "user_asset", userId, body);
        return asset(userId);
    }

    @Transactional
    public Map<String, Object> grantItems(int userId, Map<String, Object> body, String key,
                                          EscapeAccessService.UserContext actor) {
        if (operation(actor.userId, "item_grant_" + userId, key) != null) return asset(userId);
        claimOperation(actor.userId, "item_grant_" + userId, key);
        int itemId = positive(body.get("item_id"), "物品ID无效");
        int quantity = positive(body.get("quantity"), "发放数量必须大于0");
        if (quantity > 999) throw new IllegalArgumentException("单次发放不能超过999件");
        Map<String, Object> item = requiredOne("select id,width,height from escape_items " +
                "where id=? and enabled=1 and deleted_at is null", itemId);
        ensureAsset(userId);
        List<GridPacking.Rect> occupied = occupied(userId, "buffer");
        Map<String, Object> asset = requiredOne("select buffer_width,buffer_height from escape_user_assets where user_id=? for update", userId);
        int width = number(asset.get("buffer_width"));
        int height = number(asset.get("buffer_height"));
        List<GridPacking.Position> positions = new ArrayList<GridPacking.Position>();
        for (int i = 0; i < quantity; i++) {
            GridPacking.Position p = GridPacking.firstFit(width, height, number(item.get("width")), number(item.get("height")), occupied);
            if (p == null) throw new IllegalArgumentException("用户缓冲区空间不足");
            positions.add(p);
            occupied.add(new GridPacking.Rect(-(i + 1), p.x, p.y, number(item.get("width")), number(item.get("height"))));
        }
        String reason = blank(body.get("reason")) ? "后台物品入库" : String.valueOf(body.get("reason")).trim();
        if (reason.length() > 200) throw new IllegalArgumentException("入库原因不能超过200字符");
        long grantId = insert("insert into escape_item_grants(user_id,item_id,quantity,reason,operator_user_id,idempotency_key) " +
                "values(?,?,?,?,?,?)", userId, itemId, quantity, reason, actor.userId, key);
        for (GridPacking.Position p : positions) {
            jdbc.update("insert into escape_inventory_instances(user_id,item_id,warehouse_type,pos_x,pos_y,status,source_type,source_id) " +
                    "values(?,?,'buffer',?,?,'available','admin_grant',?)", userId, itemId, p.x, p.y, grantId);
        }
        completeOperation(actor.userId, "item_grant_" + userId, key, String.valueOf(grantId));
        audit(actor, "escape:assets", "grant_items", "user_asset", userId, body);
        Map<String, Object> result = asset(userId);
        result.put("granted_quantity", quantity);
        return result;
    }

    public List<Map<String, Object>> audit(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        return Rows.list(jdbc, "select a.*,coalesce(nullif(u.callsign,''),u.username) actor_name " +
                "from escape_admin_audit_log a left join users u on u.id=a.actor_user_id order by a.id desc limit ?", safe);
    }

    private String settleSpecialWeapon(Map<String, Object> p, boolean escaped) {
        if (!"special".equals(String.valueOf(p.get("weapon_type"))) || p.get("special_inventory_id") == null) return "not_used";
        Map<String, Object> inventory = requiredOne("select durability_percent from escape_inventory_instances " +
                "where id=? and user_id=? and status='in_match' for update", p.get("special_inventory_id"), p.get("user_id"));
        int durability = inventory.get("durability_percent") == null ? 100 : number(inventory.get("durability_percent"));
        int after = durability - number(p.get("durability_loss_percent"));
        if (!escaped || after <= 0) {
            jdbc.update("delete from escape_inventory_instances where id=?", p.get("special_inventory_id"));
            return "destroyed";
        }
        jdbc.update("update escape_inventory_instances set status='available',durability_percent=?,version=version+1 where id=?",
                after, p.get("special_inventory_id"));
        return "returned";
    }

    private void grantSettlementItem(int userId, long detailId, Map<String, Object> grant) {
        int itemId = positive(grant.get("item_id"), "结算物品ID无效");
        int quantity = positive(grant.get("quantity"), "结算物品数量必须大于0");
        if (quantity > 999) throw new IllegalArgumentException("单项结算物品不能超过999件");
        Map<String, Object> item = requiredOne("select * from escape_items where id=?", itemId);
        ensureAsset(userId);
        for (int i = 0; i < quantity; i++) {
            GridPacking.Position position = firstFit(userId, "buffer", number(item.get("width")), number(item.get("height")));
            if (position == null) throw new IllegalArgumentException("用户缓冲区空间不足，无法完成结算");
            jdbc.update("insert into escape_inventory_instances(user_id,item_id,warehouse_type,pos_x,pos_y,status,source_type,source_id) " +
                    "values(?,?,'buffer',?,?,'available','match_settlement',?)",
                    userId, itemId, position.x, position.y, detailId);
        }
        jdbc.update("insert into escape_match_settlement_items(settlement_detail_id,item_id,item_name_snapshot," +
                "rarity_snapshot,price_snapshot,quantity) values(?,?,?,?,?,?)", detailId, itemId, item.get("name"),
                item.get("rarity"), item.get("current_price"), quantity);
    }

    private List<Map<String, Object>> matchItems(long matchId, boolean lock) {
        return Rows.list(jdbc, "select mi.id,mi.match_id,mi.item_id,i.name,i.rarity,i.category,i.material_type,i.image_url," +
                "i.stock_quantity,mi.allocated_quantity,mi.consumed_quantity,mi.returned_quantity," +
                "(mi.allocated_quantity-mi.consumed_quantity-mi.returned_quantity) remaining_quantity " +
                "from escape_match_items mi join escape_items i on i.id=mi.item_id where mi.match_id=? " +
                "order by i.name,i.id" + (lock ? " for update" : ""), matchId);
    }

    private void replaceMatchItems(long matchId, Object value) {
        Map<Integer, Integer> requested = itemQuantities(value, "带入本局的物品格式错误");
        List<Map<String, Object>> existingRows = matchItems(matchId, true);
        Map<Integer, Integer> existing = new HashMap<Integer, Integer>();
        for (Map<String, Object> row : existingRows) {
            if (number(row.get("consumed_quantity")) > 0 || number(row.get("returned_quantity")) > 0) {
                throw new IllegalArgumentException("已有物品被结算或归还，不能修改战局物品");
            }
            existing.put(number(row.get("item_id")), number(row.get("allocated_quantity")));
        }

        SortedSet<Integer> itemIds = new TreeSet<Integer>();
        itemIds.addAll(existing.keySet());
        itemIds.addAll(requested.keySet());
        for (Integer itemId : itemIds) {
            Map<String, Object> item = requiredOne("select id,name,stock_quantity,enabled,deleted_at,material_type " +
                    "from escape_items where id=? for update", itemId);
            int oldQuantity = existing.containsKey(itemId) ? existing.get(itemId) : 0;
            int newQuantity = requested.containsKey(itemId) ? requested.get(itemId) : 0;
            if (!"activity".equals(String.valueOf(item.get("material_type")))) throw new IllegalArgumentException("物品“" + item.get("name") + "”不是活动物资，不能放入对局");
            if (newQuantity > oldQuantity && (!bool(item.get("enabled")) || item.get("deleted_at") != null)) {
                throw new IllegalArgumentException("物品“" + item.get("name") + "”已停用，不能增加数量");
            }
        }
        jdbc.update("delete from escape_match_items where match_id=?", matchId);
        for (Map.Entry<Integer, Integer> item : requested.entrySet()) {
            jdbc.update("insert into escape_match_items(match_id,item_id,allocated_quantity) values(?,?,?)",
                    matchId, item.getKey(), item.getValue());
        }
    }

    private void returnUnusedMatchItems(long matchId) {
        List<Map<String, Object>> rows = matchItems(matchId, true);
        for (Map<String, Object> row : rows) {
            int remaining = unconsumedQuantity(number(row.get("allocated_quantity")),
                    number(row.get("consumed_quantity")), number(row.get("returned_quantity")));
            if (remaining <= 0) continue;
            int itemId = number(row.get("item_id"));
            jdbc.update("update escape_match_items set returned_quantity=returned_quantity+?,updated_at=now() " +
                    "where match_id=? and item_id=?", remaining, matchId, itemId);
        }
    }

    static int unconsumedQuantity(int allocated, int consumed, int returned) {
        return Math.max(0, allocated - consumed - returned);
    }

    private void validateSettlementItemTotals(long matchId, Map<Integer, Integer> requested) {
        List<Map<String, Object>> rows = matchItems(matchId, true);
        Map<Integer, Map<String, Object>> available = new HashMap<Integer, Map<String, Object>>();
        for (Map<String, Object> row : rows) available.put(number(row.get("item_id")), row);
        for (Map.Entry<Integer, Integer> item : requested.entrySet()) {
            Map<String, Object> pool = available.get(item.getKey());
            if (pool == null) throw new IllegalArgumentException("物品ID " + item.getKey() + " 未带入本局，不能结算");
            int remaining = number(pool.get("remaining_quantity"));
            if (item.getValue() > remaining) {
                throw new IllegalArgumentException("物品“" + pool.get("name") + "”数量不足，本局剩余 " + remaining);
            }
        }
    }

    static Map<Integer, Integer> settlementItemTotals(List<Map<String, Object>> participants) {
        Map<Integer, Integer> totals = new LinkedHashMap<Integer, Integer>();
        for (Map<String, Object> participant : participants) {
            Object rawItems = participant.get("items");
            if (rawItems == null) continue;
            if (!(rawItems instanceof List)) throw new IllegalArgumentException("结算物品格式错误");
            for (Object rawItem : (List<?>) rawItems) {
                if (!(rawItem instanceof Map)) throw new IllegalArgumentException("结算物品格式错误");
                Map<?, ?> item = (Map<?, ?>) rawItem;
                int itemId = positiveValue(item.get("item_id"), "结算物品ID无效");
                int quantity = positiveValue(item.get("quantity"), "结算物品数量必须大于0");
                if (quantity > 999) throw new IllegalArgumentException("单项结算物品不能超过999件");
                int total = totals.containsKey(itemId) ? totals.get(itemId) + quantity : quantity;
                if (total > 999999) throw new IllegalArgumentException("结算物品总数量过大");
                totals.put(itemId, total);
            }
        }
        return totals;
    }

    static void validateSettlementExtractionRules(List<Map<String, Object>> participants) {
        for (Map<String, Object> participant : participants) {
            Object rawItems = participant.get("items");
            if (rawItems == null) continue;
            if (!(rawItems instanceof List)) throw new IllegalArgumentException("结算物品格式错误");
            if (!((List<?>) rawItems).isEmpty() && !booleanValue(participant.get("escaped"))) {
                throw new IllegalArgumentException("未成功撤离的参与者不能带出物资");
            }
        }
    }

    static void validateTeamKillLimits(List<Map<String, Object>> participants,
                                       Map<Long, Map<String, Object>> inputs) {
        int totalParticipants = participants.size();
        Map<Integer, Integer> teamSizes = new LinkedHashMap<Integer, Integer>();
        Map<Integer, Long> teamKills = new LinkedHashMap<Integer, Long>();
        for (Map<String, Object> participant : participants) {
            long participantId = ((Number) participant.get("id")).longValue();
            Map<String, Object> input = inputs.get(participantId);
            if (input == null) throw new IllegalArgumentException("结算参与者与战局不匹配");
            Object rawTeamNo = participant.get("team_no");
            if (!(rawTeamNo instanceof Number) || ((Number) rawTeamNo).intValue() < 1) {
                throw new IllegalArgumentException("存在未分配小队的参与者，无法结算");
            }
            int teamNo = ((Number) rawTeamNo).intValue();
            int kills = nonNegativeValue(input.get("kills"), "击杀数必须为非负整数");
            teamSizes.put(teamNo, teamSizes.containsKey(teamNo) ? teamSizes.get(teamNo) + 1 : 1);
            teamKills.put(teamNo, teamKills.containsKey(teamNo) ? teamKills.get(teamNo) + kills : (long) kills);
        }
        for (Map.Entry<Integer, Integer> team : teamSizes.entrySet()) {
            int teamNo = team.getKey();
            int limit = totalParticipants - team.getValue();
            long kills = teamKills.get(teamNo);
            if (kills > limit) {
                throw new IllegalArgumentException("第 " + teamNo + " 小队击杀数合计为 " + kills + "，不能超过 " + limit);
            }
        }
    }

    private Map<Integer, Integer> itemQuantities(Object value, String message) {
        Map<Integer, Integer> result = new LinkedHashMap<Integer, Integer>();
        if (value == null) return result;
        for (Map<String, Object> row : maps(value, message)) {
            int itemId = positive(row.get("item_id"), "请选择有效物品");
            int quantity = positive(row.get("quantity"), "带入数量必须大于0");
            if (quantity > 999999) throw new IllegalArgumentException("单项带入数量过大");
            if (result.put(itemId, quantity) != null) throw new IllegalArgumentException("同一物品不能重复选择");
        }
        return result;
    }

    private static int positiveValue(Object value, String message) {
        try {
            int result = Integer.parseInt(String.valueOf(value));
            if (result <= 0) throw new IllegalArgumentException(message);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    private static int nonNegativeValue(Object value, String message) {
        int result;
        try {
            result = Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
        if (result < 0) throw new IllegalArgumentException(message);
        return result;
    }

    static Map<Long, Map<String, Object>> indexSettlementInputs(List<Map<String, Object>> inputs) {
        Map<Long, Map<String, Object>> indexed = new LinkedHashMap<Long, Map<String, Object>>();
        for (Map<String, Object> input : inputs) {
            Object value = input.get("participant_id");
            long id;
            try {
                id = Long.parseLong(String.valueOf(value));
            } catch (Exception e) {
                throw new IllegalArgumentException("participant_id无效");
            }
            if (id <= 0 || indexed.put(id, input) != null) throw new IllegalArgumentException("结算参与者重复或无效");
        }
        return indexed;
    }

    private GridPacking.Position firstFit(int userId, String warehouse, int itemWidth, int itemHeight) {
        Map<String, Object> asset = requiredOne("select buffer_width,buffer_height from escape_user_assets " +
                "where user_id=? for update", userId);
        return GridPacking.firstFit(number(asset.get("buffer_width")), number(asset.get("buffer_height")),
                itemWidth, itemHeight, occupied(userId, warehouse));
    }

    private List<GridPacking.Rect> occupied(int userId, String warehouse) {
        List<Map<String, Object>> rows = Rows.list(jdbc, "select inv.id,inv.pos_x,inv.pos_y,i.width,i.height " +
                "from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                "where inv.user_id=? and inv.warehouse_type=? for update", userId, warehouse);
        List<GridPacking.Rect> result = new ArrayList<GridPacking.Rect>();
        for (Map<String, Object> row : rows) result.add(new GridPacking.Rect(((Number) row.get("id")).longValue(),
                number(row.get("pos_x")), number(row.get("pos_y")), number(row.get("width")), number(row.get("height"))));
        return result;
    }

    private Map<String, Object> asset(int userId) {
        Map<String, Object> result = requiredOne("select a.*,u.username,u.callsign from escape_user_assets a " +
                "join users u on u.id=a.user_id where a.user_id=?", userId);
        result.put("inventory_count", jdbc.queryForObject("select count(*) from escape_inventory_instances where user_id=?",
                Number.class, userId));
        return result;
    }

    private void validateCatalog(String type, Map<String, Object> body, boolean partial) {
        normalizeCatalogInput(type, body);
        if (!partial || body.containsKey("name")) text(body, "name", 100);
        if ("items".equals(type)) {
            if (!partial || body.containsKey("material_type")) oneOf(body.get("material_type"), "物资类型无效", "activity", "product");
            String materialType = String.valueOf(body.get("material_type"));
            if (partial && !body.containsKey("material_type")) materialType = null;
            if (!partial || body.containsKey("rarity")) oneOf(body.get("rarity"), "物品品质无效",
                    "extraordinary", "epic", "fine", "normal");
            if (!partial || body.containsKey("category")) oneOf(body.get("category"), "物品分类无效", "regular", "weapon");
            if (body.containsKey("weapon_type")) oneOf(body.get("weapon_type"), "武器分类无效", "knife", "regular", "special");
            if (!partial && "weapon".equals(String.valueOf(body.get("category"))) && !body.containsKey("weapon_type")) {
                throw new IllegalArgumentException("请选择武器分类");
            }
            if ("regular".equals(String.valueOf(body.get("category")))) body.put("weapon_type", null);
            if (body.containsKey("durability_loss_percent") && body.get("durability_loss_percent") != null) {
                int loss = nonNegative(body.get("durability_loss_percent"), "耐久损耗不能为负");
                if (loss > 100) throw new IllegalArgumentException("耐久损耗不能超过100%");
            }
            if (!partial && "special".equals(String.valueOf(body.get("weapon_type")))
                    && !body.containsKey("durability_loss_percent")) {
                throw new IllegalArgumentException("请配置每局耐久损耗");
            }
            if (body.containsKey("weapon_type") && !"special".equals(String.valueOf(body.get("weapon_type")))) {
                body.put("durability_loss_percent", null);
            }
            if ("activity".equals(materialType)) {
                body.put("min_price", BigDecimal.ZERO); body.put("max_price", BigDecimal.ZERO);
                body.put("current_price", BigDecimal.ZERO); body.put("stock_quantity", 0);
            } else if (!partial || "product".equals(materialType)) validatePriceRange(body, partial);
            if (!partial || body.containsKey("width")) positive(body.get("width"), "物品宽度无效");
            if (!partial || body.containsKey("height")) positive(body.get("height"), "物品高度无效");
            if (!"activity".equals(materialType) && (!partial || body.containsKey("stock_quantity"))) nonNegative(body.get("stock_quantity"), "物品数量不能为负");
        } else if ("products".equals(type)) {
            if (!partial || body.containsKey("product_type")) oneOf(body.get("product_type"), "商品类型无效",
                    "expansion", "regular", "weapon");
            if (!partial || body.containsKey("price")) nonNegativeDecimal(body.get("price"), "商品价格不能为负");
            if (!partial || body.containsKey("stock")) nonNegative(body.get("stock"), "库存不能为负");
        } else if ("seasons".equals(type)) {
            if (!partial && (!body.containsKey("start_date") || !body.containsKey("end_date"))) {
                throw new IllegalArgumentException("赛季起止日期不能为空");
            }
            if (!partial || body.containsKey("kill_reward")) nonNegativeDecimal(body.get("kill_reward"), "击杀奖励不能为负");
        } else if ("professions".equals(type)) {
            if (!partial || body.containsKey("health")) positive(body.get("health"), "生命值必须大于0");
            if (!partial || body.containsKey("maintenance_fee")) nonNegativeDecimal(body.get("maintenance_fee"), "维护费不能为负");
        } else if ("weapons".equals(type)) {
            if (!partial || body.containsKey("weapon_type")) oneOf(body.get("weapon_type"), "武器类型无效",
                    "knife", "regular", "special");
            if (!partial || body.containsKey("usage_fee")) nonNegativeDecimal(body.get("usage_fee"), "使用费不能为负");
            if (!partial || body.containsKey("durability_loss_percent")) {
                int loss = nonNegative(body.get("durability_loss_percent"), "耐久损耗不能为负");
                if (loss > 100) throw new IllegalArgumentException("耐久损耗不能超过100%");
            }
        }
    }

    private void validatePriceRange(Map<String, Object> body, boolean partial) {
        if (!partial) {
            BigDecimal min = nonNegativeDecimal(body.get("min_price"), "最低价格不能为负");
            BigDecimal max = nonNegativeDecimal(body.get("max_price"), "最高价格不能为负");
            if (max.compareTo(min) < 0) throw new IllegalArgumentException("最高价格不能低于最低价格");
            if (!body.containsKey("current_price")) body.put("current_price", min);
        }
    }

    private Catalog catalogDef(String type) {
        if (!CATALOGS.contains(type)) throw new IllegalArgumentException("后台资源类型无效");
        if ("items".equals(type)) return new Catalog("escape_items", true,
                "name", "rarity", "category", "material_type", "weapon_type", "durability_loss_percent", "min_price", "max_price", "current_price", "previous_price",
                "width", "height", "image_url", "stock_quantity", "enabled");
        if ("products".equals(type)) return new Catalog("escape_shop_products", true,
                "name", "product_type", "item_id", "price", "stock", "warehouse_width", "warehouse_height",
                "off_shelf_at", "enabled");
        if ("seasons".equals(type)) return new Catalog("escape_seasons", true,
                "name", "start_date", "end_date", "enabled", "kill_reward");
        if ("professions".equals(type)) return new Catalog("escape_professions", true,
                "name", "health", "maintenance_fee", "knife_only", "enabled", "sort_order");
        return new Catalog("escape_weapons", true,
                "item_id", "name", "weapon_type", "usage_fee", "durability_loss_percent", "enabled", "sort_order");
    }

    private void normalizeCatalogInput(String type, Map<String, Object> body) {
        alias(body, "off_shelf_at", "offline_at");
        alias(body, "start_date", "start_at");
        alias(body, "end_date", "end_at");
        alias(body, "maintenance_fee", "maintenance_cost");
        alias(body, "usage_fee", "cost");
        if ("items".equals(type) && body.containsKey("rarity")) {
            Map<String, String> values = new HashMap<String, String>();
            values.put("超凡", "extraordinary"); values.put("史诗", "epic");
            values.put("精品", "fine"); values.put("普通", "normal");
            if (values.containsKey(String.valueOf(body.get("rarity")))) body.put("rarity", values.get(String.valueOf(body.get("rarity"))));
        }
        if ("products".equals(type) && body.containsKey("product_type")) {
            String value = String.valueOf(body.get("product_type")).toLowerCase(Locale.ROOT);
            if ("item".equals(value)) value = "regular";
            body.put("product_type", value);
        }
        if ("weapons".equals(type) && body.containsKey("weapon_type")) {
            body.put("weapon_type", String.valueOf(body.get("weapon_type")).toLowerCase(Locale.ROOT));
        }
        if ("weapons".equals(type) && body.containsKey("max_durability") && !body.containsKey("durability_loss_percent")) {
            body.put("durability_loss_percent", Math.max(0, 100 - numberValue(body.get("max_durability"), "耐久值无效")));
        }
    }

    private List<Map<String, Object>> matchRows(String where, Object[] args) {
        return Rows.list(jdbc, "select m.*,v.name venue_name,s.name season_name," +
                "(select count(*) from escape_match_participants p where p.match_id=m.id) participant_count," +
                "(select count(*) from escape_match_participants p where p.match_id=m.id and p.loadout_status='locked') locked_count," +
                "(m.team_count*m.team_capacity) capacity,m.team_count squad_count,m.team_capacity squad_capacity,m.started_at start_at " +
                "from escape_matches m left join venues v on v.id=m.venue_id left join escape_seasons s on s.id=m.season_id " +
                where + " order by m.id desc", args);
    }

    private String normalizeStatus(String status) {
        String value = String.valueOf(status).toLowerCase(Locale.ROOT);
        if ("finished".equals(value)) return "settled";
        return value;
    }

    private List<Map<String, Object>> decorate(String type, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) decorate(type, row);
        return rows;
    }

    private Map<String, Object> decorate(String type, Map<String, Object> row) {
        if ("items".equals(type)) {
            Map<String, String> labels = new HashMap<String, String>();
            labels.put("extraordinary", "超凡"); labels.put("epic", "史诗");
            labels.put("fine", "精品"); labels.put("normal", "普通");
            row.put("rarity_code", row.get("rarity"));
            row.put("rarity", labels.containsKey(String.valueOf(row.get("rarity")))
                    ? labels.get(String.valueOf(row.get("rarity"))) : row.get("rarity"));
            row.put("today_price", row.get("current_price"));
        } else if ("products".equals(type)) {
            row.put("offline_at", row.get("off_shelf_at"));
        } else if ("seasons".equals(type)) {
            row.put("start_at", row.get("start_date"));
            row.put("end_at", row.get("end_date"));
        } else if ("professions".equals(type)) {
            row.put("maintenance_cost", row.get("maintenance_fee"));
        } else if ("weapons".equals(type)) {
            row.put("cost", row.get("usage_fee"));
            row.put("max_durability", 100 - number(row.get("durability_loss_percent")));
        }
        return row;
    }

    private void alias(Map<String, Object> body, String target, String source) {
        if (!body.containsKey(target) && body.containsKey(source)) body.put(target, body.get(source));
    }

    private Object first(Map<String, Object> body, String first, String second) {
        return body.containsKey(first) ? body.get(first) : body.get(second);
    }

    private void audit(EscapeAccessService.UserContext actor, String permission, String action,
                       String entity, Object entityId, Object snapshot) {
        jdbc.update("insert into escape_admin_audit_log(actor_user_id,permission_code,action,entity_type,entity_id,request_snapshot) " +
                "values(?,?,?,?,?,?)", actor.userId, permission, action, entity,
                entityId == null ? null : String.valueOf(entityId), json(snapshot));
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("审计快照无法序列化");
        }
    }

    private Map<String, Object> operation(int userId, String type, String key) {
        return Rows.one(jdbc, "select * from escape_operation_idempotency where user_id=? and operation_type=? and idempotency_key=?",
                userId, type, key);
    }

    private void claimOperation(int userId, String type, String key) {
        jdbc.update("insert ignore into escape_operation_idempotency(user_id,operation_type,idempotency_key) values(?,?,?)",
                userId, type, key);
    }

    private void completeOperation(int userId, String type, String key, String reference) {
        jdbc.update("update escape_operation_idempotency set result_reference=? where user_id=? and operation_type=? and idempotency_key=?",
                reference, userId, type, key);
    }

    private void ensureAsset(int userId) {
        jdbc.update("insert ignore into escape_user_assets(user_id) values(?)", userId);
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("写入数据失败");
        return key.getKey().longValue();
    }

    private Map<String, Object> requiredOne(String sql, Object... args) {
        Map<String, Object> row = Rows.one(jdbc, sql, args);
        if (row == null) throw new IllegalArgumentException("数据不存在或已失效");
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value, String message) {
        if (!(value instanceof List)) throw new IllegalArgumentException(message);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) throw new IllegalArgumentException(message);
            result.add((Map<String, Object>) item);
        }
        return result;
    }

    private String text(Map<String, Object> body, String field, int max) {
        String value = body.get(field) == null ? "" : String.valueOf(body.get(field)).trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(field + "不能为空且不能超过" + max + "字符");
        return value;
    }

    private int positive(Object value, String message) {
        int result = numberValue(value, message);
        if (result <= 0) throw new IllegalArgumentException(message);
        return result;
    }

    private int atLeast(Object value, int minimum, String message) {
        int result = numberValue(value, message);
        if (result < minimum) throw new IllegalArgumentException(message);
        return result;
    }

    private int nonNegative(Object value, String message) {
        int result = numberValue(value, message);
        if (result < 0) throw new IllegalArgumentException(message);
        return result;
    }

    private int numberValue(Object value, String message) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    private int number(Object value) {
        return ((Number) value).intValue();
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private Integer nullableInt(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        return numberValue(value, "ID无效");
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("金额格式无效");
        }
    }

    private BigDecimal nonNegativeDecimal(Object value, String message) {
        BigDecimal result = decimal(value);
        if (result.signum() < 0) throw new IllegalArgumentException(message);
        return result;
    }

    private boolean bool(Object value) {
        return booleanValue(value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value)) || "0".equals(String.valueOf(value))) return false;
        throw new IllegalArgumentException("escaped必须为布尔值");
    }

    private void oneOf(Object value, String message, String... allowed) {
        if (!Arrays.asList(allowed).contains(String.valueOf(value))) throw new IllegalArgumentException(message);
    }

    private boolean blank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private List<String> presentFields(Catalog c, Map<String, Object> body) {
        List<String> result = new ArrayList<String>();
        for (String field : c.fields) if (body.containsKey(field)) result.add(field);
        return result;
    }

    private List<Object> values(List<String> fields, Map<String, Object> body) {
        List<Object> result = new ArrayList<Object>();
        for (String field : fields) result.add(body.get(field));
        return result;
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private static final class Catalog {
        private final String table;
        private final boolean versioned;
        private final List<String> fields;

        private Catalog(String table, boolean versioned, String... fields) {
            this.table = table;
            this.versioned = versioned;
            this.fields = Arrays.asList(fields);
        }
    }
}
