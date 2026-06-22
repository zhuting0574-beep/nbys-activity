package com.nbys.activity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticUploadConfig implements WebMvcConfigurer {
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) path = Paths.get(System.getProperty("user.dir")).resolve(path);
        registry.addResourceHandler("/uploads/**").addResourceLocations(path.normalize().toUri().toString());
    }
}
