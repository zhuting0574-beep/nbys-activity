package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class BufferLiquidationScheduler {
    private static final Logger log = LoggerFactory.getLogger(BufferLiquidationScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final BufferLiquidationWorker worker;
    private final EscapePriceRefreshWorker priceRefreshWorker;
    private final EscapeShopStockWorker shopStockWorker;

    public BufferLiquidationScheduler(JdbcTemplate jdbc, BufferLiquidationWorker worker,
                                      EscapePriceRefreshWorker priceRefreshWorker,
                                      EscapeShopStockWorker shopStockWorker) {
        this.jdbc = jdbc;
        this.worker = worker;
        this.priceRefreshWorker = priceRefreshWorker;
        this.shopStockWorker = shopStockWorker;
    }

    /**
     * 每天北京时间 05:00 出售缓冲区可用物品。逐用户事务，单个异常不扩大锁范围。
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Shanghai")
    public void liquidateBuffers() {
        List<Map<String, Object>> users = Rows.list(jdbc,
                "select distinct user_id from escape_inventory_instances " +
                        "where warehouse_type='buffer' and status='available' order by user_id");
        String businessDate = LocalDate.now().toString();
        runLogged("price-refresh", "每日商品价格刷新", LocalDate.parse(businessDate), () -> priceRefreshWorker.refresh(businessDate));
        runLogged("shop-stock-plan", "商店库存每日计划", LocalDate.parse(businessDate), () -> shopStockWorker.plan(LocalDate.parse(businessDate)));
        for (Map<String, Object> row : users) {
            int userId = ((Number) row.get("user_id")).intValue();
            try {
                worker.liquidate(userId, businessDate);
            } catch (RuntimeException e) {
                log.error("缓冲区自动出售失败，userId={}, businessDate={}", userId, businessDate, e);
            }
        }
    }

    /** 每分钟执行已到时间的随机上架任务，同时覆盖服务重启后的补执行。 */
    @Scheduled(cron = "15 * * * * *", zone = "Asia/Shanghai")
    public void replenishShopStock() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (!now.toLocalTime().isBefore(LocalTime.of(5, 0))) {
            try {
                runLogged("shop-stock-plan", "商店库存每日计划", now.toLocalDate(), () -> shopStockWorker.plan(now.toLocalDate()));
            } catch (RuntimeException e) {
                log.error("商店每日库存计划补建失败，businessDate={}", now.toLocalDate(), e);
            }
        }
        for (LocalDate businessDate : shopStockWorker.dueTasks(now)) {
            try {
                runLogged("shop-stock-replenish", "商店随机补货", businessDate, () -> shopStockWorker.execute(businessDate));
            } catch (RuntimeException e) {
                log.error("商店随机上架失败，businessDate={}", businessDate, e);
            }
        }
    }

    private void runLogged(String key, String name, LocalDate date, Runnable action) {
        long id = 0;
        try {
            jdbc.update("insert into batch_run_logs(task_key,task_name,business_date,trigger_type,status,message) values(?,?,?,'scheduled','running','定时任务开始')", key, name, date);
            Number value = jdbc.queryForObject("select last_insert_id()", Number.class);
            id = value == null ? 0 : value.longValue();
            action.run();
            jdbc.update("update batch_run_logs set status='success',finished_at=now(),message='执行成功',result_json=? where id=?", "{\"trigger\":\"scheduled\"}", id);
        } catch (RuntimeException e) {
            if (id > 0) jdbc.update("update batch_run_logs set status='failed',finished_at=now(),message=? where id=?", e.getMessage(), id);
            throw e;
        }
    }
}
