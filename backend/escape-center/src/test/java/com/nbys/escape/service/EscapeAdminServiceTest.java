package com.nbys.escape.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EscapeAdminServiceTest {
    @Test
    void linkedProductPriceCannotBeLowerThanItemPrice() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EscapeAdminService.validateLinkedProductPrice(new BigDecimal("99.99"), new BigDecimal("100.00")));

        assertEquals("商品售价不能低于物品配置价格", error.getMessage());
    }

    @Test
    void linkedProductPriceAllowsEqualOrHigherPrice() {
        assertDoesNotThrow(() -> EscapeAdminService.validateLinkedProductPrice(
                new BigDecimal("100.00"), new BigDecimal("100.00")));
        assertDoesNotThrow(() -> EscapeAdminService.validateLinkedProductPrice(
                new BigDecimal("120.00"), new BigDecimal("100.00")));
    }

    @Test
    void createMatchRequiresAtLeastTwoTeams() {
        EscapeAdminService service = new EscapeAdminService(null, new ObjectMapper());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "测试对局");
        body.put("team_count", 1);
        body.put("team_capacity", 4);
        body.put("season_id", 2);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createMatch(body, null));

        assertEquals("小队数量不能少于2个", error.getMessage());
    }

    @Test
    void createMatchRequiresSeason() {
        EscapeAdminService service = new EscapeAdminService(null, new ObjectMapper());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "测试对局");
        body.put("team_count", 2);
        body.put("team_capacity", 4);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createMatch(body, null));

        assertEquals("请选择赛季", error.getMessage());
    }

    @Test
    void createMatchRejectsBlankSeason() {
        EscapeAdminService service = new EscapeAdminService(null, new ObjectMapper());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "测试对局");
        body.put("team_count", 2);
        body.put("team_capacity", 4);
        body.put("season_id", " ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createMatch(body, null));

        assertEquals("请选择赛季", error.getMessage());
    }

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
    void settlementRejectsItemsWhenParticipantDidNotEscape() {
        Map<String, Object> participant = input(11);
        participant.put("escaped", false);
        participant.put("items", Collections.singletonList(item(7, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EscapeAdminService.validateSettlementExtractionRules(Collections.singletonList(participant)));

        assertTrue(error.getMessage().contains("不能带出物资"));
    }

    @Test
    void settlementAllowsNoItemsWhenParticipantDidNotEscape() {
        Map<String, Object> participant = input(11);
        participant.put("escaped", false);

        assertDoesNotThrow(() -> EscapeAdminService.validateSettlementExtractionRules(
                Collections.singletonList(participant)));
    }

    @Test
    void settlementRejectsTeamKillsAboveOpponentCount() {
        List<Map<String, Object>> participants = Arrays.asList(
                participant(11, 1), participant(12, 1), participant(13, 2));
        List<Map<String, Object>> inputs = Arrays.asList(input(11), input(12), input(13));
        inputs.get(0).put("kills", 1);
        inputs.get(1).put("kills", 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EscapeAdminService.validateTeamKillLimits(
                        participants, EscapeAdminService.indexSettlementInputs(inputs)));

        assertEquals("第 1 小队击杀数合计为 2，不能超过 1", error.getMessage());
    }

    @Test
    void settlementAllowsTeamKillsEqualToOpponentCount() {
        List<Map<String, Object>> participants = Arrays.asList(
                participant(11, 1), participant(12, 1), participant(13, 2));
        List<Map<String, Object>> inputs = Arrays.asList(input(11), input(12), input(13));
        inputs.get(0).put("kills", 1);
        inputs.get(2).put("kills", 2);

        assertDoesNotThrow(() -> EscapeAdminService.validateTeamKillLimits(
                participants, EscapeAdminService.indexSettlementInputs(inputs)));
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

    private Map<String, Object> participant(long participantId, int teamNo) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", participantId);
        value.put("team_no", teamNo);
        return value;
    }
}
