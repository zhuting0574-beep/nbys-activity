package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping({"/api/admin/auth/login", "/api/h5/auth/login"})
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        boolean admin = request.getRequestURI().contains("/admin/");
        return ApiResponse.ok(auth.login(String.valueOf(body.get("account")), String.valueOf(body.get("password")), admin));
    }

    @GetMapping({"/api/admin/auth/me", "/api/h5/me"})
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(auth.current(request));
    }
}
