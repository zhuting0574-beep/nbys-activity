package com.nbys.escape.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EscapeAdminServiceTest {
    @Test
    void settlementInputIndexRejectsDuplicateParticipant() {
        List<Map<String, Object>> inputs = Arrays.asList(input(9), input(9));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EscapeAdminService.indexSettlementInputs(inputs));
        assertTrue(error.getMessage().contains("重复"));
    }

    @Test
    void settlementInputIndexRetainsEveryParticipant() {
        Map<Long, Map<String, Object>> indexed =
                EscapeAdminService.indexSettlementInputs(Arrays.asList(input(11), input(12)));
        assertEquals(Arrays.asList(11L, 12L), new ArrayList<Long>(indexed.keySet()));
        assertEquals(Boolean.TRUE, indexed.get(11L).get("escaped"));
    }

    @Test
    void settlementItemTotalsAggregateAcrossParticipants() {
        Map<String, Object> first = input(11);
        first.put("items", Arrays.asList(item(7, 2), item(8, 1)));
        Map<String, Object> second = input(12);
        second.put("items", Collections.singletonList(item(7, 3)));

        Map<Integer, Integer> totals = EscapeAdminService.settlementItemTotals(Arrays.asList(first, second));

        assertEquals(5, totals.get(7));
        assertEquals(1, totals.get(8));
    }

    @Test
    void settlementItemTotalsRejectInvalidQuantity() {
        Map<String, Object> participant = input(11);
        participant.put("items", Collections.singletonList(item(7, 0)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EscapeAdminService.settlementItemTotals(Collections.singletonList(participant)));

        assertTrue(error.getMessage().contains("数量"));
    }

    @Test
    void settlementReturnsOnlyUnconsumedMatchItemQuantity() {
        assertEquals(7, EscapeAdminService.unconsumedQuantity(10, 3, 0));
        assertEquals(4, EscapeAdminService.unconsumedQuantity(10, 3, 3));
        assertEquals(0, EscapeAdminService.unconsumedQuantity(3, 3, 0));
    }

    private Map<String, Object> input(long participantId) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("participant_id", participantId);
        value.put("escaped", true);
        value.put("kills", 0);
        value.put("manual_cash", 0);
        value.put("items", Collections.emptyList());
        return value;
    }

    private Map<String, Object> item(int itemId, int quantity) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("item_id", itemId);
        value.put("quantity", quantity);
        return value;
    }
}
