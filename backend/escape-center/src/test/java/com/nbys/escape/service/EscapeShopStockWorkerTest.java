package com.nbys.escape.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscapeShopStockWorkerTest {
    @Test
    void randomPriceAlwaysStaysWithinConfiguredRange() {
        Random random = new Random(7);
        BigDecimal min = new BigDecimal("100.00");
        BigDecimal max = new BigDecimal("150.00");

        for (int i = 0; i < 1000; i++) {
            BigDecimal price = EscapeShopStockWorker.randomPrice(min, max, random);
            assertTrue(price.compareTo(min) >= 0);
            assertTrue(price.compareTo(max) <= 0);
            assertEquals(2, price.scale());
        }
    }

    @Test
    void fixedPriceKeepsTwoDecimalPlaces() {
        assertEquals(new BigDecimal("88.00"), EscapeShopStockWorker.randomPrice(
                new BigDecimal("88"), new BigDecimal("88"), new Random(1)));
    }

    @Test
    void productTypeIsInferredFromItemCategory() {
        assertEquals("weapon", EscapeShopStockWorker.productType("weapon"));
        assertEquals("regular", EscapeShopStockWorker.productType("regular"));
    }

    @Test
    void databaseDatesAreConvertedForCatchUpExecution() {
        LocalDate expected = LocalDate.of(2026, 8, 21);

        assertEquals(expected, EscapeShopStockWorker.localDate(java.sql.Date.valueOf(expected)));
        assertEquals(expected, EscapeShopStockWorker.localDate(expected));
        assertEquals(expected, EscapeShopStockWorker.localDate("2026-08-21"));
    }

    @Test
    void dailyReductionNeverMakesStockNegative() {
        assertEquals(0, EscapeShopStockWorker.reducedStock(1, 2));
        assertEquals(3, EscapeShopStockWorker.reducedStock(5, 2));
    }

    @Test
    void replenishmentNeverExceedsActualItemStock() {
        assertEquals(1, EscapeShopStockWorker.replenishmentAmount(2, 3, 2));
        assertEquals(0, EscapeShopStockWorker.replenishmentAmount(3, 3, 2));
        assertEquals(2, EscapeShopStockWorker.replenishmentAmount(0, 5, 2));
    }
}
