package com.nbys.publiccenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.nbys")
public class PublicCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(PublicCenterApplication.class, args);
    }
}
