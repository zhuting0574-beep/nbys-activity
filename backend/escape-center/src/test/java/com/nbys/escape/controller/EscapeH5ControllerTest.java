package com.nbys.escape.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.escape.service.EscapeAccessService;
import com.nbys.escape.service.EscapeAdminService;
import com.nbys.escape.service.EscapeH5Service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EscapeH5ControllerTest {
    @Test
    void dashboardPassesAuthenticatedDomainContextToService() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeH5Service service = mock(EscapeH5Service.class);
        EscapeAdminService adminService = mock(EscapeAdminService.class);
        EscapeAccessService.UserContext me =
                new EscapeAccessService.UserContext(12, "user", "YS-012", false);
        when(access.requireUser(any())).thenReturn(me);
        when(service.dashboard(me)).thenReturn(Collections.<String, Object>singletonMap("display_name", "YS-012"));
        EscapeH5Controller controller = new EscapeH5Controller(access, service, adminService);

        ApiResponse<Map<String, Object>> response = controller.dashboard(new MockHttpServletRequest());

        assertEquals(0, response.code);
        assertEquals("YS-012", response.data.get("display_name"));
        verify(service).dashboard(me);
    }

    @Test
    void startControlRequiresMatchControllerAndDelegatesActor() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeH5Service service = mock(EscapeH5Service.class);
        EscapeAdminService adminService = mock(EscapeAdminService.class);
        EscapeAccessService.UserContext actor =
                new EscapeAccessService.UserContext(9, "user", "发起人", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(access.requireMatchController(request, 31L)).thenReturn(actor);
        when(adminService.startMatch(31L, "mobile-start", actor))
                .thenReturn(Collections.<String, Object>singletonMap("status", "in_progress"));

        ApiResponse<Map<String, Object>> response =
                new EscapeH5Controller(access, service, adminService)
                        .startMatch(31L, "mobile-start", request);

        assertEquals("in_progress", response.data.get("status"));
        verify(access).requireMatchController(request, 31L);
        verify(adminService).startMatch(31L, "mobile-start", actor);
    }

    @Test
    void createManagedMatchRequiresManagerPermission() {
        EscapeAccessService access = mock(EscapeAccessService.class);
        EscapeH5Service service = mock(EscapeH5Service.class);
        EscapeAdminService adminService = mock(EscapeAdminService.class);
        EscapeAccessService.UserContext actor =
                new EscapeAccessService.UserContext(9, "user", "活动发起人", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> body = Collections.<String, Object>singletonMap("name", "夜间封锁区");
        when(access.requireMatchManager(request)).thenReturn(actor);
        when(adminService.createMatch(body, actor))
                .thenReturn(Collections.<String, Object>singletonMap("id", 41L));

        ApiResponse<Map<String, Object>> response =
                new EscapeH5Controller(access, service, adminService)
                        .createManagedMatch(body, request);

        assertEquals(41L, response.data.get("id"));
        verify(access).requireMatchManager(request);
        verify(adminService).createMatch(body, actor);
    }

    @Test
    void rejectsMissingOrOversizedIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> EscapeH5Controller.idempotencyKey(null));
        assertThrows(IllegalArgumentException.class, () -> EscapeH5Controller.idempotencyKey(" "));
        assertThrows(IllegalArgumentException.class, () ->
                EscapeH5Controller.idempotencyKey(String.join("", Collections.nCopies(81, "x"))));
        assertEquals("mobile-123", EscapeH5Controller.idempotencyKey(" mobile-123 "));
    }

    @Test
    void quantityIsStrictlyBounded() {
        assertEquals(1, EscapeH5Controller.parseQuantity(1));
        assertEquals(99, EscapeH5Controller.parseQuantity("99"));
        assertThrows(IllegalArgumentException.class, () -> EscapeH5Controller.parseQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> EscapeH5Controller.parseQuantity(100));
        assertThrows(IllegalArgumentException.class, () -> EscapeH5Controller.parseQuantity("abc"));
    }
}
