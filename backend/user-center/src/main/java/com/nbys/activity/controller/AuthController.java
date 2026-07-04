package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping({"/api/admin/auth/login", "/api/h5/auth/login"})
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        boolean admin = request.getRequestURI().contains("/admin/");
        Map<String, Object> user = auth.login(String.valueOf(body.get("account")), String.valueOf(body.get("password")), admin);
        auth.issueRefreshToken((Number) user.get("id"), request, response);
        return ApiResponse.ok(user);
    }

    @GetMapping({"/api/admin/auth/me", "/api/h5/me"})
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(request.getRequestURI().contains("/admin/") ? auth.currentAdmin(request) : auth.current(request));
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponse<Map<String, Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.ok(auth.refresh(request, response));
    }

    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
        return ApiResponse.ok(null);
    }
}
