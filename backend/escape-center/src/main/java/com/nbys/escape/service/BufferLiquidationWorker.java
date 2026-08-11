package com.nbys.escape.service;

import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class BufferLiquidationWorker {
    private final JdbcTemplate jdbc;

    public BufferLiquidationWorker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void liquidate(int userId, String businessDate) {
        if (Rows.one(jdbc,
                "select id from escape_cash_ledger where user_id=? and business_type='buffer_auto_sale' and business_id=?",
                userId, businessDate) != null) return;
        jdbc.update("insert ignore into escape_user_assets(user_id) values(?)", userId);
        Map<String, Object> asset = Rows.one(jdbc,
                "select cash_balance from escape_user_assets where user_id=? for update", userId);
        List<Map<String, Object>> items = Rows.list(jdbc,
                "select inv.id,i.current_price from escape_inventory_instances inv join escape_items i on i.id=inv.item_id " +
                        "where inv.user_id=? and inv.warehouse_type='buffer' and inv.status='available' for update", userId);
        if (items.isEmpty()) return;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) total = total.add(decimal(item.get("current_price")));
        BigDecimal after = decimal(asset.get("cash_balance")).add(total);
        jdbc.update("delete from escape_inventory_instances " +
                "where user_id=? and warehouse_type='buffer' and status='available'", userId);
        jdbc.update("update escape_user_assets set cash_balance=?,version=version+1 where user_id=?", after, userId);
        jdbc.update("insert into escape_cash_ledger(user_id,amount,balance_after,business_type,business_id,description) " +
                        "values(?,?,?,'buffer_auto_sale',?,'缓冲区每日自动出售')",
                userId, total, after, businessDate);
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
    }
}
