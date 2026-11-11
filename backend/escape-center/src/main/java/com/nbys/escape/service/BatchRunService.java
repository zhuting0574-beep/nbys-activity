package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchRunService {
    private final JdbcTemplate jdbc;
    private final EscapePriceRefreshWorker prices;
    private final EscapeShopStockWorker shopStock;
    private final BufferLiquidationScheduler buffer;
    private final ObjectMapper json;
    private final EscapeAdminService admin;

    public BatchRunService(JdbcTemplate jdbc, EscapePriceRefreshWorker prices,
                           EscapeShopStockWorker shopStock, BufferLiquidationScheduler buffer, ObjectMapper json, EscapeAdminService admin) {
        this.jdbc = jdbc;
        this.prices = prices;
        this.shopStock = shopStock;
        this.buffer = buffer;
        this.json = json;
        this.admin = admin;
    }

    public List<Map<String, Object>> tasks() {
        return Rows.list(jdbc, "select task_key,task_name,enabled,cycle,start_at from batch_task_configs order by task_name");
    }

    public List<Map<String, Object>> logs(String taskKey) {
        List<Map<String, Object>> result = Rows.list(jdbc,
                "select id,task_key,task_name,business_date,trigger_type,status,started_at,finished_at,message,result_json,created_by " +
                        "from batch_run_logs " + (taskKey == null || taskKey.trim().isEmpty() ? "" : "where task_key=? ") + "order by id desc limit 500",
                taskKey == null || taskKey.trim().isEmpty() ? new Object[]{} : new Object[]{taskKey});
        for (Map<String, Object> row : result) row.put("result", row.remove("result_json"));
        return result;
    }

    public Map<String, Object> run(String taskKey, LocalDate businessDate, Integer actorId) {
        return execute(taskKey, businessDate, actorId, "manual", null);
    }

    public Map<String, Object> runScheduled(String taskKey, LocalDateTime occurrence) {
        return execute(taskKey, occurrence.toLocalDate(), null, "scheduled", occurrence.toString().replace('T', ' '));
    }

    private Map<String, Object> execute(String taskKey, LocalDate businessDate, Integer actorId, String trigger, String slot) {
        String name = taskName(taskKey);
        if (!isEnabled(taskKey)) {
            if ("scheduled".equals(trigger)) return null;
            throw new IllegalStateException("跑批任务未开启");
        }
        long id = insertRunning(taskKey, name, businessDate, actorId, trigger, slot);
        if (id == 0) return null;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            if ("price-refresh".equals(taskKey)) prices.refresh(businessDate.toString());
            else if ("shop-stock-plan".equals(taskKey)) shopStock.plan(businessDate);
            else if ("shop-stock-replenish".equals(taskKey)) shopStock.execute(businessDate);
            else if ("buffer-liquidation".equals(taskKey)) buffer.liquidateBuffers();
            else if ("expired-season-cleanup".equals(taskKey)) result.put("cleared_seasons", admin.clearEndedSeasonInventories());
            else throw new IllegalArgumentException("未知跑批任务");
            result.put("business_date", businessDate.toString());
            result.put("status", "success");
            finish(id, "success", "执行成功", result);
        } catch (RuntimeException e) {
            finish(id, "failed", e.getMessage(), result);
            throw e;
        }
        Map<String, Object> row = Rows.one(jdbc, "select * from batch_run_logs where id=?", id);
        if (row != null) row.put("result", row.remove("result_json"));
        return row;
    }

    private long insertRunning(String key, String name, LocalDate date, Integer actorId, String trigger, String slot) {
        if ("scheduled".equals(trigger)) {
            if (!isEnabled(key)) return 0;
            int inserted = jdbc.update("insert ignore into batch_run_logs(task_key,task_name,business_date,trigger_type,status,created_by,schedule_slot) values(?,?,?,'scheduled','running',?,?)", key, name, date, actorId, slot);
            if (inserted == 0) return 0;
        } else jdbc.update("insert into batch_run_logs(task_key,task_name,business_date,trigger_type,status,created_by) values(?,?,?,'manual','running',?)", key, name, date, actorId);
        Number id = jdbc.queryForObject("select last_insert_id()", Number.class);
        return id.longValue();
    }

    private boolean isEnabled(String key) {
        Integer enabled = jdbc.queryForObject(
                "select coalesce((select enabled from batch_task_configs where task_key=?),0)",
                Integer.class, key);
        return enabled != null && enabled == 1;
    }

    private void finish(long id, String status, String message, Map<String, Object> result) {
        jdbc.update("update batch_run_logs set status=?,finished_at=now(),message=?,result_json=? where id=?",
                status, message, writeJson(result), id);
    }

    private String writeJson(Map<String, Object> result) {
        try {
            return json.writeValueAsString(result);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String taskName(String key) {
        if ("price-refresh".equals(key)) return "每日商品价格刷新";
        if ("shop-stock-plan".equals(key)) return "商店库存每日计划";
        if ("shop-stock-replenish".equals(key)) return "商店随机补货";
        if ("buffer-liquidation".equals(key)) return "缓冲区自动出售";
        if ("expired-season-cleanup".equals(key)) return "赛季到期自动清理";
        throw new IllegalArgumentException("未知跑批任务");
    }

    public void updateConfig(String key, Map<String, Object> body) {
        if (Rows.one(jdbc, "select task_key from batch_task_configs where task_key=?", key) == null) throw new IllegalArgumentException("未知跑批任务");
        String cycle = String.valueOf(body.get("cycle"));
        if (!java.util.Arrays.asList("daily", "weekly", "monthly", "yearly").contains(cycle)) throw new IllegalArgumentException("跑批周期无效");
        LocalDateTime start = LocalDateTime.parse(String.valueOf(body.get("start_at")).replace(' ', 'T'));
        int enabled = Boolean.TRUE.equals(body.get("enabled")) || "1".equals(String.valueOf(body.get("enabled"))) || "true".equalsIgnoreCase(String.valueOf(body.get("enabled"))) ? 1 : 0;
        jdbc.update("update batch_task_configs set enabled=?,cycle=?,start_at=? where task_key=?", enabled, cycle, start, key);
    }
}
