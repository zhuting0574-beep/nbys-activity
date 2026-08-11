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
                "update escape_items set previous_price=current_price," +
                        "current_price=floor(min_price + rand()*(max_price-min_price+1))," +
                        "version=version+1 where enabled=1 and deleted_at is null");
        jdbc.update("update escape_price_refresh_runs set refreshed_items=? where business_date=?",
                updated, businessDate);
    }
}
