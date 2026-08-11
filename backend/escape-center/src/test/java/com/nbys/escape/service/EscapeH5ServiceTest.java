package com.nbys.escape.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscapeH5ServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void currentMatchesAreLimitedToActiveSeason() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
        EscapeH5Service service = new EscapeH5Service(jdbc);

        service.matches(new EscapeAccessService.UserContext(22, "member", "测试用户", false));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("m.season_id=(select id from escape_seasons"));
        assertTrue(sql.getValue().contains("enabled=1 and current_date between start_date and end_date"));
    }

    @Test
    void purchaseDeductsLinkedItemStock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        EscapeH5Service service = new EscapeH5Service(jdbc);

        service.deductItemStock(product("regular", 12), 2);

        verify(jdbc).update(
                "update escape_items set stock_quantity=stock_quantity-?,version=version+1 " +
                        "where id=? and stock_quantity>=?",
                2, 12, 2);
    }

    @Test
    void purchaseRejectsInsufficientLinkedItemStock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        EscapeH5Service service = new EscapeH5Service(jdbc);

        assertThrows(IllegalArgumentException.class,
                () -> service.deductItemStock(product("regular", 12), 2));
    }

    @Test
    void expansionPurchaseDoesNotDeductItemStock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EscapeH5Service service = new EscapeH5Service(jdbc);

        assertDoesNotThrow(() -> service.deductItemStock(product("expansion", null), 1));

        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void soldItemsAreAggregatedAndReturnedToStock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EscapeH5Service service = new EscapeH5Service(jdbc);
        Map<String, Object> firstLedx = inventoryItem(12);
        Map<String, Object> secondLedx = inventoryItem(12);
        Map<String, Object> disk = inventoryItem(23);

        Map<Integer, Integer> counts = EscapeH5Service.soldItemCounts(
                Arrays.asList(firstLedx, secondLedx, disk));
        service.returnItemStock(counts);

        verify(jdbc).update(
                "update escape_items set stock_quantity=stock_quantity+?,version=version+1 where id=?", 2, 12);
        verify(jdbc).update(
                "update escape_items set stock_quantity=stock_quantity+?,version=version+1 where id=?", 1, 23);
    }

    @Test
    void returningNoSoldItemsDoesNotChangeStock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EscapeH5Service service = new EscapeH5Service(jdbc);

        service.returnItemStock(Collections.<Integer, Integer>emptyMap());

        verify(jdbc, never()).update(anyString(), any(), any());
    }

    private Map<String, Object> product(String type, Integer itemId) {
        Map<String, Object> product = new LinkedHashMap<String, Object>();
        product.put("product_type", type);
        product.put("item_id", itemId);
        return product;
    }

    private Map<String, Object> inventoryItem(int itemId) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("item_id", itemId);
        return item;
    }
}
