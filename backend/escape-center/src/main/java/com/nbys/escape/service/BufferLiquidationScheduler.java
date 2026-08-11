package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class BufferLiquidationScheduler {
    private static final Logger log = LoggerFactory.getLogger(BufferLiquidationScheduler.class);
    private final JdbcTemplate jdbc;
    private final BufferLiquidationWorker worker;
    private final EscapePriceRefreshWorker priceRefreshWorker;

    public BufferLiquidationScheduler(JdbcTemplate jdbc, BufferLiquidationWorker worker,
                                      EscapePriceRefreshWorker priceRefreshWorker) {
        this.jdbc = jdbc;
        this.worker = worker;
        this.priceRefreshWorker = priceRefreshWorker;
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
        priceRefreshWorker.refresh(businessDate);
        for (Map<String, Object> row : users) {
            int userId = ((Number) row.get("user_id")).intValue();
            try {
                worker.liquidate(userId, businessDate);
            } catch (RuntimeException e) {
                log.error("缓冲区自动出售失败，userId={}, businessDate={}", userId, businessDate, e);
            }
        }
    }
}
