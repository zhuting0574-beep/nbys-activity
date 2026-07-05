package com.nbys.activitycenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.nbys")
public class ActivityCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivityCenterApplication.class, args);
    }

    @Bean
    CommandLineRunner warmDatabasePool(JdbcTemplate jdbc) {
        return args -> jdbc.queryForObject("select 1", Integer.class);
    }
}
