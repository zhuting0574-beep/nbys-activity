package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public BatchRunService(JdbcTemplate jdbc, EscapePriceRefreshWorker prices,
                           EscapeShopStockWorker shopStock, BufferLiquidationScheduler buffer, ObjectMapper json) {
        this.jdbc = jdbc;
        this.prices = prices;
        this.shopStock = shopStock;
        this.buffer = buffer;
        this.json = json;
    }

    public List<Map<String, Object>> tasks() {
        List<Map<String, Object>> result = Rows.list(jdbc,
                "select id,task_key,task_name,business_date,trigger_type,status,started_at,finished_at,message,result_json,created_by " +
                        "from batch_run_logs order by id desc limit 500");
        for (Map<String, Object> row : result) row.put("result", row.remove("result_json"));
        return result;
    }

    @Transactional
    public Map<String, Object> run(String taskKey, LocalDate businessDate, Integer actorId) {
        String name = taskName(taskKey);
        long id = insertRunning(taskKey, name, businessDate, actorId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            if ("price-refresh".equals(taskKey)) prices.refresh(businessDate.toString());
            else if ("shop-stock-plan".equals(taskKey)) shopStock.plan(businessDate);
            else if ("shop-stock-replenish".equals(taskKey)) shopStock.execute(businessDate);
            else if ("buffer-liquidation".equals(taskKey)) buffer.liquidateBuffers();
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

    private long insertRunning(String key, String name, LocalDate date, Integer actorId) {
        jdbc.update("insert into batch_run_logs(task_key,task_name,business_date,trigger_type,status,created_by) values(?,?,?,'manual','running',?)",
                key, name, date, actorId);
        Number id = jdbc.queryForObject("select last_insert_id()", Number.class);
        return id.longValue();
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
        throw new IllegalArgumentException("未知跑批任务");
    }
}
