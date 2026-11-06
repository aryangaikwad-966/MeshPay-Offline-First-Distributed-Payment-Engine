package com.demo.meshpay.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Saga Service Application
 * Orchestrates distributed payment transactions using the Saga pattern
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class SagaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaServiceApplication.class, args);
    }
}
