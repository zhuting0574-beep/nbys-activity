package com.nbys.escape.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.escape.service.EscapeAccessService;
import com.nbys.escape.service.EscapeAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EscapeAdminControllerTest {
    @Test
    void overviewUsesDedicatedViewPermission() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeAdminService service = mock(EscapeAdminService.class);
        EscapeAccessService.UserContext actor =
                new EscapeAccessService.UserContext(7, "escape_admin", "OP-007", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(access.requireAdmin(request, "escape:view")).thenReturn(actor);
        when(service.overview()).thenReturn(Collections.<String, Object>singletonMap("ready", true));

        ApiResponse<Map<String, Object>> response = new EscapeAdminController(access, service).overview(request);

        assertEquals(0, response.code);
        assertEquals(Boolean.TRUE, response.data.get("ready"));
        verify(access).requireAdmin(request, "escape:view");
    }

    @Test
    void startDelegatesActorAndStableIdempotencyKey() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeAdminService service = mock(EscapeAdminService.class);
        EscapeAccessService.UserContext actor =
                new EscapeAccessService.UserContext(7, "escape_admin", "OP-007", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(access.requireAdmin(request, "escape:match:start")).thenReturn(actor);
        when(service.startMatch(21L, "retry-1", actor))
                .thenReturn(Collections.<String, Object>singletonMap("status", "in_progress"));

        ApiResponse<Map<String, Object>> response =
                new EscapeAdminController(access, service).startMatch(21L, " retry-1 ", request);

        assertEquals("in_progress", response.data.get("status"));
        verify(service).startMatch(21L, "retry-1", actor);
    }

    @Test
    void generatedCompatibilityKeyAndBoundsAreValidated() {
        assertFalse(EscapeAdminController.idempotencyKey(null).isEmpty());
        assertEquals("client-1", EscapeAdminController.idempotencyKey(" client-1 "));
        assertThrows(IllegalArgumentException.class, () ->
                EscapeAdminController.idempotencyKey(String.join("", Collections.nCopies(81, "x"))));
        assertEquals(3, EscapeAdminController.positiveInt("3", "bad"));
        assertThrows(IllegalArgumentException.class, () -> EscapeAdminController.positiveInt(0, "bad"));
    }

    @Test
    void itemOptionsUsesItemViewPermissionAndFiltersArguments() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeAdminService service = mock(EscapeAdminService.class);
        EscapeAccessService.UserContext actor =
                new EscapeAccessService.UserContext(7, "escape_admin", "OP-007", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        List<Map<String, Object>> options = Collections.singletonList(Collections.<String, Object>singletonMap("id", 3));
        when(access.requireAdmin(request, "escape:item:view")).thenReturn(actor);
        when(service.itemOptions("weapon", 3)).thenReturn(options);

        ApiResponse<List<Map<String, Object>>> response =
                new EscapeAdminController(access, service).itemOptions("weapon", 3, request);

        assertEquals(0, response.code);
        assertEquals(options, response.data);
        verify(service).itemOptions("weapon", 3);
    }

    @Test
    void itemListPassesKeywordAndRarityFilters() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeAdminService service = mock(EscapeAdminService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("keyword", "LEDX");
        request.setParameter("rarity", "史诗");
        when(service.catalog("items", "LEDX", "史诗")).thenReturn(Collections.emptyList());

        ApiResponse<List<Map<String, Object>>> response =
                new EscapeAdminController(access, service).items(request);

        assertEquals(0, response.code);
        verify(access).requireAdmin(request, "escape:item:view");
        verify(service).catalog("items", "LEDX", "史诗");
    }
}
