package com.nbys.trainingcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.nbys")
public class TrainingCenterApplication {
    public static void main(String[] args) { SpringApplication.run(TrainingCenterApplication.class, args); }
}
