package com.nbys.escapecenter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.nbys")
public class EscapeCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(EscapeCenterApplication.class, args);
    }

    @Bean
    CommandLineRunner warmDatabasePool(JdbcTemplate jdbc) {
        return args -> jdbc.queryForObject("select 1", Integer.class);
    }
}
