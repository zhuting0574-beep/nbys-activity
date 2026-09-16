package com.nbys.escape.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbys.activity.service.Rows;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class EscapeShopStockWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Random random;

    @Autowired
    public EscapeShopStockWorker(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this(jdbc, objectMapper, new Random());
    }

    EscapeShopStockWorker(JdbcTemplate jdbc, ObjectMapper objectMapper, Random random) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.random = random;
    }

    @Transactional
    public boolean plan(LocalDate businessDate) {
        boolean hit = random.nextInt(100) < 20;
        LocalDateTime scheduledAt = hit
                ? businessDate.atTime(6 + random.nextInt(18), 0)
                : null;
        int claimed = jdbc.update(
                "insert ignore into escape_shop_stock_tasks(business_date,draw_hit,scheduled_at,status) values(?,?,?,?)",
                businessDate, hit, scheduledAt, hit ? "planned" : "skipped");
        if (claimed == 0) return false;

        List<Map<String, Object>> products = Rows.list(jdbc,
                "select id,stock from escape_shop_products where enabled=1 " +
                        "and product_type in ('regular','weapon') and item_id is not null for update");
        for (Map<String, Object> product : products) {
            int reduction = random.nextInt(3);
            if (reduction == 0) continue;
            int id = number(product.get("id"));
            int stock = number(product.get("stock"));
            jdbc.update("update escape_shop_products set stock=?,version=version+1 where id=?",
                    reducedStock(stock, reduction), id);
        }
        return true;
    }

    public List<LocalDate> dueTasks(LocalDateTime now) {
        List<Map<String, Object>> rows = Rows.list(jdbc,
                "select business_date from escape_shop_stock_tasks " +
                        "where status='planned' and scheduled_at<=? order by business_date", now);
        List<LocalDate> dates = new ArrayList<LocalDate>();
        for (Map<String, Object> row : rows) {
            dates.add(localDate(row.get("business_date")));
        }
        return dates;
    }

    @Transactional
    public boolean execute(LocalDate businessDate) {
        List<Map<String, Object>> tasks = Rows.list(jdbc,
                "select business_date from escape_shop_stock_tasks " +
                        "where business_date=? and status='planned' and scheduled_at<=now() for update",
                businessDate);
        if (tasks.isEmpty()) return false;

        List<Map<String, Object>> candidates = Rows.list(jdbc,
                "select i.id,i.name,i.category,i.min_price,i.max_price,i.stock_quantity," +
                        "p.id product_id,p.stock product_stock " +
                        "from escape_items i left join escape_shop_products p on p.item_id=i.id " +
                        "where i.enabled=1 and i.deleted_at is null and i.material_type='product' " +
                        "and i.stock_quantity>coalesce(p.stock,0) order by i.id for update");
        Collections.shuffle(candidates, random);
        int selectedCount = Math.min(candidates.size(), 1 + random.nextInt(2));
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < selectedCount; index++) {
            results.add(replenish(candidates.get(index)));
        }
        jdbc.update("update escape_shop_stock_tasks set status='completed',selected_count=?,result_json=?,executed_at=now() where business_date=?",
                selectedCount, json(results), businessDate);
        return true;
    }

    private Map<String, Object> replenish(Map<String, Object> item) {
        int itemId = number(item.get("id"));
        int actualStock = number(item.get("stock_quantity"));
        int currentStock = item.get("product_stock") == null ? 0 : number(item.get("product_stock"));
        int added = replenishmentAmount(currentStock, actualStock, 1 + random.nextInt(2));
        BigDecimal price = randomPrice(decimal(item.get("min_price")), decimal(item.get("max_price")), random);
        Object productId = item.get("product_id");
        if (productId == null) {
            try {
                jdbc.update("insert into escape_shop_products(name,product_type,item_id,price,stock,off_shelf_at,enabled) " +
                                "values(?,?,?,?,?,null,1)",
                        String.valueOf(item.get("name")), productType(String.valueOf(item.get("category"))),
                        itemId, price, added);
            } catch (DuplicateKeyException ignored) {
                jdbc.update("update escape_shop_products set price=?,stock=least(?,stock+?),enabled=1," +
                                "off_shelf_at=null,version=version+1 where item_id=?",
                        price, actualStock, added, itemId);
            }
        } else {
            jdbc.update("update escape_shop_products set price=?,stock=least(?,stock+?),enabled=1," +
                            "off_shelf_at=null,version=version+1 where id=?",
                    price, actualStock, added, number(productId));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("itemId", itemId);
        result.put("name", item.get("name"));
        result.put("added", added);
        result.put("price", price);
        return result;
    }

    static BigDecimal randomPrice(BigDecimal min, BigDecimal max, Random random) {
        if (max.compareTo(min) <= 0) return min.setScale(2, RoundingMode.HALF_UP);
        BigDecimal spread = max.subtract(min);
        return min.add(spread.multiply(BigDecimal.valueOf(random.nextDouble())))
                .setScale(2, RoundingMode.HALF_UP);
    }

    static String productType(String category) {
        return "weapon".equals(category) ? "weapon" : "regular";
    }

    static int reducedStock(int currentStock, int reduction) {
        return Math.max(0, currentStock - reduction);
    }

    static int replenishmentAmount(int currentStock, int actualStock, int requested) {
        return Math.max(0, Math.min(requested, actualStock - currentStock));
    }

    static LocalDate localDate(Object value) {
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("随机上架结果序列化失败", e);
        }
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
    }
}
