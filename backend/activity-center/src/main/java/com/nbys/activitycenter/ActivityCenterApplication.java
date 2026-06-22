package com.nbys.activitycenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.nbys")
public class ActivityCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivityCenterApplication.class, args);
    }
}
