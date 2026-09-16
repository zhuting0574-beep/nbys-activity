package com.nbys.escape.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscapePriceRefreshWorker {
    private final JdbcTemplate jdbc;

    public EscapePriceRefreshWorker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void refresh(String businessDate) {
        int claimed = jdbc.update(
                "insert ignore into escape_price_refresh_runs(business_date,refreshed_items) values(?,0)",
                businessDate);
        if (claimed == 0) return;
        int updated = jdbc.update(
                "update escape_shop_products p join escape_items i on i.id=p.item_id " +
                        "set p.price=least(i.max_price,greatest(i.min_price,round(p.price*(0.95+rand()*0.10),2)))," +
                        "p.version=p.version+1 " +
                        "where p.enabled=1 and i.enabled=1 and i.deleted_at is null and i.material_type='product' " +
                        "and i.min_price>=0 and i.max_price>=i.min_price and i.max_price>0");
        jdbc.update(
                "update escape_items i join (select item_id,round(avg(price),2) refreshed_price " +
                        "from escape_shop_products where enabled=1 and item_id is not null group by item_id) p on p.item_id=i.id " +
                        "set i.previous_price=i.current_price,i.current_price=p.refreshed_price,i.version=i.version+1 " +
                        "where i.enabled=1 and i.deleted_at is null and i.material_type='product'");
        jdbc.update("update escape_price_refresh_runs set refreshed_items=? where business_date=?",
                updated, businessDate);
    }
}
