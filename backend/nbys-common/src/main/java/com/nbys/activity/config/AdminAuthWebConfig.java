package com.nbys.activity.config;

import com.nbys.activity.service.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class AdminAuthWebConfig implements WebMvcConfigurer {
    private final AuthService auth;

    public AdminAuthWebConfig(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                auth.currentAdmin(request);
                return true;
            }
        }).addPathPatterns("/api/admin/**")
          .excludePathPatterns("/api/admin/auth/login");
    }
}
