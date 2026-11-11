package com.nbys.escape.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchRunServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private EscapePriceRefreshWorker prices;
    @Mock private EscapeShopStockWorker shopStock;
    @Mock private BufferLiquidationScheduler buffer;
    @Mock private EscapeAdminService admin;

    private BatchRunService service;

    @BeforeEach
    void setUp() {
        service = new BatchRunService(jdbc, prices, shopStock, buffer, new ObjectMapper(), admin);
    }

    @Test
    void disabledTaskCannotRunAutomaticallyOrManually() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("price-refresh"))).thenReturn(0);

        assertNull(service.runScheduled("price-refresh", LocalDateTime.parse("2026-11-11T05:00:00")));
        assertThrows(IllegalStateException.class,
                () -> service.run("price-refresh", LocalDate.of(2026, 11, 11), 1));
        verifyNoInteractions(prices);
    }
}
