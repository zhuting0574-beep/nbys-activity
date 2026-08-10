package com.nbys.escape.service;

import com.nbys.activity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EscapeAccessServiceTest {
    @Test
    void formalMemberCanAccessWithoutExplicitEscapeGrant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", 28);
        user.put("role", "user");
        user.put("username", "formal-member");
        user.put("callsign", "YS-035");
        user.put("is_regular_member", true);
        when(auth.current(request)).thenReturn(user);

        EscapeAccessService.UserContext context = new EscapeAccessService(jdbc, auth).requireUser(request);

        assertEquals(28, context.userId);
        assertEquals("YS-035", context.displayName);
        verifyNoInteractions(jdbc);
    }

    @Test
    void onlyOwnerAndSuperadminCanControlMatch() {
        EscapeAccessService.UserContext owner =
                new EscapeAccessService.UserContext(12, "user", "发起人", false);
        EscapeAccessService.UserContext superadmin =
                new EscapeAccessService.UserContext(1, "superadmin", "超级管理员", true);
        EscapeAccessService.UserContext ordinaryAdmin =
                new EscapeAccessService.UserContext(2, "admin", "普通管理员", true);

        assertTrue(EscapeAccessService.canControlMatch(owner, 12));
        assertTrue(EscapeAccessService.canControlMatch(superadmin, 12));
        assertFalse(EscapeAccessService.canControlMatch(ordinaryAdmin, 12));
    }

    @Test
    void activityCreatorMatchOwnerAndSuperadminCanOpenMatchManager() {
        EscapeAccessService.UserContext member =
                new EscapeAccessService.UserContext(12, "user", "队员", false);
        EscapeAccessService.UserContext superadmin =
                new EscapeAccessService.UserContext(1, "superadmin", "超级管理员", true);

        assertTrue(EscapeAccessService.canManageMatches(member, true, false));
        assertTrue(EscapeAccessService.canManageMatches(member, false, true));
        assertTrue(EscapeAccessService.canManageMatches(superadmin, false, false));
        assertFalse(EscapeAccessService.canManageMatches(member, false, false));
    }
}
